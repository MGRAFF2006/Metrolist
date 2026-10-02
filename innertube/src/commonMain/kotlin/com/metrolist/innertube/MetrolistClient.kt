package com.metrolist.innertube

import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.getItems
import com.metrolist.innertube.models.getContinuation
import com.metrolist.innertube.models.response.BrowseResponse
import com.metrolist.innertube.models.response.SearchResponse
import com.metrolist.innertube.pages.ClientBrowsePage
import com.metrolist.innertube.pages.HomePage
import kotlinx.coroutines.CancellationException
import com.metrolist.innertube.pages.SearchPage
import com.metrolist.innertube.pages.SearchResult
import com.metrolist.innertubex.InnerTube as InnerTubeX
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.models.YouTubeClient.Companion.WEB_REMIX
import io.ktor.client.HttpClient
import io.ktor.client.call.body

class MetrolistClient(
    private val httpClient: HttpClient = createPlatformHttpClient(),
) {
    private val innerTube = InnerTubeX(httpClient).apply {
        // A language-only system locale has no country; YouTube rejects an empty gl.
        locale = locale.copy(gl = locale.gl.ifBlank { "US" }, hl = locale.hl.ifBlank { "en" })
    }
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
        cancellableResult {
            val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_home", setLogin = true).body<BrowseResponse>()
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

    suspend fun search(query: String, params: String? = null, continuation: String? = null): Result<SearchResult> =
        cancellableResult {
            val response = innerTube.search(WEB_REMIX, query = query.takeIf { continuation == null }, params = params, continuation = continuation, setLogin = true).body<SearchResponse>()
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
            val continued = response.continuationContents?.musicShelfContinuation
            continued?.contents?.map { it.musicResponsiveListItemRenderer }?.mapNotNull(SearchPage::toYTItem)?.let(items::addAll)
            val sections = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty()
            val next = continued?.continuations?.getContinuation()
                ?: sections.firstNotNullOfOrNull { section ->
                    section.musicShelfRenderer?.continuations?.getContinuation()
                        ?: section.musicShelfRenderer?.contents?.getContinuation()
                }
            SearchResult(items.distinctBy { "${it::class.simpleName}:${it.id}" }, next)
        }

    suspend fun browse(browseId: String, continuation: String? = null): Result<ClientBrowsePage> =
        cancellableResult {
            val response = innerTube.browse(
                WEB_REMIX,
                browseId = browseId.takeIf { continuation == null },
                continuation = continuation,
                setLogin = true,
            ).body<BrowseResponse>()
            ClientBrowsePage.fromResponse(response)
        }

    suspend fun library(browseId: String = "FEmusic_liked_videos", continuation: String? = null): Result<ClientBrowsePage> =
        browse(browseId, continuation)

    suspend fun playbackSource(item: YTItem): Result<PlaybackSource> =
        cancellableResult {
            val explicit = when (item) {
                is SongItem -> item.explicit
                is EpisodeItem -> item.explicit
                else -> error("${item::class.simpleName} is not directly playable")
            }
            val stream =
                requireNotNull(
                    extractor.extract(
                        videoId = item.id,
                        // AVPlayer supports AAC/MP4 and HLS, but not YouTube's WebM audio.
                        audioQuality = AudioQuality.MP4,
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

private suspend fun <T> cancellableResult(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
