package com.metrolist.music.ios

import com.metrolist.innertube.MetrolistClient

internal expect class AudioPlayer() {
    fun play(source: MetrolistClient.PlaybackSource)

    fun pause()

    fun resume()

    fun stop()
}
