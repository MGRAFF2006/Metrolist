package com.metrolist.innertube

import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.GridRenderer
import com.metrolist.innertube.models.MusicShelfRenderer
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.getContinuation
import com.metrolist.innertube.models.getItems
import com.metrolist.innertube.models.response.BrowseResponse
import com.metrolist.innertube.models.response.SearchResponse
import com.metrolist.innertube.pages.HomePage
import com.metrolist.innertube.pages.LibraryPage
import com.metrolist.innertube.pages.SearchPage
import com.metrolist.innertube.pages.SearchResult
import com.metrolist.innertubex.InnerTube as InnerTubeX
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.models.YouTubeClient.Companion.WEB_REMIX
import io.ktor.client.HttpClient
import io.ktor.client.call.body

class MetrolistClient(
    private val httpClient: HttpClient = createPlatformHttpClient(),
) {
    private val innerTube = InnerTubeX(httpClient)
    private val cipherService = YouTubeCipherService(httpClient)
    private val extractor =
        InnerTubeExtractor(
            configParser = YtConfigParserImpl(httpClient, innerTube),
            cipherService = cipherService,
            innerTube = innerTube,
        )

    data class PlaybackSource(
        val url: String,
        val headers: Map<String, String>,
    )

    fun setSession(
        cookie: String?,
        visitorData: String? = null,
        dataSyncId: String? = null,
        authUser: String = "0",
    ) {
        innerTube.replaceSession(cookie, visitorData, dataSyncId, authUser, useLoginForBrowse = cookie != null)
    }

    suspend fun home(): Result<HomePage> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_home").body<BrowseResponse>()
            val sections =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.mapNotNull { it.musicCarouselShelfRenderer }
                    ?.mapNotNull(HomePage.Section::fromMusicCarouselShelfRenderer)
                    .orEmpty()
            HomePage(chips = null, sections = sections)
        }

    suspend fun search(query: String): Result<SearchResult> =
        runCatching {
            val response = innerTube.search(WEB_REMIX, query = query).body<SearchResponse>()
            val items = mutableListOf<YTItem>()
            response.contents
                ?.tabbedSearchResultsRenderer
                ?.tabs
                ?.firstOrNull()
                ?.tabRenderer
                ?.content
                ?.sectionListRenderer
                ?.contents
                ?.forEach { section ->
                    section.musicShelfRenderer
                        ?.contents
                        ?.getItems()
                        ?.mapNotNull(SearchPage::toYTItem)
                        ?.let(items::addAll)
                    section.itemSectionRenderer
                        ?.contents
                        ?.mapNotNull { it.musicResponsiveListItemRenderer }
                        ?.mapNotNull(SearchPage::toYTItem)
                        ?.let(items::addAll)
                }
            SearchResult(items.distinctBy(YTItem::id))
        }

    suspend fun library(browseId: String = "FEmusic_liked_videos"): Result<LibraryPage> =
        runCatching {
            val response =
                innerTube
                    .browse(WEB_REMIX, browseId = browseId, setLogin = true)
                    .body<BrowseResponse>()
            val contents =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.firstOrNull {
                        it.gridRenderer != null || it.musicShelfRenderer != null || it.musicPlaylistShelfRenderer != null
                    }
            val grid = contents?.gridRenderer
            val playlistShelf = contents?.musicPlaylistShelfRenderer
            val shelf = contents?.musicShelfRenderer
            when {
                grid != null ->
                    LibraryPage(
                        grid.items
                            .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                            .mapNotNull(LibraryPage::fromMusicTwoRowItemRenderer),
                        grid.continuations?.getContinuation(),
                    )
                playlistShelf != null ->
                    LibraryPage(
                        playlistShelf.contents.getItems().mapNotNull(LibraryPage::fromMusicResponsiveListItemRenderer),
                        playlistShelf.continuations?.getContinuation(),
                    )
                shelf != null ->
                    LibraryPage(
                        shelf.contents
                            .orEmpty()
                            .mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                            .mapNotNull(LibraryPage::fromMusicResponsiveListItemRenderer),
                        shelf.continuations?.getContinuation(),
                    )
                else -> LibraryPage(emptyList(), null)
            }
        }

    suspend fun playbackSource(item: YTItem): Result<PlaybackSource> =
        runCatching {
            val explicit = when (item) {
                is SongItem -> item.explicit
                is EpisodeItem -> item.explicit
                else -> error("${item::class.simpleName} is not directly playable")
            }
            val stream =
                requireNotNull(
                    extractor.extract(
                        videoId = item.id,
                        hints =
                            ContentHints(isExplicit = explicit).withStreamCapabilities(
                                allowHls = true,
                                allowSabr = false,
                                allowBoundedRange = false,
                            ),
                    ),
                ) { "No playable stream found" }
            PlaybackSource(stream.audioUrl, stream.headers)
        }

    suspend fun close() {
        cipherService.dispose()
        innerTube.close()
        httpClient.close()
    }
}
