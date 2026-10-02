package com.metrolist.innertube

import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class LivePlaybackTest {
    // Opt in with METROLIST_LIVE_SMOKE=1; ordinary test runs do not depend on YouTube.
    @Test
    fun browseSearchAndExtractCompatibleAudio() = runBlocking {
        assumeTrue(System.getenv("METROLIST_LIVE_SMOKE") == "1")
        val client = MetrolistClient()
        try {
            val home = client.home().getOrThrow()
            assertTrue(home.sections.isNotEmpty())
            println("Live home: ${home.sections.size} sections")
            val result = client.search("Rick Astley Never Gonna Give You Up").getOrThrow()
            println("Live search: ${result.items.size} items")
            val collection = result.items.firstOrNull { it is AlbumItem || it is PlaylistItem }
            if (collection != null) {
                val id = if (collection is AlbumItem) "VL${collection.playlistId}" else "VL${collection.id}"
                val page = client.browse(id).getOrThrow()
                assertTrue(page.items.isNotEmpty())
                println("Live collection: ${page.items.size} items")
            }
            val stream = client.playbackSource(result.items.filterIsInstance<SongItem>().first()).getOrThrow()
            val url = Url(stream.url)
            assertTrue(url.protocol.name == "https")
            val isHls = url.encodedPath.contains("/manifest/")
            assertTrue(isHls || url.parameters["mime"] == "audio/mp4")
            println("Live format: " + if (isHls) "HLS" else "audio/mp4")
        } finally { client.close() }
    }
}
