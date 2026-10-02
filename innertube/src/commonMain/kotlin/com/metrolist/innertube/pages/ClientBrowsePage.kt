package com.metrolist.innertube.pages

import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.getContinuation
import com.metrolist.innertube.models.getItems
import com.metrolist.innertube.models.response.BrowseResponse

/** A browse page used by clients that don't need Android's database-backed models. */
data class ClientBrowsePage(
    val items: List<YTItem>,
    val continuation: String?,
) {
    companion object {
        fun fromResponse(response: BrowseResponse): ClientBrowsePage {
            val sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs
                ?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty() +
                response.contents?.twoColumnBrowseResultsRenderer?.tabs
                    ?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty() +
                response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents
                    ?.sectionListRenderer?.contents.orEmpty() +
                response.contents?.sectionListRenderer?.contents.orEmpty() +
                response.continuationContents?.sectionListContinuation?.contents.orEmpty()
            val shelves = sections.flatMap { section ->
                section.musicPlaylistShelfRenderer?.contents.orEmpty() +
                    section.musicShelfRenderer?.contents.orEmpty() +
                    section.itemSectionRenderer?.contents.orEmpty().flatMap {
                        it.musicShelfRenderer?.contents.orEmpty() + it.musicPlaylistShelfRenderer?.contents.orEmpty()
                    }
            } + response.continuationContents?.musicPlaylistShelfContinuation?.contents.orEmpty() +
                response.continuationContents?.musicShelfContinuation?.contents.orEmpty() +
                response.onResponseReceivedActions.orEmpty().flatMap {
                    it.appendContinuationItemsAction?.continuationItems.orEmpty()
                }
            val grids = sections.flatMap { it.gridRenderer?.items.orEmpty() } +
                response.continuationContents?.gridContinuation?.items.orEmpty()
            val items = shelves.getItems().mapNotNull {
                if (it.isEpisode) PodcastPage.fromMusicResponsiveListItemRenderer(it)
                else LibraryPage.fromMusicResponsiveListItemRenderer(it) ?: SearchPage.toYTItem(it)
            } + shelves.mapNotNull { it.musicMultiRowListItemRenderer }
                .mapNotNull { PodcastPage.fromMusicMultiRowListItemRenderer(it) } +
                grids.mapNotNull { it.musicTwoRowItemRenderer }.mapNotNull(LibraryPage::fromMusicTwoRowItemRenderer) +
                sections.flatMap { section ->
                    section.musicCarouselShelfRenderer?.let {
                        HomePage.Section.fromMusicCarouselShelfRenderer(it)?.items
                    }.orEmpty() + section.itemSectionRenderer?.contents.orEmpty()
                        .mapNotNull { it.musicResponsiveListItemRenderer }.mapNotNull(SearchPage::toYTItem)
                }
            val continuation = response.continuationContents?.sectionListContinuation?.continuations?.getContinuation()
                ?: response.continuationContents?.musicPlaylistShelfContinuation?.continuations?.getContinuation()
                ?: response.continuationContents?.musicShelfContinuation?.continuations?.getContinuation()
                ?: response.continuationContents?.gridContinuation?.continuations?.getContinuation()
                ?: sections.firstNotNullOfOrNull {
                    it.musicPlaylistShelfRenderer?.continuations?.getContinuation()
                        ?: it.musicShelfRenderer?.continuations?.getContinuation()
                        ?: it.gridRenderer?.continuations?.getContinuation()
                }
                ?: shelves.getContinuation()
            return ClientBrowsePage(items.distinctBy { "${it::class.simpleName}:${it.id}" }, continuation)
        }
    }
}
