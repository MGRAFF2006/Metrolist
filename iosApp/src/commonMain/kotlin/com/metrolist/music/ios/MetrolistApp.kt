package com.metrolist.music.ios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.MetrolistClient
import com.metrolist.innertube.models.*
import com.metrolist.innertube.pages.ClientBrowsePage
import com.metrolist.innertube.pages.HomePage
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Tab(val label: String, val symbol: String) {
    Home("Home", "⌂"), Search("Search", "⌕"), Library("Library", "♫"),
}

private enum class SearchCategory(val label: String, val params: String?) {
    All("All", null),
    Songs("Songs", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"),
    Albums("Albums", "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"),
    Artists("Artists", "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"),
    Playlists("Playlists", "EgeKAQQoAEABagoQAxAEEAoQCRAF"),
    Podcasts("Podcasts", "EgWKAQJQAWoKEAkQChAFEAMQBA%3D%3D"),
}

private enum class Library(val label: String, val browseId: String) {
    Songs("Liked songs", "FEmusic_liked_videos"),
    Playlists("Playlists", "FEmusic_liked_playlists"),
    Albums("Albums", "FEmusic_liked_albums"),
    Artists("Artists", "FEmusic_library_corpus_track_artists"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetrolistApp(player: AudioPlayer, session: AccountSession) {
    val client = remember { MetrolistClient().apply { setSession(session.cookie) } }
    val scope = rememberCoroutineScope()
    val playback = remember { PlaybackController(player, scope, client::playbackSource) }
    var tab by remember { mutableStateOf(Tab.Home) }
    var home by remember { mutableStateOf<List<HomePage.Section>?>(null) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var homeRevision by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<YTItem>>(emptyList()) }
    var searchCategory by remember { mutableStateOf(SearchCategory.All) }
    var submittedCategory by remember { mutableStateOf(SearchCategory.All) }
    var submittedQuery by remember { mutableStateOf("") }
    var searchContinuation by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var searchGeneration by remember { mutableStateOf(0) }
    var signedIn by remember { mutableStateOf(session.cookie != null) }
    var libraryType by remember { mutableStateOf(Library.Songs) }
    var libraryRevision by remember { mutableStateOf(0) }
    var libraryPage by remember { mutableStateOf<ClientBrowsePage?>(null) }
    var libraryLoading by remember { mutableStateOf(false) }
    var libraryError by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf<List<YTItem>>(emptyList()) }
    var detailPage by remember { mutableStateOf<ClientBrowsePage?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var detailRevision by remember { mutableStateOf(0) }
    var showQueue by remember { mutableStateOf(false) }

    fun browseId(item: YTItem): String = when (item) {
        is PlaylistItem -> "VL${item.id.removePrefix("VL")}"
        is AlbumItem -> "VL${item.playlistId}"
        else -> item.id
    }

    fun open(item: YTItem, items: List<YTItem>) {
        if (item is SongItem || item is EpisodeItem) playback.play(items, item)
        else details = details + item
    }

    fun search(more: Boolean = false) {
        val submitted = if (more) submittedQuery else query.trim()
        val category = if (more) submittedCategory else searchCategory
        val token = if (more) searchContinuation else null
        if (more && token == null) return
        if (submitted.isEmpty()) return
        searchJob?.cancel()
        val generation = ++searchGeneration
        searching = true
        searchError = null
        if (!more) {
            searchResults = emptyList()
            searchContinuation = null
        }
        searchJob = scope.launch {
            try {
                client.search(submitted, category.params, token)
                    .onSuccess {
                        if (generation == searchGeneration) {
                            searchResults = if (more) (searchResults + it.items).distinctBy { "${it::class.simpleName}:${it.id}" } else it.items
                            searchContinuation = it.continuation.takeUnless { next -> next == token }
                            submittedQuery = submitted
                            submittedCategory = category
                        }
                    }
                    .onFailure { if (generation == searchGeneration) searchError = it.message ?: "Search failed" }
            } finally { if (generation == searchGeneration) searching = false }
        }
    }

    LaunchedEffect(homeRevision) {
        home = null
        homeError = null
        client.home().onSuccess { home = it.sections }
            .onFailure { homeError = it.message ?: "Home could not be loaded" }
    }
    LaunchedEffect(signedIn, libraryType, libraryRevision) {
        libraryPage = null
        libraryError = null
        if (signedIn) {
            libraryLoading = true
            try {
                client.library(libraryType.browseId).onSuccess { libraryPage = it }
                    .onFailure { libraryError = it.message ?: "Library could not be loaded" }
            } finally { libraryLoading = false }
        } else libraryLoading = false
    }
    val detail = details.lastOrNull()
    LaunchedEffect(detail, detailRevision) {
        detailPage = null
        detailError = null
        if (detail != null) {
            detailLoading = true
            try {
                client.browse(browseId(detail)).onSuccess { detailPage = it }
                    .onFailure { detailError = it.message ?: "Collection could not be loaded" }
            } finally { detailLoading = false }
        } else detailLoading = false
    }
    LaunchedEffect(client) {
        try { kotlinx.coroutines.awaitCancellation() }
        finally { withContext(NonCancellable) { client.close() } }
    }
    DisposableEffect(playback) { onDispose { playback.close() } }

    MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (showQueue) "Queue" else detail?.title ?: "Metrolist", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        if (showQueue || detail != null) TextButton(onClick = {
                            if (showQueue) showQueue = false else details = details.dropLast(1)
                        }) { Text("Back") }
                    },
                )
            },
            bottomBar = {
                Column {
                    if (playback.current != null) PlayerBar(playback, onQueue = { showQueue = !showQueue })
                    NavigationBar {
                        Tab.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = tab == destination,
                                onClick = { tab = destination; details = emptyList(); showQueue = false },
                                icon = { Text(destination.symbol) }, label = { Text(destination.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                when {
                    showQueue -> ItemList(playback.queue, onOpen = { _, index -> playback.select(index) }, currentIndex = playback.index)
                    detail != null -> {
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                detailPage?.items?.firstOrNull { it is SongItem || it is EpisodeItem }?.let {
                                    playback.play(detailPage!!.items, it)
                                }
                            }, enabled = detailPage?.items?.any { it is SongItem || it is EpisodeItem } == true) { Text("Play") }
                            OutlinedButton(onClick = {
                                val shuffled = detailPage?.items.orEmpty().filter { it is SongItem || it is EpisodeItem }.shuffled()
                                shuffled.firstOrNull()?.let { playback.play(shuffled, it) }
                            }, enabled = detailPage?.items?.any { it is SongItem || it is EpisodeItem } == true) { Text("Shuffle") }
                        }
                        BrowseList(detailPage, detailLoading, detailError, onRetry = { detailRevision++ },
                            onOpen = { item -> open(item, detailPage?.items.orEmpty()) }, onMore = {
                                val previous = detailPage ?: return@BrowseList
                                val token = previous.continuation ?: return@BrowseList
                                val revision = detailRevision
                                detailLoading = true
                                detailError = null
                                scope.launch {
                                    try {
                                        client.browse(browseId(detail), token).onSuccess {
                                            if (details.lastOrNull() == detail && detailRevision == revision) detailPage = previous.append(it)
                                        }.onFailure { if (details.lastOrNull() == detail && detailRevision == revision) detailError = it.message ?: "Loading failed" }
                                    } finally { if (details.lastOrNull() == detail && detailRevision == revision) detailLoading = false }
                                }
                            })
                    }
                    tab == Tab.Home -> {
                        homeError?.let { Failure(it) { homeRevision++ } }
                        if (home == null && homeError == null) Loading()
                        LazyColumn(Modifier.fillMaxSize()) {
                            item { TextButton(onClick = { homeRevision++ }) { Text("Refresh") } }
                            if (home?.isEmpty() == true) item { Text("No recommendations available", Modifier.padding(16.dp)) }
                            home.orEmpty().forEach { section ->
                                item { Text(section.title, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium) }
                                itemsIndexed(section.items) { _, item -> ItemRow(item) { open(item, section.items) } }
                            }
                        }
                    }
                    tab == Tab.Search -> {
                        OutlinedTextField(query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            label = { Text("Search YouTube Music") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }))
                        Button(onClick = { search() }, enabled = query.isNotBlank(), modifier = Modifier.padding(horizontal = 16.dp)) { Text("Search") }
                        searchError?.let { Failure(it) { search() } }
                        if (searching) Loading()
                        PrimaryScrollableTabRow(selectedTabIndex = searchCategory.ordinal, edgePadding = 16.dp) {
                            SearchCategory.entries.forEach { category ->
                                androidx.compose.material3.Tab(selected = searchCategory == category,
                                    onClick = { searchCategory = category; if (query.isNotBlank()) search() }, text = { Text(category.label) })
                            }
                        }
                        ItemList(searchResults, onOpen = { item, _ -> open(item, searchResults) }, footer = {
                            if (searchContinuation != null) Button(onClick = { search(true) }, enabled = !searching, modifier = Modifier.padding(16.dp)) { Text("Load more") }
                            if (!searching && searchResults.isEmpty() && submittedQuery.isNotEmpty()) Text("No results", Modifier.padding(16.dp))
                        })
                    }
                    else -> {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (signedIn) "YouTube Music library" else "Sign in to your library", Modifier.weight(1f))
                            TextButton(onClick = {
                                if (signedIn) {
                                    session.signOut()
                                    if (session.cookie == null) {
                                        client.setSession(null); signedIn = false; homeRevision++
                                    }
                                } else session.signIn(object : SignInListener {
                                    override fun onComplete(cookie: String?) {
                                        if (cookie != null) {
                                            client.setSession(cookie); signedIn = true; libraryRevision++; homeRevision++
                                        }
                                    }
                                })
                            }) { Text(if (signedIn) "Sign out" else "Sign in") }
                        }
                        if (signedIn) {
                            PrimaryScrollableTabRow(selectedTabIndex = libraryType.ordinal, edgePadding = 16.dp) {
                                Library.entries.forEach { collection ->
                                    androidx.compose.material3.Tab(selected = libraryType == collection, onClick = { libraryType = collection }, text = { Text(collection.label) })
                                }
                            }
                            BrowseList(libraryPage, libraryLoading, libraryError, onRetry = { libraryRevision++ },
                                onOpen = { open(it, libraryPage?.items.orEmpty()) }, onMore = {
                                    val previous = libraryPage ?: return@BrowseList
                                    val token = previous.continuation ?: return@BrowseList
                                    val collection = libraryType
                                    val revision = libraryRevision
                                    libraryLoading = true
                                    libraryError = null
                                    scope.launch {
                                        try {
                                            client.library(collection.browseId, token).onSuccess {
                                                if (signedIn && libraryType == collection && libraryRevision == revision) libraryPage = previous.append(it)
                                            }.onFailure { if (signedIn && libraryType == collection && libraryRevision == revision) libraryError = it.message ?: "Loading failed" }
                                        } finally { if (signedIn && libraryType == collection && libraryRevision == revision) libraryLoading = false }
                                    }
                                })
                        } else Text("Sign in with Google. Your session is stored in the iOS Keychain.", Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

private fun ClientBrowsePage.append(page: ClientBrowsePage) =
    ClientBrowsePage((items + page.items).distinctBy { "${it::class.simpleName}:${it.id}" }, page.continuation.takeUnless { it == continuation })

@Composable
private fun BrowseList(page: ClientBrowsePage?, loading: Boolean, error: String?, onRetry: () -> Unit, onOpen: (YTItem) -> Unit, onMore: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        error?.let { Failure(it, onRetry) }
        if (loading) Loading()
        ItemList(page?.items.orEmpty(), onOpen = { item, _ -> onOpen(item) }, footer = {
            if (page?.items?.isEmpty() == true && !loading) Text("No items available", Modifier.padding(16.dp))
            if (page?.continuation != null) Button(onClick = onMore, enabled = !loading, modifier = Modifier.padding(16.dp)) { Text("Load more") }
        })
    }
}

@Composable
private fun ItemList(items: List<YTItem>, onOpen: (YTItem, Int) -> Unit, currentIndex: Int = -1, footer: @Composable () -> Unit = {}) {
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(items) { index, item -> ItemRow(item, selected = index == currentIndex) { onOpen(item, index) } }
        item { footer() }
    }
}

@Composable
private fun ItemRow(item: YTItem, selected: Boolean = false, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AsyncImage(model = item.thumbnail, contentDescription = null, modifier = Modifier.size(48.dp), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(item.subtitle(), style = MaterialTheme.typography.bodySmall)
        }
    }
    HorizontalDivider()
}

