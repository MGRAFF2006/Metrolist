import AVFoundation
import MediaPlayer
import MetrolistShared

final class NativeAudioPlayer: NSObject, AudioPlayer {
    private let player = AVPlayer()
    private var listener: PlaybackListener?
    private var itemObservation: NSKeyValueObservation?
    private var playbackObservation: NSKeyValueObservation?
    private var timeObserver: Any?
    private var notifications: [NSObjectProtocol] = []
    private var commands: [(MPRemoteCommand, Any)] = []
    private var failure: String?
    private var resumeAfterInterruption = false
    private var metadata: [String: Any] = [:]

    func configure(listener: PlaybackListener) {
        close()
        self.listener = listener
        playbackObservation = player.observe(\.timeControlStatus, options: [.initial, .new]) { [weak self] _, _ in
            self?.publish()
        }
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main) { [weak self] _ in
            self?.publish()
        }
        let center = NotificationCenter.default
        notifications.append(center.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: nil, queue: .main) { [weak self] notification in
            guard let self, let item = notification.object as? AVPlayerItem, item === self.player.currentItem else { return }
            self.publish()
            self.listener?.onEnded()
        })
        notifications.append(center.addObserver(forName: .AVPlayerItemFailedToPlayToEndTime, object: nil, queue: .main) { [weak self] notification in
            guard let self, let item = notification.object as? AVPlayerItem, item === self.player.currentItem else { return }
            self.failure = (notification.userInfo?[AVPlayerItemFailedToPlayToEndTimeErrorKey] as? Error)?.localizedDescription ?? "Playback failed"
            self.player.pause()
            self.publish()
        })
        notifications.append(center.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] notification in
            guard let self,
                  let rawType = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                  let type = AVAudioSession.InterruptionType(rawValue: rawType) else { return }
            if type == .began {
                self.resumeAfterInterruption = self.player.rate > 0
                self.player.pause()
            } else {
                let options = AVAudioSession.InterruptionOptions(rawValue: notification.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0)
                if self.resumeAfterInterruption && options.contains(.shouldResume) { self.resume() }
                self.resumeAfterInterruption = false
            }
            self.publish()
        })
        notifications.append(center.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] notification in
            let reason = notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt
            if reason == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue { self?.pause() }
        })
        let remote = MPRemoteCommandCenter.shared()
        register(remote.playCommand) { [weak self] in self?.resume() }
        register(remote.pauseCommand) { [weak self] in self?.pause() }
        register(remote.togglePlayPauseCommand) { [weak self] in
            guard let self else { return }
            self.player.rate > 0 ? self.pause() : self.resume()
        }
        register(remote.nextTrackCommand) { [weak self] in self?.listener?.onNext() }
        register(remote.previousTrackCommand) { [weak self] in self?.listener?.onPrevious() }
        remote.changePlaybackPositionCommand.isEnabled = true
        let seekTarget = remote.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let self, self.player.currentItem != nil,
                  let event = event as? MPChangePlaybackPositionCommandEvent else { return .noSuchContent }
            self.seek(seconds: event.positionTime)
            return .success
        }
        commands.append((remote.changePlaybackPositionCommand, seekTarget))
    }

    func play(url: String, headers: [String: String], title: String, artist: String) {
        stop()
        guard let source = URL(string: url), ["https", "http"].contains(source.scheme?.lowercased() ?? "") else {
            failure = "Invalid audio URL"
            publish()
            return
        }
        do {
            try activateSession()
            failure = nil
            metadata = [MPMediaItemPropertyTitle: title, MPMediaItemPropertyArtist: artist]
            let asset = AVURLAsset(url: source, options: ["AVURLAssetHTTPHeaderFieldsKey": headers])
            let item = AVPlayerItem(asset: asset)
            player.replaceCurrentItem(with: item)
            itemObservation = item.observe(\.status, options: [.initial, .new]) { [weak self] item, _ in
                guard let self, item === self.player.currentItem else { return }
                if item.status == .failed {
                    self.failure = item.error?.localizedDescription ?? "Unable to play this stream"
                    self.player.pause()
                }
                self.publish()
            }
            player.play()
            publish()
        } catch {
            failure = error.localizedDescription
            publish()
        }
    }

    func pause() {
        resumeAfterInterruption = false
        player.pause()
        publish()
    }

    func resume() {
        guard player.currentItem != nil, failure == nil else { return }
        do {
            try activateSession()
            player.play()
        } catch {
            failure = error.localizedDescription
        }
        publish()
    }

    func seek(seconds: Double) {
        let duration = finiteSeconds(player.currentItem?.duration)
        guard seconds.isFinite, duration > 0 else { return }
        player.seek(to: CMTime(seconds: min(max(seconds, 0), duration), preferredTimescale: 600)) { [weak self] _ in
            DispatchQueue.main.async { self?.publish() }
        }
    }

    func stop() {
        itemObservation?.invalidate()
        itemObservation = nil
        player.pause()
        player.replaceCurrentItem(with: nil)
        resumeAfterInterruption = false
        failure = nil
        metadata = [:]
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        publish()
    }

    func close() {
        stop()
        playbackObservation?.invalidate()
        playbackObservation = nil
        if let timeObserver { player.removeTimeObserver(timeObserver) }
        timeObserver = nil
        notifications.forEach(NotificationCenter.default.removeObserver)
        notifications.removeAll()
        commands.forEach { $0.0.removeTarget($0.1) }
        commands.removeAll()
        listener = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func activateSession() throws {
        try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, policy: .longFormAudio)
        try AVAudioSession.sharedInstance().setActive(true)
    }

    private func register(_ command: MPRemoteCommand, action: @escaping () -> Void) {
        command.isEnabled = true
        let target = command.addTarget { [weak self] _ in
            guard let self, self.player.currentItem != nil else { return .noSuchContent }
            action()
            return .success
        }
        commands.append((command, target))
    }

    private func finiteSeconds(_ time: CMTime?) -> Double {
        guard let time else { return 0 }
        let seconds = CMTimeGetSeconds(time)
        return seconds.isFinite ? max(0, seconds) : 0
    }

    private func publish() {
        // KVO can arrive off the main thread; Compose and UIKit state must stay on main.
        guard Thread.isMainThread else {
            DispatchQueue.main.async { [weak self] in self?.publish() }
            return
        }
        let position = finiteSeconds(player.currentTime())
        let duration = finiteSeconds(player.currentItem?.duration)
        let playing = player.timeControlStatus == .playing
        let buffering = player.timeControlStatus == .waitingToPlayAtSpecifiedRate
        if player.currentItem != nil {
            metadata[MPMediaItemPropertyPlaybackDuration] = duration
            metadata[MPNowPlayingInfoPropertyElapsedPlaybackTime] = position
            metadata[MPNowPlayingInfoPropertyPlaybackRate] = playing ? 1.0 : 0.0
            MPNowPlayingInfoCenter.default().nowPlayingInfo = metadata
        }
        listener?.onState(state: PlaybackState(playing: playing, buffering: buffering, position: position, duration: duration, error: failure))
    }
}
