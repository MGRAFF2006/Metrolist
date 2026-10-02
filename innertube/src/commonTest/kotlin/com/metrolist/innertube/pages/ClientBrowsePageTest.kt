package com.metrolist.innertube.pages

import com.metrolist.innertube.models.response.BrowseResponse
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalSerializationApi::class)
class ClientBrowsePageTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val song = """{
        "musicResponsiveListItemRenderer": {
            "playlistItemData": {"videoId": "track"},
            "flexColumns": [{"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Track"}]}}}],
            "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [{"url": "https://example.com/art.jpg", "width": 100, "height": 100}]}}}
        }
    }"""

    @Test
    fun parsesTwoColumnPlaylistAndItsContinuation() {
        val response = json.decodeFromString<BrowseResponse>("""{
            "responseContext": {},
            "contents": {"twoColumnBrowseResultsRenderer": {"secondaryContents": {"sectionListRenderer": {
                "contents": [{"musicPlaylistShelfRenderer": {"contents": [$song], "continuations": [{"nextContinuationData": {"continuation": "next"}}]}}]
            }}}}
        }""")
        val page = ClientBrowsePage.fromResponse(response)
        assertEquals(listOf("track"), page.items.map { it.id })
        assertEquals("next", page.continuation)
    }

    @Test
    fun parsesAppendedContinuationAndDeduplicates() {
        val response = json.decodeFromString<BrowseResponse>("""{
            "responseContext": {},
            "continuationContents": {"musicPlaylistShelfContinuation": {"contents": [$song]}},
            "onResponseReceivedActions": [{"appendContinuationItemsAction": {"continuationItems": [$song, {
                "continuationItemRenderer": {"continuationEndpoint": {"continuationCommand": {"token": "next-page"}}}
            }]}}]
        }""")
        val page = ClientBrowsePage.fromResponse(response)
        assertEquals(listOf("track"), page.items.map { it.id })
        assertEquals("next-page", page.continuation)
    }

    @Test
    fun emptyResponseDoesNotCrash() {
        val page = ClientBrowsePage.fromResponse(json.decodeFromString("""{"responseContext": {}}"""))
        assertEquals(emptyList(), page.items)
        assertNull(page.continuation)
    }
}
