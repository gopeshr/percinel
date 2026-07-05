package gopesh.percinel.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.math.round

private val zone: ZoneId get() = ZoneId.systemDefault()
private val dateFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val listDateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

fun formatDate(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(dateFmt)
fun formatTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(timeFmt)
fun formatListDate(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(listDateFmt)
fun formatRating(value: Double): String = String.format(Locale.US, "%.2f", value)

fun roundTo2(v: Double): Double = round(v * 100.0) / 100.0

private fun sanitizeRating(raw: String): String {
    val s = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
    val dot = s.indexOf('.')
    if (dot < 0) return s.take(2)
    val intPart = s.substring(0, dot).filter { it.isDigit() }.take(2)
    val decPart = s.substring(dot + 1).filter { it.isDigit() }.take(2)
    return "$intPart.$decPart"
}

@Composable
fun PosterImage(posterUrl: String?, mediaType: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
    ) {
        if (posterUrl != null) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(if (mediaType == "tv") "📺" else "🎬", fontSize = 26.sp)
            }
        }
    }
}

@Composable
fun RatingPill(rating: Double) {
    Surface(color = Silver, shape = RoundedCornerShape(999.dp)) {
        Text(
            formatRating(rating),
            color = Color(0xFF1B1A16),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun RatingField(initial: Double?, onChange: (Double?) -> Unit) {
    var text by remember { mutableStateOf(initial?.let { formatRating(it) } ?: "") }
    val parsed = text.toDoubleOrNull()
    val invalid = text.isNotBlank() && (parsed == null || parsed < 1.0 || parsed > 10.0)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { raw ->
                    val cleaned = sanitizeRating(raw)
                    text = cleaned
                    val v = cleaned.toDoubleOrNull()
                    onChange(if (v != null && v in 1.0..10.0) roundTo2(v) else null)
                },
                isError = invalid,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                placeholder = { Text("0.00") },
                modifier = Modifier.width(130.dp),
            )
            Text(
                " / 10",
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (invalid) {
            Text(
                "Must be between 1 and 10",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
fun DateTimeRow(millis: Long, onChange: (Long) -> Unit) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Chip(label = "Date", value = formatDate(millis), modifier = Modifier.weight(1f)) {
            val c = Calendar.getInstance().apply { timeInMillis = millis }
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    val nc = Calendar.getInstance().apply {
                        timeInMillis = millis
                        set(Calendar.YEAR, y); set(Calendar.MONTH, m); set(Calendar.DAY_OF_MONTH, d)
                    }
                    onChange(nc.timeInMillis)
                },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH),
            ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
        }
        Chip(label = "Time", value = formatTime(millis), modifier = Modifier.weight(1f)) {
            val c = Calendar.getInstance().apply { timeInMillis = millis }
            TimePickerDialog(
                context,
                { _, h, min ->
                    val nc = Calendar.getInstance().apply {
                        timeInMillis = millis
                        set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min)
                    }
                    onChange(nc.timeInMillis)
                },
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false,
            ).show()
        }
    }
}

@Composable
private fun Chip(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(58.dp).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
