package gopesh.percinel.ui

import gopesh.percinel.data.Entry
import gopesh.percinel.data.Repo
import gopesh.percinel.data.SearchResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(repo: Repo, id: Long, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var entry by remember { mutableStateOf<Entry?>(null) }
    var rating by remember { mutableStateOf<Double?>(null) }
    var watchedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var notes by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        val e = withContext(Dispatchers.IO) { repo.get(id) }
        if (e != null) {
            entry = e
            rating = e.rating
            watchedAt = e.watchedAt
            notes = e.notes ?: ""
        } else {
            onDone()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Edit watch", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        val e = entry
        if (e == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Box(Modifier.padding(padding)) {
                EntryForm(
                    selected = SearchResult(
                        tmdbId = e.tmdbId,
                        mediaType = e.mediaType,
                        title = e.title,
                        year = e.year,
                        posterPath = e.posterPath,
                        overview = "",
                    ),
                    rating = rating,
                    onRating = { rating = it },
                    watchedAt = watchedAt,
                    onWatchedAt = { watchedAt = it },
                    notes = notes,
                    onNotes = { notes = it },
                    onChange = null,
                    saveLabel = "Save changes",
                    onSave = {
                        val r = rating ?: return@EntryForm
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                repo.update(e.copy(rating = r, watchedAt = watchedAt, notes = notes.trim().ifBlank { null }))
                            }
                            onDone()
                        }
                    },
                    extra = {
                        Text(
                            "Delete this watch",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { confirmDelete = true }
                                .padding(vertical = 14.dp),
                        )
                    },
                )
            }
        }
    }

    if (confirmDelete && entry != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this watch?") },
            text = { Text("Remove “${entry!!.title}” from your watches?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.delete(id) }
                        onDone()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
