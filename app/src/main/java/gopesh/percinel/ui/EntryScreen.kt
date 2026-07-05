package gopesh.percinel.ui

import gopesh.percinel.data.Entry
import gopesh.percinel.data.Repo
import gopesh.percinel.data.Tmdb
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    repo: Repo,
    id: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAbout: () -> Unit,
    onFindTmdb: () -> Unit,
) {
    var entry by remember { mutableStateOf<Entry?>(null) }

    LaunchedEffect(id) {
        val e = withContext(Dispatchers.IO) { repo.get(id) }
        if (e == null) onBack() else entry = e
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onEdit) {
                        Text("Edit", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        val e = entry
        if (e == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Row(Modifier.padding(top = 4.dp)) {
                PosterImage(
                    posterUrl = Tmdb.posterUrl(e.posterPath, "w342"),
                    mediaType = e.mediaType,
                    modifier = Modifier.size(width = 84.dp, height = 126.dp),
                )
                Column(Modifier.padding(start = 14.dp)) {
                    Text(e.title, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append(if (e.mediaType == "tv") "Series" else "Movie")
                            if (e.year != null) append(" · ${e.year}")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Label("Your rating", top = 28.dp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatRating(e.rating),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    " / 10",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
            }

            Label("Watched", top = 24.dp)
            Text("${formatDate(e.watchedAt)} · ${formatTime(e.watchedAt)}", fontSize = 15.sp)

            Label("Your notes", top = 24.dp)
            if (!e.notes.isNullOrBlank()) {
                Text(e.notes, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
            } else {
                Text(
                    "Nothing written yet — tap Edit to add your thoughts.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (e.tmdbId != 0L) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp).clickable { onAbout() },
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("More about this film", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp).clickable { onFindTmdb() },
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Just the basics for now", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "Add a poster, synopsis, and cast in a tap",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Text("Find it ›", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String, top: androidx.compose.ui.unit.Dp) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = top, bottom = 10.dp),
    )
}
