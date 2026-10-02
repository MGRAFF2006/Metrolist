package com.metrolist.music.ios

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.metrolist.innertube.MetrolistClient
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class PlaybackController(
    private val player: AudioPlayer,
    private val scope: CoroutineScope,
    private val source: suspend (YTItem) -> Result<MetrolistClient.PlaybackSource>,
) : PlaybackListener {
    var queue by mutableStateOf<List<YTItem>>(emptyList())
        private set
    var index by mutableStateOf(-1)
        private set
    var state by mutableStateOf(PlaybackState())
        private set
    var resolving by mutableStateOf(false)
        private set
    private var request: Job? = null
    private var generation = 0
    val current: YTItem? get() = queue.getOrNull(index)

    init { player.configure(this) }

    fun play(items: List<YTItem>, item: YTItem) {
        queue = items.filter { it is SongItem || it is EpisodeItem }
        val selected = queue.indexOfFirst { it.id == item.id }
        if (selected >= 0) select(selected)
    }

    fun select(selected: Int) {
        val item = queue.getOrNull(selected) ?: return
        request?.cancel()
        val version = ++generation
        player.stop()
        index = selected
        state = PlaybackState()
        resolving = true
        request = scope.launch {
            try {
                source(item).onSuccess {
                    // A cancelled extractor may finish anyway; only the newest tap owns playback.
                    if (version == generation) player.play(it.url, it.headers, item.title, item.subtitle())
                }.onFailure {
                    if (version == generation) state = PlaybackState(error = it.message ?: "Unable to play this item")
                }
            } finally {
                if (version == generation) resolving = false
            }
        }
    }

    fun toggle() {
        if (resolving) return
        when {
            state.error != null -> select(index)
            state.playing || state.buffering -> player.pause()
            else -> {
                if (state.duration > 0 && state.position >= state.duration - 0.5) player.seek(0.0)
                player.resume()
            }
        }
    }

    override fun onState(state: PlaybackState) { this.state = state }
    override fun onEnded() {
        if (index + 1 < queue.size) select(index + 1) else player.pause()
    }
    override fun onNext() { select(index + 1) }
    override fun onPrevious() {
        if (state.position > 3) player.seek(0.0) else select(index - 1)
    }
    fun seek(seconds: Double) = player.seek(seconds)
    fun close() {
        ++generation
        request?.cancel()
        player.close()
    }
}

internal fun YTItem.subtitle(): String = when (this) {
    is SongItem -> artists.joinToString { it.name }
    is EpisodeItem -> author?.name ?: podcast?.name ?: "Episode"
    is com.metrolist.innertube.models.AlbumItem -> artists?.joinToString { it.name } ?: "Album"
    is com.metrolist.innertube.models.ArtistItem -> if (isProfile) "Profile" else "Artist"
    is com.metrolist.innertube.models.PlaylistItem -> author?.name ?: "Playlist"
    is com.metrolist.innertube.models.PodcastItem -> author?.name ?: "Podcast"
}
