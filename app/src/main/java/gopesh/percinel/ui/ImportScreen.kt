package gopesh.percinel.ui

import gopesh.percinel.data.Entry
import gopesh.percinel.data.Import
import gopesh.percinel.data.Repo
import gopesh.percinel.data.STATUS_WATCHED
import gopesh.percinel.data.Tmdb
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface ImportState {
    data object Pick : ImportState
    data class Error(val message: String) : ImportState
    data class Preview(val parsed: Import.Parsed, val fresh: List<Import.Row>, val duplicates: Int) : ImportState
    data class Working(val step: String) : ImportState
    data class Done(val added: Int, val posters: Int) : ImportState
}

/** Bring watch history in from Letterboxd, IMDb, or a percinel export. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(repo: Repo, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ImportState>(ImportState.Pick) }
    var findPosters by remember { mutableStateOf(true) }

    val busy = state is ImportState.Working
    BackHandler(enabled = !busy) { onClose() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            state = ImportState.Working("Reading the file…")
            state = withContext(Dispatchers.IO) {
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
                when {
                    bytes == null -> ImportState.Error("Couldn't open that file.")
                    bytes.size > 30_000_000 -> ImportState.Error("That file is too large.")
                    else -> {
                        val parsed = runCatching { Import.parse(bytes) }.getOrNull()
                        if (parsed == null) {
                            ImportState.Error(
                                "That doesn't look like a file we can read. " +
                                    "percinel understands Letterboxd exports (diary.csv or ratings.csv), " +
                                    "IMDb ratings exports, and percinel's own spreadsheet export.",
                            )
                        } else {
                            // Skip anything already in the diary, and repeats inside the file itself.
                            val existing = repo.list()
                                .map { Import.dedupeKey(it.title, it.year, it.watchedAt) }
                                .toHashSet()
                            val seen = HashSet<String>()
                            val fresh = parsed.rows.filter { r ->
                                val key = Import.dedupeKey(r.title, r.year, r.watchedAt)
                                key !in existing && seen.add(key)
                            }
                            ImportState.Preview(parsed, fresh, parsed.rows.size - fresh.size)
                        }
                    }
                }
            }
        }
    }

    fun runImport(preview: ImportState.Preview) {
        scope.launch {
            state = ImportState.Working("Adding your watches…")
            val result = withContext(Dispatchers.IO) {
                val ids = preview.fresh.map { r ->
                    repo.insert(
                        Entry(
                            id = 0,
                            tmdbId = 0,
                            mediaType = r.mediaType,
                            title = r.title,
                            posterPath = null,
                            year = r.year,
                            rating = roundTo2(r.rating),
                            watchedAt = r.watchedAt,
                            notes = r.notes,
                            status = STATUS_WATCHED,
                            season = r.season,
                        ),
                    ) to r
                }
                var posters = 0
                if (findPosters) {
                    // One TMDB lookup per film; a wrong guess is worse than no poster,
                    // so only link when the year agrees (or one side has no year).
                    val matched = HashMap<String, gopesh.percinel.data.SearchResult?>()
                    ids.forEachIndexed { i, (id, r) ->
                        withContext(Dispatchers.Main) {
                            state = ImportState.Working("Finding posters… ${i + 1}/${ids.size}")
                        }
                        val cacheKey = "${r.mediaType}|${r.title.lowercase()}|${r.year}"
                        val hit = matched.getOrPut(cacheKey) {
                            runCatching {
                                Tmdb.search(r.title).firstOrNull { s ->
                                    s.mediaType == r.mediaType &&
                                        (r.year == null || s.year == null || kotlin.math.abs(s.year - r.year) <= 1)
                                }
                            }.getOrNull()
                        }
                        if (hit != null) {
                            repo.linkTmdb(id, hit.tmdbId, hit.mediaType, hit.title, hit.posterPath, hit.year ?: r.year)
                            posters++
                        }
                    }
                }
                ImportState.Done(ids.size, if (findPosters) posters else -1)
            }
            state = result
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Import watches", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (!busy) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
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
        Column(
            Modifier.fillMaxSize().padding(padding).then(rememberBouncy()).verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            when (val s = state) {
                is ImportState.Pick, is ImportState.Error -> {
                    Text(
                        "Bring your history with you",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Everything you've already logged elsewhere can live here too. percinel reads:",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    SourceCard(
                        "Letterboxd",
                        "Ask for your data at letterboxd.com/settings/data, then pick diary.csv (best) or ratings.csv from the zip.",
                    )
                    SourceCard(
                        "IMDb",
                        "On your Ratings page, choose Export. Pick the CSV it gives you.",
                    )
                    SourceCard(
                        "percinel",
                        "A spreadsheet exported from Settings on another phone imports back perfectly — seasons, notes and all.",
                    )
                    if (s is ImportState.Error) {
                        Text(
                            s.message,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    Button(
                        onClick = {
                            picker.launch(
                                arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "text/plain",
                                    "application/octet-stream",
                                ),
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(52.dp),
                    ) {
                        Text("Choose a file", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }

                is ImportState.Preview -> {
                    Text(
                        "Found ${s.parsed.rows.size} ${if (s.parsed.rows.size == 1) "watch" else "watches"} from ${s.parsed.source}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (s.duplicates > 0) {
                        Text(
                            "${s.duplicates} already in your diary — we'll skip those.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (s.parsed.unrated > 0) {
                        Text(
                            "${s.parsed.unrated} had no rating and were left out.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Find posters", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "Look each film up on TMDB for artwork and details",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = findPosters, onCheckedChange = { findPosters = it })
                    }
                    Button(
                        onClick = { runImport(s) },
                        enabled = s.fresh.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(52.dp),
                    ) {
                        Text(
                            if (s.fresh.isEmpty()) "Nothing new to add"
                            else "Add ${s.fresh.size} ${if (s.fresh.size == 1) "watch" else "watches"}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    TextButton(
                        onClick = { state = ImportState.Pick },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text("Pick a different file", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                is ImportState.Working -> {
                    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(s.step, fontSize = 15.sp, modifier = Modifier.padding(top = 20.dp))
                        }
                    }
                }

                is ImportState.Done -> {
                    Text("All set 🎉", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        buildString {
                            append("${s.added} ${if (s.added == 1) "watch" else "watches"} added to your diary.")
                            if (s.posters >= 0) append(" Posters found for ${s.posters} of them.")
                        },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (s.posters in 0 until s.added) {
                        Text(
                            "The rest just need a tap — open one and choose \"Find it\" to add artwork.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Button(
                        onClick = onClose,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(52.dp),
                    ) {
                        Text("Done", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SourceCard(name: String, how: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                how,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
