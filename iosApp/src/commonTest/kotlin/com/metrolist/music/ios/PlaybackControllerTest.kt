package com.metrolist.music.ios

import com.metrolist.innertube.MetrolistClient
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaybackControllerTest {
    private fun song(id: String) = SongItem(id = id, title = id, artists = emptyList(), thumbnail = "")

    @Test
    fun queueSkipsCollectionsAndAdvancesOnEnd() = runTest {
        val player = FakePlayer()
        val controller = PlaybackController(player, backgroundScope) { Result.success(MetrolistClient.PlaybackSource(it.id, emptyMap())) }
        val songs = listOf(song("first"), song("second"))
        controller.play(songs + ArtistItem("artist", "Artist", null, shuffleEndpoint = null, radioEndpoint = null), songs[0])
        runCurrent()
        assertEquals(2, controller.queue.size)
        assertEquals("first", player.url)
        controller.onEnded()
        runCurrent()
        assertEquals("second", player.url)
        controller.onEnded()
        assertEquals(1, controller.index)
        assertTrue(player.paused)
        controller.onState(PlaybackState(position = 8.0))
        controller.onPrevious()
        assertEquals(0.0, player.seekPosition)
        controller.onState(PlaybackState(position = 1.0))
        controller.onPrevious()
        runCurrent()
        assertEquals("first", player.url)
        controller.close()
        assertTrue(player.closed)
    }

    @Test
    fun staleExtractionCannotReplaceNewSelection() = runTest {
        val pending = CompletableDeferred<Unit>()
        val player = FakePlayer()
        val controller = PlaybackController(player, backgroundScope) {
            if (it.id == "slow") withContext(NonCancellable) { pending.await() }
            Result.success(MetrolistClient.PlaybackSource(it.id, emptyMap()))
        }
        val songs = listOf(song("slow"), song("fast"))
        controller.play(songs, songs[0])
        runCurrent()
        controller.select(1)
        runCurrent()
        assertEquals("fast", player.url)
        pending.complete(Unit)
        runCurrent()
        assertEquals("fast", player.url)
        assertFalse(controller.resolving)
        controller.close()
    }

    @Test
    fun failureIsRetryable() = runTest {
        val player = FakePlayer()
        var attempts = 0
        val controller = PlaybackController(player, backgroundScope) {
            if (++attempts == 1) Result.failure(IllegalStateException("No stream"))
            else Result.success(MetrolistClient.PlaybackSource(it.id, emptyMap()))
        }
        val song = song("retry")
        controller.play(listOf(song), song)
        runCurrent()
        assertEquals("No stream", controller.state.error)
        controller.toggle()
        runCurrent()
        assertEquals("retry", player.url)
        controller.close()
        assertTrue(player.closed)
    }

    @Test
    fun closePreventsPendingSourceFromStartingPlayback() = runTest {
        val pending = CompletableDeferred<Unit>()
        val player = FakePlayer()
        val controller = PlaybackController(player, backgroundScope) {
            withContext(NonCancellable) { pending.await() }
            Result.success(MetrolistClient.PlaybackSource(it.id, emptyMap()))
        }
        val song = song("pending")
        controller.play(listOf(song), song)
        runCurrent()
        controller.close()
        pending.complete(Unit)
        runCurrent()
        assertEquals(null, player.url)
        assertTrue(player.closed)
    }

    private class FakePlayer : AudioPlayer {
        var url: String? = null
        var paused = false
        var closed = false
        var seekPosition = -1.0
        override fun configure(listener: PlaybackListener) = Unit
        override fun play(url: String, headers: Map<String, String>, title: String, artist: String) { this.url = url; paused = false }
        override fun pause() { paused = true }
        override fun resume() { paused = false }
        override fun seek(seconds: Double) { seekPosition = seconds }
        override fun stop() { url = null }
        override fun close() { closed = true }
    }
}
