package com.metrolist.music.ios

// Swift owns AVPlayer so KVO, audio interruptions, and remote commands use native APIs.
interface AudioPlayer {
    fun configure(listener: PlaybackListener)
    fun play(url: String, headers: Map<String, String>, title: String, artist: String)
    fun pause()
    fun resume()
    fun seek(seconds: Double)
    fun stop()
    fun close()
}

interface PlaybackListener {
    fun onState(state: PlaybackState)
    fun onEnded()
    fun onNext()
    fun onPrevious()
}

data class PlaybackState(
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val position: Double = 0.0,
    val duration: Double = 0.0,
    val error: String? = null,
)

interface AccountSession {
    val cookie: String?
    fun signIn(onComplete: SignInListener)
    fun signOut()
}

interface SignInListener {
    fun onComplete(cookie: String?)
}