@Composable
private fun PlayerBar(playback: PlaybackController, onQueue: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        HorizontalDivider()
        Text(playback.current?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(playback.current?.subtitle().orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1)
        playback.state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, maxLines = 2) }
        if (playback.state.duration > 0) {
            var seeking by remember(playback.current) { mutableStateOf<Float?>(null) }
            Slider(value = seeking ?: playback.state.position.toFloat().coerceIn(0f, playback.state.duration.toFloat()),
                onValueChange = { seeking = it }, valueRange = 0f..playback.state.duration.toFloat(),
                onValueChangeFinished = { seeking?.let { playback.seek(it.toDouble()) }; seeking = null })
            Text("${time(playback.state.position)} / ${time(playback.state.duration)}", style = MaterialTheme.typography.labelSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = playback::onPrevious, enabled = playback.index > 0 || playback.state.position > 3) { Text("Previous") }
            TextButton(onClick = playback::toggle, enabled = !playback.resolving) {
                Text(when {
                    playback.resolving -> "Loading…"
                    playback.state.error != null -> "Retry"
                    playback.state.playing || playback.state.buffering -> "Pause"
                    else -> "Play"
                })
            }
            TextButton(onClick = playback::onNext, enabled = playback.index + 1 < playback.queue.size) { Text("Next") }
            TextButton(onClick = onQueue) { Text("Queue") }
        }
    }
}

private fun time(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

@Composable
private fun Loading() { CircularProgressIndicator(Modifier.padding(16.dp)) }

@Composable
private fun Failure(message: String, onRetry: () -> Unit) {
    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
    TextButton(onClick = onRetry) { Text("Retry") }
}
