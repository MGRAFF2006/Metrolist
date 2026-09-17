package com.metrolist.music.ios

import com.metrolist.innertube.MetrolistClient
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
internal actual class AudioPlayer actual constructor() {
    private val player = AVPlayer()

    actual fun play(source: MetrolistClient.PlaybackSource) {
        val url = NSURL.URLWithString(source.url) ?: return
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
        AVAudioSession.sharedInstance().setActive(true, error = null)
        val asset = AVURLAsset(url, mapOf("AVURLAssetHTTPHeaderFieldsKey" to source.headers))
        player.replaceCurrentItemWithPlayerItem(AVPlayerItem(asset))
        player.play()
    }

    actual fun pause() = player.pause()

    actual fun resume() = player.play()

    actual fun stop() {
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
    }
}
