package ai.ligaments.percinel.ui

import ai.ligaments.percinel.data.Details
import ai.ligaments.percinel.data.Entry
import ai.ligaments.percinel.data.Repo
import ai.ligaments.percinel.data.Tmdb
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(repo: Repo, id: Long, onBack: () -> Unit, onEdit: () -> Unit, onFindTmdb: () -> Unit) {
    var entry by remember { mutableStateOf<Entry?>(null) }
    var details by remember { mutableStateOf<Details?>(null) }

    LaunchedEffect(id) {
        val e = withContext(Dispatchers.IO) { repo.get(id) }
        if (e == null) { onBack(); return@LaunchedEffect }
        entry = e
        if (e.tmdbId != 0L) {
            try {
                details = Tmdb.details(e.mediaType, e.tmdbId)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Exception) {
                // Offline, manual entry, or fetch failed — show stored info only.
            }
        }
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
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState()),
        ) {
            val backdrop = Tmdb.backdropUrl(details?.backdropPath)
            if (backdrop != null) {
                Box(Modifier.fillMaxWidth().height(200.dp)) {
                    AsyncImage(
                        model = backdrop,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to MaterialTheme.colorScheme.background,
                            )
                        )
                    )
                }
            }

            Row(Modifier.padding(horizontal = 20.dp).padding(top = if (backdrop != null) 0.dp else 8.dp)) {
                PosterImage(
                    posterUrl = Tmdb.posterUrl(e.posterPath, "w342"),
                    mediaType = e.mediaType,
                    modifier = Modifier.size(width = 96.dp, height = 144.dp),
                )
                Column(Modifier.padding(start = 14.dp)) {
                    Text(e.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append(if (e.mediaType == "tv") "Series" else "Movie")
                            if (e.year != null) append(" · ${e.year}")
                            details?.runtimeText?.let { append(" · $it") }
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    details?.genres?.takeIf { it.isNotEmpty() }?.let {
                        Text(
                            it.take(3).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Row(
                        Modifier.padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RatingPill(e.rating)
                        Text(formatListDate(e.watchedAt), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            }

            if (e.tmdbId == 0L) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 18.dp)
                        .clickable { onFindTmdb() },
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Just the basics for now", fontSize = 14.sp, fontWeight = FontWeight.Medium)
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

            if (!e.notes.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 18.dp),
                ) {
                    Text(e.notes, fontSize = 14.sp, modifier = Modifier.padding(14.dp))
                }
            }

            details?.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                SectionHeader("Synopsis")
                Text(
                    overview,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            details?.cast?.takeIf { it.isNotEmpty() }?.let { cast ->
                SectionHeader("Cast")
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(cast) { member ->
                        Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val profile = Tmdb.posterUrl(member.profilePath, "w185")
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape, modifier = Modifier.size(72.dp)) {
                                if (profile != null) {
                                    AsyncImage(model = profile, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else {
                                    Box(contentAlignment = Alignment.Center) { Text("🎭", fontSize = 24.sp) }
                                }
                            }
                            Text(member.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            if (member.character.isNotBlank()) {
                                Text(member.character, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 10.dp),
    )
}
