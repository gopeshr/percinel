package gopesh.percinel.ui

import gopesh.percinel.data.Entry
import gopesh.percinel.data.Repo
import gopesh.percinel.data.Tmdb
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
    data class Entry(val id: Long) : Screen
    data class About(val id: Long) : Screen
    data class Edit(val id: Long) : Screen
    data class Match(val id: Long) : Screen
}

private enum class SortMode(val label: String) { RECENT("Recent"), RATING("Rating"), TITLE("Title") }
private enum class TypeFilter(val label: String, val mediaType: String?) {
    ALL("All", null), MOVIES("Movies", "movie"), SERIES("Series", "tv")
}
private enum class Section(val label: String) {
    WATCHES("Watches"), WATCHLIST("Watchlist"), FORYOU("For you"), STATS("Stats"), PROFILE("You"), SETTINGS("Settings")
}

private sealed interface WlScreen {
    data object List : WlScreen
    data object Add : WlScreen
    data class About(val id: Long) : WlScreen
    data class MarkWatched(val id: Long) : WlScreen
}

private sealed interface StScreen {
    data object Home : StScreen
    data class Listing(val filter: StatFilter) : StScreen
    data class Entry(val id: Long) : StScreen
    data class About(val id: Long) : StScreen
    data class Edit(val id: Long) : StScreen
    data class Match(val id: Long) : StScreen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val context = LocalContext.current
    val repo = remember { Repo(context) }
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("percinel", android.content.Context.MODE_PRIVATE) }

    // First-run onboarding: show until completed. Existing users (name already set) skip it.
    var onboarded by remember {
        mutableStateOf(prefs.getBoolean("onboarded", false) || !prefs.getString("name", "").isNullOrBlank())
    }
    if (!onboarded) {
        OnboardingScreen(repo = repo, onDone = { onboarded = true })
        return
    }

    var section by remember { mutableStateOf(Section.WATCHES) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    fun openDrawer() { scope.launch { drawerState.open() } }

    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var entries by remember { mutableStateOf<List<Entry>>(emptyList()) }

    LaunchedEffect(screen, section) {
        if (section == Section.WATCHES && screen is Screen.Home) {
            entries = withContext(Dispatchers.IO) { repo.list() }
        }
    }

    // Automatic background sync: pull on foreground, push on background — silent, best-effort.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START ||
                event == androidx.lifecycle.Lifecycle.Event.ON_STOP
            ) {
                scope.launch {
                    val result = autoSync(context, repo)
                    if (result != null) {
                        entries = withContext(Dispatchers.IO) { repo.list() }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = section != Section.WATCHES) { section = Section.WATCHES }
    BackHandler(enabled = section == Section.WATCHES && screen !is Screen.Home) { screen = Screen.Home }

    val atTopLevel = section != Section.WATCHES || screen is Screen.Home

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = atTopLevel,
        drawerContent = {
            DrawerSheet(current = section, onSelect = {
                section = it
                screen = Screen.Home
                scope.launch { drawerState.close() }
            })
        },
    ) {
        when (section) {
            Section.FORYOU -> ForYouScreen(repo = repo, onMenu = { openDrawer() })
            Section.STATS -> StatsFlow(repo = repo, scope = scope, onMenu = { openDrawer() })
            Section.PROFILE -> ProfileScreen(repo = repo, onMenu = { openDrawer() })
            Section.SETTINGS -> SettingsScreen(repo = repo, onMenu = { openDrawer() })
            Section.WATCHLIST -> WatchlistFlow(repo = repo, scope = scope, onMenu = { openDrawer() })
            Section.WATCHES -> WatchesFlow(
                repo = repo,
                scope = scope,
                screen = screen,
                entries = entries,
                onScreen = { screen = it },
                onEntries = { entries = it },
                onMenu = { openDrawer() },
            )
        }
    }
}

private fun sectionIcon(s: Section): androidx.compose.ui.graphics.vector.ImageVector = when (s) {
    Section.WATCHES -> Icons.Filled.Movie
    Section.WATCHLIST -> Icons.Filled.BookmarkBorder
    Section.FORYOU -> Icons.Filled.AutoAwesome
    Section.STATS -> Icons.Filled.BarChart
    Section.PROFILE -> Icons.Filled.Person
    Section.SETTINGS -> Icons.Filled.Settings
}

