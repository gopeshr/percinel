package gopesh.percinel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gopesh.percinel.data.Entry
import gopesh.percinel.data.Tmdb

/** One watch, as a card worth putting on a story: poster, rating, and your own words. */
@Composable
fun WatchShareCard(entry: Entry, name: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Wordmark(fontSize = 14.sp)
                if (name.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "· $name",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PosterImage(
                    posterUrl = Tmdb.posterUrl(entry.posterPath, "w342"),
                    mediaType = entry.mediaType,
                    modifier = Modifier.size(width = 92.dp, height = 138.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append(if (entry.mediaType == "tv") "Series" else "Movie")
                            entry.year?.let { append(" · $it") }
                            entry.season?.let { append(" · Season $it") }
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
                        Text(
                            formatRating(entry.rating),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            " / 10",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                    Text(
                        formatListDate(entry.watchedAt),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            entry.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text(
                    "“$notes”",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

/** The poster wall as a fixed collage — a lazy grid can't be captured, so this lays out its own. */
@Composable
fun PosterWallShareCard(films: List<Entry>, name: String, columns: Int = 4, rows: Int = 5) {
    val shown = films.take(columns * rows)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Wordmark(fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    buildString {
                        if (name.isNotBlank()) append("· $name  ")
                        append("${films.size} watched")
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                Modifier.padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                shown.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { film ->
                            Box(Modifier.weight(1f).aspectRatio(2f / 3f)) {
                                PosterImage(
                                    posterUrl = Tmdb.posterUrl(film.posterPath, "w342"),
                                    mediaType = film.mediaType,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        // Keep the last row aligned when it isn't full.
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
