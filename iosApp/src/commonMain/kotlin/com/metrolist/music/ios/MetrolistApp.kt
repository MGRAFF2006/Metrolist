package com.metrolist.music.ios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.metrolist.innertube.MetrolistClient
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.HomePage
import kotlinx.coroutines.launch

private enum class Tab(
    val label: String,
    val symbol: String,
) {
    Home("Home", "⌂"),
    Search("Search", "⌕"),
    Library("Library", "♫"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetrolistApp(player: AudioPlayer, session: AccountSession) {
    val client = remember { MetrolistClient() }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(Tab.Home) }
    var home by remember { mutableStateOf<List<HomePage.Section>?>(null) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<YTItem>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var cookie by remember { mutableStateOf("") }
    var library by remember { mutableStateOf<List<YTItem>>(emptyList()) }
    var libraryLoading by remember { mutableStateOf(false) }
    var libraryError by remember { mutableStateOf<String?>(null) }
    var nowPlaying by remember { mutableStateOf<YTItem?>(null) }
    var playing by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    fun play(item: YTItem) {
        if (item !is SongItem && item !is EpisodeItem) return
        playbackError = null
        scope.launch {
            client.playbackSource(item)
                .onSuccess {
                    player.play(it.url, it.headers, item.title, item.subtitle())
                    nowPlaying = item
                    playing = true
                }.onFailure { playbackError = it.message ?: "Unable to play this item" }
        }
    }

    fun search() {
        val query = searchQuery.trim()
        if (query.isEmpty()) return
        searching = true
        searchError = null
        scope.launch {
            client.search(query)
                .onSuccess { searchResults = it.items }
                .onFailure { searchError = it.message ?: "Search failed" }
            searching = false
        }
    }

    fun loadLibrary() {
        val sessionCookie = cookie.trim()
        if (sessionCookie.isEmpty()) return
        client.setSession(sessionCookie)
        libraryLoading = true
        libraryError = null
        scope.launch {
            client.library()
                .onSuccess { library = it.items }
                .onFailure { libraryError = it.message ?: "Library could not be loaded" }
            libraryLoading = false
        }
    }

    LaunchedEffect(Unit) {
        player.configure(object : PlaybackListener {
            override fun onState(state: PlaybackState) { playing = state.playing; playbackError = state.error }
            override fun onEnded() { playing = false }
            override fun onNext() = Unit
            override fun onPrevious() = Unit
        })
        client.setSession(session.cookie)
        client.home()
            .onSuccess { home = it.sections }
            .onFailure { homeError = it.message ?: "Home could not be loaded" }
    }

    androidx.compose.runtime.DisposableEffect(player) {
        onDispose { player.close() }
    }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Metrolist") }) },
            bottomBar = {
                Column {
                    nowPlaying?.let { item ->
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, maxLines = 1)
                                Text(item.subtitle(), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            Button(
                                onClick = {
                                    if (playing) player.pause() else player.resume()
                                    playing = !playing
                                },
                            ) { Text(if (playing) "Pause" else "Play") }
                        }
                    }
                    NavigationBar {
                        Tab.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = tab == destination,
                                onClick = { tab = destination },
                                icon = { Text(destination.symbol) },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                playbackError?.let { ErrorText(it) }
                when (tab) {
                    Tab.Home -> HomeScreen(home, homeError, ::play)
                    Tab.Search ->
                        SearchScreen(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = ::search,
                            searching = searching,
                            error = searchError,
                            results = searchResults,
                            onPlay = ::play,
                        )
                    Tab.Library ->
                        LibraryScreen(
                            cookie = cookie,
                            onCookieChange = { cookie = it },
                            onLoad = ::loadLibrary,
                            loading = libraryLoading,
                            error = libraryError,
                            items = library,
                            onPlay = ::play,
                        )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    sections: List<HomePage.Section>?,
    error: String?,
    onPlay: (YTItem) -> Unit,
) {
    when {
        error != null -> ErrorText(error)
        sections == null -> Loading()
        sections.isEmpty() -> EmptyText("No recommendations available")
        else ->
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                sections.forEach { section ->
                    item {
                        Text(
                            section.title,
                            modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 6.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    items(section.items, key = { "${section.title}:${it.id}" }) { ItemRow(it, onPlay) }
                }
            }
    }
}

@Composable
private fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    searching: Boolean,
    error: String?,
    results: List<YTItem>,
    onPlay: (YTItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search YouTube Music") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSearch, enabled = query.isNotBlank() && !searching) {
            Text(if (searching) "Searching…" else "Search")
        }
        error?.let { ErrorText(it) }
        if (searching) Loading()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(results, key = { it.id }) { ItemRow(it, onPlay) }
        }
    }
}

@Composable
private fun LibraryScreen(
    cookie: String,
    onCookieChange: (String) -> Unit,
    onLoad: () -> Unit,
    loading: Boolean,
    error: String?,
    items: List<YTItem>,
    onPlay: (YTItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Sign in for this session", style = MaterialTheme.typography.titleMedium)
        Text(
            "Paste a YouTube Music cookie. It stays in memory and is not saved.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = cookie,
            onValueChange = onCookieChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Cookie") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onLoad, enabled = cookie.isNotBlank() && !loading) {
            Text(if (loading) "Loading…" else "Load liked songs")
        }
        error?.let { ErrorText(it) }
        if (loading) Loading()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { ItemRow(it, onPlay) }
        }
    }
}

@Composable
private fun ItemRow(
    item: YTItem,
    onPlay: (YTItem) -> Unit,
) {
    val playable = item is SongItem || item is EpisodeItem
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = playable) { onPlay(item) }
                .padding(vertical = 12.dp, horizontal = 4.dp),
    ) {
        Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(item.subtitle(), style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider()
}

private fun YTItem.subtitle(): String =
    when (this) {
        is SongItem -> artists.joinToString { it.name }
        is EpisodeItem -> podcast?.name ?: "Episode"
        is AlbumItem -> artists?.joinToString { it.name } ?: "Album"
        is ArtistItem -> if (isProfile) "Profile" else "Artist"
        is PlaylistItem -> author?.name ?: "Playlist"
        is PodcastItem -> author?.name ?: "Podcast"
    }

@Composable
private fun Loading() {
    CircularProgressIndicator(modifier = Modifier.padding(24.dp))
}

@Composable
private fun ErrorText(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
}

@Composable
private fun EmptyText(message: String) {
    Text(message, modifier = Modifier.padding(24.dp))
}
