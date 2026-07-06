package gopesh.percinel.ui

import gopesh.percinel.BuildConfig
import gopesh.percinel.data.Entry
import gopesh.percinel.data.Export
import gopesh.percinel.data.Repo
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(repo: Repo, onMenu: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("percinel", Context.MODE_PRIVATE) }
    var name by remember { mutableStateOf(prefs.getString("name", "") ?: "") }
    var editingName by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var chooseExport by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<List<Entry>?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(Export.MIME),
    ) { uri ->
        val data = pendingSave
        pendingSave = null
        if (uri != null && data != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { Export.writeXlsx(it, data) }
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
                Toast.makeText(
                    context,
                    if (ok) "Saved" else "Couldn't save file",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // --- Cloud sync ---
    var syncOn by remember { mutableStateOf(prefs.getBoolean("sync_on", false)) }
    var lastSync by remember { mutableStateOf(prefs.getLong("last_sync", 0L)) }
    var confirmSyncOff by remember { mutableStateOf(false) }

    val cloud = rememberCloudSync(
        repo = repo,
        onDone = { res ->
            syncOn = true
            lastSync = System.currentTimeMillis()
            prefs.edit().putBoolean("sync_on", true).putLong("last_sync", lastSync).apply()
            Toast.makeText(context, "Synced · ${res.total} watches", Toast.LENGTH_SHORT).show()
        },
        onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() },
    )
    val syncing = cloud.syncing

    fun exportText(list: List<Entry>) {
        val text = buildString {
            appendLine("percinel — my watches")
            appendLine()
            list.forEach { e ->
                append(e.title)
                if (e.year != null) append(" (${e.year})")
                append(" — ${if (e.mediaType == "tv") "Series" else "Movie"}")
                append(" — ${formatRating(e.rating)}/10")
                append(" — ${formatListDate(e.watchedAt)}")
                appendLine()
                if (!e.notes.isNullOrBlank()) appendLine("  “${e.notes}”")
            }
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "My percinel watches")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Export your watches"))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
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
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            ActionRow(
                title = "Your name",
                subtitle = name.ifBlank { "Not set — tap to add" },
                onClick = { draft = name; editingName = true },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            ActionRow(
                title = if (syncOn) "Sync now" else "Back up & sync",
                subtitle = when {
                    syncing -> "Syncing…"
                    syncOn -> "On · synced ${relativeTime(lastSync)}"
                    else -> "Save your watches to your Google Drive"
                },
                onClick = { if (!syncing) cloud.connect() },
            )
            if (syncOn) {
                ActionRow(
                    title = "Turn off sync",
                    subtitle = "Stop backing up — your watches stay on this device",
                    onClick = { confirmSyncOff = true },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            ActionRow(
                title = "Export your watches",
                subtitle = "Save or share as a spreadsheet or text",
                onClick = { chooseExport = true },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            ActionRow(
                title = "Clear everything",
                subtitle = "Delete all your watches from this device",
                destructive = true,
                onClick = { confirmClear = true },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            InfoRow(
                "Your data",
                if (syncOn) "On this device + your private Google Drive" else "Everything stays on this device",
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            // Long-press the version to toggle a hidden "test update" mode (forces the update
            // banner on next launch, so the download/install flow can be tested without releasing).
            Column(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            val on = !prefs.getBoolean("dev_force_update", false)
                            prefs.edit().putBoolean("dev_force_update", on).apply()
                            Toast.makeText(
                                context,
                                if (on) "Update testing ON — reopen the app" else "Update testing off",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                    .padding(vertical = 16.dp),
            ) {
                Text("Version", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    BuildConfig.VERSION_NAME,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp)
                    .clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.themoviedb.org/")),
                        )
                    },
            ) {
                Image(
                    painter = painterResource(gopesh.percinel.R.drawable.tmdb_logo),
                    contentDescription = "The Movie Database",
                    modifier = Modifier.height(12.dp),
                )
                Text(
                    "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }

    if (editingName) {
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("Your name") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    placeholder = { Text("What should we call you?") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    name = draft.trim()
                    prefs.edit().putString("name", name).apply()
                    editingName = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text("Cancel") } },
        )
    }

    if (chooseExport) {
        fun withList(action: (List<Entry>) -> Unit) {
            chooseExport = false
            scope.launch {
                val list = withContext(Dispatchers.IO) { repo.list() }
                if (list.isEmpty()) {
                    Toast.makeText(context, "Nothing to export yet", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                action(list)
            }
        }

        AlertDialog(
            onDismissRequest = { chooseExport = false },
            title = { Text("Export your watches") },
            text = {
                Column {
                    ExportOption(
                        title = "Save spreadsheet to device",
                        subtitle = "Choose where — Downloads by default",
                        onClick = {
                            withList { list ->
                                pendingSave = list
                                saveLauncher.launch(Export.FILENAME)
                            }
                        },
                    )
                    ExportOption(
                        title = "Share spreadsheet",
                        subtitle = "Send the .xlsx to another app",
                        onClick = {
                            withList { list ->
                                val uri = Export.xlsx(context, list)
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = Export.MIME
                                    putExtra(Intent.EXTRA_SUBJECT, "My percinel watches")
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, "Share your watches"))
                            }
                        },
                    )
                    ExportOption(
                        title = "Share as text",
                        subtitle = "A quick plain-text summary",
                        onClick = { withList { list -> exportText(list) } },
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { chooseExport = false }) { Text("Cancel") } },
        )
    }

    if (confirmSyncOff) {
        AlertDialog(
            onDismissRequest = { confirmSyncOff = false },
            title = { Text("Turn off sync?") },
            text = { Text("percinel will stop backing up to Google Drive. Your watches stay on this device, and your Drive backup is left untouched — turn sync back on any time to restore it.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSyncOff = false
                    syncOn = false
                    prefs.edit().putBoolean("sync_on", false).apply()
                }) { Text("Turn off") }
            },
            dismissButton = { TextButton(onClick = { confirmSyncOff = false }) { Text("Cancel") } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear everything?") },
            text = { Text("This permanently deletes all your watches. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.clearAll() }
                        Toast.makeText(context, "All watches cleared", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
            )
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun relativeTime(epoch: Long): String {
    if (epoch <= 0L) return "just now"
    val diff = System.currentTimeMillis() - epoch
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}

@Composable
private fun ExportOption(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun InfoRow(title: String, subtitle: String) {
    Column(Modifier.padding(vertical = 16.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}
