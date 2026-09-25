package gopesh.percinel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gopesh.percinel.data.Entry
import gopesh.percinel.data.Insights
import gopesh.percinel.data.Tmdb
import kotlinx.coroutines.launch

/**
 * Every poster you've logged, packed into a grid. Deliberately dense — the appeal is seeing the
 * shape of your taste all at once. One tile per film (rewatches collapse), newest first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosterWallScreen(entries: List<Entry>, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val shareLayer = rememberShareLayer()
    val films = remember(entries) {
        Insights.perFilm(entries).sortedByDescending { it.watchedAt }
    }
    val shareName = remember {
        context.getSharedPreferences("percinel", android.content.Context.MODE_PRIVATE)
            .getString("name", "") ?: ""
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (films.isEmpty()) "Poster wall" else "Poster wall · ${films.size}",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (films.isNotEmpty()) {
                        ShareMenu(
                            layer = shareLayer,
                            fileName = "percinel-poster-wall",
                            caption = "${films.size} films watched — logged with percinel",
                            chooserTitle = "Share your poster wall",
                        )
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
        if (films.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nothing to show yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        Box(Modifier.padding(padding)) {
            OffscreenShareable(shareLayer) { PosterWallShareCard(films, shareName) }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier = Modifier.fillMaxSize().padding(padding).then(rememberBouncy()),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        ) {
            items(films, key = { it.id }) { film ->
                Box(
                    Modifier
                        .padding(4.dp)
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clickable(onClickLabel = "Open") { onOpen(film.id) }
                        .semantics {
                            contentDescription =
                                "${film.title}, rated ${formatRating(film.rating)} out of 10"
                        },
                ) {
                    PosterImage(
                        posterUrl = Tmdb.posterUrl(film.posterPath, "w342"),
                        mediaType = film.mediaType,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