@Composable
private fun DrawerSheet(current: Section, onSelect: (Section) -> Unit) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)),
            ) {
                Image(
                    painter = painterResource(gopesh.percinel.R.drawable.ic_launcher_background),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
                Image(
                    painter = painterResource(gopesh.percinel.R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(Modifier.padding(start = 14.dp)) {
                Wordmark()
                Text(
                    "your movie diary",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(10.dp))
        Section.entries.forEach { s ->
            NavigationDrawerItem(
                icon = { Icon(sectionIcon(s), contentDescription = null) },
                label = { Text(s.label, fontSize = 15.sp) },
                selected = s == current,
                onClick = { onSelect(s) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun WatchesFlow(
    repo: Repo,
    scope: kotlinx.coroutines.CoroutineScope,
    screen: Screen,
    entries: List<Entry>,
    onScreen: (Screen) -> Unit,
    onEntries: (List<Entry>) -> Unit,
    onMenu: () -> Unit,
) {
    when (val s = screen) {
        Screen.Home -> HomeScreen(
            entries = entries,
            onMenu = onMenu,
            onAdd = { onScreen(Screen.Add) },
            onOpen = { onScreen(Screen.Entry(it)) },
            onDelete = { list ->
                val ids = list.map { it.id }.toSet()
                onEntries(entries.filterNot { it.id in ids })
                scope.launch { withContext(Dispatchers.IO) { list.forEach { repo.delete(it.id) } } }
            },
            onUndo = { list ->
                scope.launch {
                    withContext(Dispatchers.IO) { list.forEach { repo.insert(it) } }
                    onEntries(withContext(Dispatchers.IO) { repo.list() })
                }
            },
        )
        Screen.Add -> AddScreen(
            onCancel = { onScreen(Screen.Home) },
            onSave = { newEntry ->
                scope.launch {
                    withContext(Dispatchers.IO) { repo.insert(newEntry) }
                    onScreen(Screen.Home)
                }
            },
        )
        is Screen.Entry -> EntryScreen(
            repo = repo,
            id = s.id,
            onBack = { onScreen(Screen.Home) },
            onEdit = { onScreen(Screen.Edit(s.id)) },
            onAbout = { onScreen(Screen.About(s.id)) },
            onFindTmdb = { onScreen(Screen.Match(s.id)) },
        )
        is Screen.About -> AboutScreen(
            repo = repo,
            id = s.id,
            onBack = { onScreen(Screen.Entry(s.id)) },
        )
        is Screen.Match -> MatchScreen(
            repo = repo,
            id = s.id,
            onDone = { onScreen(Screen.Entry(s.id)) },
        )
        is Screen.Edit -> EditScreen(
            repo = repo,
            id = s.id,
            onDone = { onScreen(Screen.Home) },
        )
    }
}

@Composable
private fun WatchlistFlow(
    repo: Repo,
    scope: kotlinx.coroutines.CoroutineScope,
    onMenu: () -> Unit,
) {
    var wscreen by remember { mutableStateOf<WlScreen>(WlScreen.List) }
    var items by remember { mutableStateOf<List<Entry>>(emptyList()) }

    LaunchedEffect(wscreen) {
        if (wscreen is WlScreen.List) items = withContext(Dispatchers.IO) { repo.watchlist() }
    }
    BackHandler(enabled = wscreen !is WlScreen.List) { wscreen = WlScreen.List }

    when (val w = wscreen) {
        WlScreen.List -> WatchlistScreen(
            items = items,
            onMenu = onMenu,
            onAdd = { wscreen = WlScreen.Add },
            onOpen = { wscreen = WlScreen.About(it) },
            onMarkWatched = { wscreen = WlScreen.MarkWatched(it) },
            onRemove = { e ->
                items = items.filterNot { it.id == e.id }
                scope.launch { withContext(Dispatchers.IO) { repo.delete(e.id) } }
            },
        )
        is WlScreen.About -> AboutScreen(
            repo = repo,
            id = w.id,
            onBack = { wscreen = WlScreen.List },
            onMarkWatched = { wscreen = WlScreen.MarkWatched(w.id) },
        )
        WlScreen.Add -> AddScreen(
            watchlist = true,
            onCancel = { wscreen = WlScreen.List },
            onSave = { newItem ->
                scope.launch {
                    withContext(Dispatchers.IO) { repo.insert(newItem) }
                    wscreen = WlScreen.List
                }
            },
        )
        is WlScreen.MarkWatched -> MarkWatchedScreen(
            repo = repo,
            id = w.id,
            onDone = { wscreen = WlScreen.List },
        )
    }
}

@Composable
private fun StatsFlow(
    repo: Repo,
    scope: kotlinx.coroutines.CoroutineScope,
    onMenu: () -> Unit,
) {
    var st by remember { mutableStateOf<StScreen>(StScreen.Home) }
    var all by remember { mutableStateOf<List<Entry>?>(null) }
    // Where an opened entry should return to (a listing, or the stats home for highlights).
    var entryReturn by remember { mutableStateOf<StScreen>(StScreen.Home) }

    LaunchedEffect(st) {
        if (st is StScreen.Home || st is StScreen.Listing) {
            all = withContext(Dispatchers.IO) { repo.list() }
        }
    }

    BackHandler(enabled = st !is StScreen.Home) {
        st = when (val s = st) {
            is StScreen.Listing -> StScreen.Home
            is StScreen.Entry -> entryReturn
            is StScreen.About -> StScreen.Entry(s.id)
            is StScreen.Edit -> StScreen.Entry(s.id)
            is StScreen.Match -> StScreen.Entry(s.id)
            StScreen.Home -> StScreen.Home
        }
    }

    when (val s = st) {
        StScreen.Home -> StatsScreen(
            entries = all,
            onMenu = onMenu,
            onOpenList = { st = StScreen.Listing(it) },
            onOpenEntry = { entryReturn = StScreen.Home; st = StScreen.Entry(it) },
        )
        is StScreen.Listing -> StatListScreen(
            title = statTitle(s.filter),
            items = statEntries(s.filter, all ?: emptyList()),
            onBack = { st = StScreen.Home },
            onOpen = { entryReturn = s; st = StScreen.Entry(it) },
        )
        is StScreen.Entry -> EntryScreen(
            repo = repo,
            id = s.id,
            onBack = { st = entryReturn },
            onEdit = { st = StScreen.Edit(s.id) },
            onAbout = { st = StScreen.About(s.id) },
            onFindTmdb = { st = StScreen.Match(s.id) },
        )
        is StScreen.About -> AboutScreen(
            repo = repo,
            id = s.id,
            onBack = { st = StScreen.Entry(s.id) },
        )
        is StScreen.Match -> MatchScreen(
            repo = repo,
            id = s.id,
            onDone = { st = StScreen.Entry(s.id) },
        )
        is StScreen.Edit -> EditScreen(
            repo = repo,
            id = s.id,
            onDone = { st = StScreen.Entry(s.id) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(
    entries: List<Entry>,
    onMenu: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onDelete: (List<Entry>) -> Unit,
    onUndo: (List<Entry>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.RECENT) }
    var descending by remember { mutableStateOf(true) }
    var typeFilter by remember { mutableStateOf(TypeFilter.ALL) }
    val snackbarState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }

    fun exitSelection() { selectionMode = false; selected = emptySet() }
    fun toggle(id: Long) {
        selected = if (id in selected) selected - id else selected + id
        if (selected.isEmpty()) selectionMode = false
    }

    fun removeWithUndo(items: List<Entry>) {
        if (items.isEmpty()) return
        onDelete(items)
        scope.launch {
            val message = if (items.size == 1) "Removed “${items[0].title}”" else "Removed ${items.size} watches"
            val result = snackbarState.showSnackbar(
                message = message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onUndo(items)
        }
    }

    BackHandler(enabled = selectionMode) { exitSelection() }

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
        snackbarHost = { SnackbarHost(snackbarState) },
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selected.size} selected", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        val allIds = displayed.map { it.id }.toSet()
                        val allSelected = allIds.isNotEmpty() && selected.containsAll(allIds)
                        TextButton(onClick = { selected = if (allSelected) emptySet() else allIds }) {
                            Text(
                                if (allSelected) "Clear" else "All",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        TextButton(onClick = {
                            val items = entries.filter { it.id in selected }
                            exitSelection()
                            removeWithUndo(items)
                        }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Wordmark() },
                    navigationIcon = {
                        IconButton(onClick = onMenu) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Default.Add, contentDescription = "Add a watch") }
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nothing here yet", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Add the last thing you watched",
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
                placeholder = { Text("Search your watches") },
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
                    items(displayed, key = { it.id }) { entry ->
                        val isSelected = entry.id in selected
                        if (selectionMode) {
                            EntryRow(
                                entry = entry,
                                selected = isSelected,
                                selectionMode = true,
                                onClick = { toggle(entry.id) },
                                onLongClick = {},
                            )
                        } else {
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        removeWithUndo(listOf(entry)); true
                                    } else false
                                },
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.errorContainer)
                                            .padding(horizontal = 24.dp),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        Text(
                                            "Delete",
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                },
                            ) {
                                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                                    EntryRow(
                                        entry = entry,
                                        selected = false,
                                        selectionMode = false,
                                        onClick = { onOpen(entry.id) },
                                        onLongClick = { selectionMode = true; selected = setOf(entry.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Wordmark(fontSize: androidx.compose.ui.unit.TextUnit = 22.sp) {
    val muted = Color(0xFF6F6B63)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Normal)) { append("per") }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) { append("CINE") }
            withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Normal)) { append("l") }
        },
        fontSize = fontSize,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: Entry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selectionMode) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                } else {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                }
            }
        }
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
