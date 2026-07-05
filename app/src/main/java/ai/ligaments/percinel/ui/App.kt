package ai.ligaments.percinel.ui

import ai.ligaments.percinel.data.Entry
import ai.ligaments.percinel.data.Repo
import ai.ligaments.percinel.data.Tmdb
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Screen {
    data object Home : Screen
    data object Add : Screen
    data class Detail(val id: Long) : Screen
    data class Edit(val id: Long) : Screen
    data class Match(val id: Long) : Screen
}

private enum class SortMode(val label: String) { RECENT("Recent"), RATING("Rating"), TITLE("Title") }
private enum class TypeFilter(val label: String, val mediaType: String?) {
    ALL("All", null), MOVIES("Movies", "movie"), SERIES("Series", "tv")
}

@Composable
fun App() {
    val context = LocalContext.current
    val repo = remember { Repo(context) }
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var entries by remember { mutableStateOf<List<Entry>>(emptyList()) }

    LaunchedEffect(screen) {
        if (screen is Screen.Home) {
            entries = withContext(Dispatchers.IO) { repo.list() }
        }
    }

    BackHandler(enabled = screen !is Screen.Home) { screen = Screen.Home }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            entries = entries,
            onAdd = { screen = Screen.Add },
            onOpen = { screen = Screen.Detail(it) },
        )
        Screen.Add -> AddScreen(
            onCancel = { screen = Screen.Home },
            onSave = { newEntry ->
                scope.launch {
                    withContext(Dispatchers.IO) { repo.insert(newEntry) }
                    screen = Screen.Home
                }
            },
        )
        is Screen.Detail -> DetailScreen(
            repo = repo,
            id = s.id,
            onBack = { screen = Screen.Home },
            onEdit = { screen = Screen.Edit(s.id) },
            onFindTmdb = { screen = Screen.Match(s.id) },
        )
        is Screen.Match -> MatchScreen(
            repo = repo,
            id = s.id,
            onDone = { screen = Screen.Detail(s.id) },
        )
        is Screen.Edit -> EditScreen(
            repo = repo,
            id = s.id,
            onDone = { screen = Screen.Home },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(entries: List<Entry>, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.RECENT) }
    var descending by remember { mutableStateOf(true) }
    var typeFilter by remember { mutableStateOf(TypeFilter.ALL) }

    val displayed = remember(entries, query, sortMode, descending, typeFilter) {
        val filtered = entries
            .filter { typeFilter.mediaType == null || it.mediaType == typeFilter.mediaType }
            .filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
        val ascending = when (sortMode) {
            SortMode.RECENT -> filtered.sortedBy { it.watchedAt }
            SortMode.RATING -> filtered.sortedBy { it.rating }
            SortMode.TITLE -> filtered.sortedBy { it.title.lowercase() }
        }
        if (descending) ascending.reversed() else ascending
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Wordmark() },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Default.Add, contentDescription = "Log a watch") }
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nothing logged yet", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Tap + to log the last thing you watched",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            return@Scaffold
        }

        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search your log") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear",
                            modifier = Modifier.clickable { query = "" },
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TypeFilter.entries.forEach { f ->
                    FilterChip(
                        selected = typeFilter == f,
                        onClick = { typeFilter = f },
                        label = { Text(f.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
                Box(Modifier.weight(1f))
                SortControl(
                    current = sortMode,
                    descending = descending,
                    onSelect = { sortMode = it; descending = it != SortMode.TITLE },
                    onToggleDir = { descending = !descending },
                )
            }

            if (displayed.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nothing here matches that", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(displayed, key = { it.id }) { entry -> EntryRow(entry, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun Wordmark() {
    val muted = Color(0xFF6F6B63)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Normal)) { append("per") }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) { append("CINE") }
            withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Normal)) { append("l") }
        },
        fontSize = 22.sp,
        letterSpacing = 0.5.sp,
    )
}

@Composable
private fun SortControl(
    current: SortMode,
    descending: Boolean,
    onSelect: (SortMode) -> Unit,
    onToggleDir: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("Sort: ${current.label}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortMode.entries.forEach { mode ->
                val selected = mode == current
                DropdownMenuItem(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                mode.label,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            )
                            if (selected) {
                                Text(
                                    if (descending) "↓" else "↑",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    },
                    onClick = {
                        if (selected) onToggleDir() else { onSelect(mode); expanded = false }
                    },
                )
            }
        }
    }
}

@Composable
private fun EntryRow(entry: Entry, onOpen: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .clickable { onOpen(entry.id) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PosterImage(
            posterUrl = Tmdb.posterUrl(entry.posterPath, "w185"),
            mediaType = entry.mediaType,
            modifier = Modifier.size(width = 56.dp, height = 84.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                buildString {
                    append(entry.title)
                    if (entry.year != null) append("  ·  ${entry.year}")
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatListDate(entry.watchedAt),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        RatingPill(entry.rating)
    }
}
