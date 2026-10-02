package com.metrolist.innertube.utils

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.LibraryPage
import com.metrolist.innertube.pages.PlaylistPage
import java.security.MessageDigest

@JvmName("completedLibrary")
suspend fun Result<PlaylistPage>.completed(): Result<PlaylistPage> = runCatching {
    val page = getOrThrow()
    val songs = page.songs.toMutableList()
    var continuation = page.songsContinuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0

    while (continuation != null) {
        check(requestCount++ < 50) { "Playlist pagination exceeded 50 requests" }
        check(seenContinuations.add(continuation)) { "Playlist pagination repeated a continuation" }

        val continuationPage = YouTube.playlistContinuation(continuation).getOrThrow()
        songs += continuationPage.songs
        continuation = continuationPage.continuation
    }
    PlaylistPage(
        playlist = page.playlist,
        songs = songs,
        songsContinuation = null,
        continuation = page.continuation
    )
}

@JvmName("completedPlaylist")
suspend fun Result<LibraryPage>.completed(): Result<LibraryPage> = runCatching {
    val page = getOrThrow()
    val items = page.items.toMutableList()
    var continuation = page.continuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0

    while (continuation != null) {
        check(requestCount++ < 50) { "Library pagination exceeded 50 requests" }
        check(seenContinuations.add(continuation)) { "Library pagination repeated a continuation" }

        val continuationPage = YouTube.libraryContinuation(continuation, isUploaded = page.isUploaded).getOrThrow()
        items += continuationPage.items
        continuation = continuationPage.continuation
    }
    page.copy(items = items, continuation = null)
}

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun sha1(str: String): String = MessageDigest.getInstance("SHA-1").digest(str.toByteArray()).toHex()
