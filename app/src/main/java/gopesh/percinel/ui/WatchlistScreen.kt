package gopesh.percinel.ui

import gopesh.percinel.data.Entry
import gopesh.percinel.data.Tmdb
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WatchlistScreen(
    items: List<Entry>,
    onMenu: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onMarkWatched: (Long) -> Unit,
    onRemove: (List<Entry>) -> Unit,
    onUndo: (List<Entry>) -> Unit,
) {
    val snackbarState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }

    fun exitSelection() { selectionMode = false; selected = emptySet() }
    fun toggle(id: Long) {
        selected = if (id in selected) selected - id else selected + id
        if (selected.isEmpty()) selectionMode = false
    }
    fun removeWithUndo(list: List<Entry>) {
        if (list.isEmpty()) return
        onRemove(list)
        scope.launch {
            val message = if (list.size == 1) "Removed “${list[0].title}”" else "Removed ${list.size} from watchlist"
            val result = snackbarState.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onUndo(list)
        }
    }

    BackHandler(enabled = selectionMode) { exitSelection() }

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
                        val allIds = items.map { it.id }.toSet()
                        val allSelected = allIds.isNotEmpty() && selected.containsAll(allIds)
                        TextButton(onClick = { selected = if (allSelected) emptySet() else allIds }) {
                            Text(
                                if (allSelected) "Clear" else "All",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        TextButton(onClick = {
                            val toRemove = items.filter { it.id in selected }
                            exitSelection()
                            removeWithUndo(toRemove)
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
                TopAppBar(
                    title = { Text("Watchlist", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(
                    onClick = onAdd,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Default.Add, contentDescription = "Add to watchlist") }
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).then(rememberBouncy()).verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nothing to watch yet", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Add something you're planning to watch",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).then(rememberBouncy()), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(items, key = { it.id }) { entry ->
                Box(Modifier.animateItem()) {
                val isSelected = entry.id in selected
                if (selectionMode) {
                    WatchlistRow(
                        entry = entry,
                        selected = isSelected,
                        selectionMode = true,
                        onClick = { toggle(entry.id) },
                        onLongClick = {},
                        onMarkWatched = null,
                    )
                } else {
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) { removeWithUndo(listOf(entry)); true } else false
                        },
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Text("Remove", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Medium)
                            }
                        },
                    ) {
                        WatchlistRow(
                            entry = entry,
                            selected = false,
                            selectionMode = false,
                            onClick = { onOpen(entry.id) },
                            onLongClick = { selectionMode = true; selected = setOf(entry.id) },
                            onMarkWatched = { onMarkWatched(entry.id) },
                        )
                    }
                }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WatchlistRow(
    entry: Entry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMarkWatched: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onClickLabel = if (selectionMode) (if (selected) "Deselect" else "Select") else "Open",
                onLongClick = onLongClick,
                onLongClickLabel = "Select",
            )
            .semantics { if (selectionMode) this.selected = selected }
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selectionMode) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                ).clearAndSetSemantics {},
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
                if (entry.mediaType == "tv") "Series" else "Movie",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (!selectionMode && onMarkWatched != null) {
            TextButton(onClick = onMarkWatched) {
                Text("Watched", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
        }
    }
}
