package gopesh.percinel.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import gopesh.percinel.data.Repo
import gopesh.percinel.data.SyncManager
import gopesh.percinel.data.SyncResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"

private fun driveAuthRequest(): AuthorizationRequest =
    AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_APPDATA)))
        .build()

/** Interactive connect+sync: shows the native Google account picker / consent when needed. */
class CloudSync internal constructor(val syncing: Boolean, val connect: () -> Unit)

@Composable
fun rememberCloudSync(
    repo: Repo,
    onDone: (SyncResult) -> Unit,
    onError: (String) -> Unit,
): CloudSync {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }

    fun runWith(token: String) {
        scope.launch {
            try {
                onDone(SyncManager.run(repo, token))
            } catch (e: Exception) {
                onError(e.message ?: "Sync failed")
            } finally {
                syncing = false
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val token = try {
            Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(result.data).accessToken
        } catch (_: Exception) {
            null
        }
        if (token != null) runWith(token) else {
            syncing = false
            onError("Couldn't connect to Google")
        }
    }

    val connect: () -> Unit = {
        syncing = true
        Identity.getAuthorizationClient(context).authorize(driveAuthRequest())
            .addOnSuccessListener { r ->
                val pending = r.pendingIntent
                if (r.hasResolution() && pending != null) {
                    launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    val token = r.accessToken
                    if (token != null) runWith(token) else {
                        syncing = false
                        onError("Couldn't get Google access")
                    }
                }
            }
            .addOnFailureListener { e ->
                syncing = false
                onError(e.message ?: "Google sign-in failed")
            }
    }

    return CloudSync(syncing, connect)
}

/**
 * Silent background sync — no UI. Runs only if the user already turned sync on and Google can
 * hand back a token without prompting. Any need for user interaction, or any failure, is a quiet
 * no-op (the next foreground/background tick, or a manual "Sync now", will catch up).
 */
suspend fun autoSync(context: Context, repo: Repo): SyncResult? {
    val prefs = context.getSharedPreferences("percinel", Context.MODE_PRIVATE)
    if (!prefs.getBoolean("sync_on", false)) return null
    val token = silentDriveToken(context) ?: return null
    return try {
        val res = SyncManager.run(repo, token)
        prefs.edit().putLong("last_sync", System.currentTimeMillis()).apply()
        res
    } catch (_: Exception) {
        null
    }
}

private suspend fun silentDriveToken(context: Context): String? =
    suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(context).authorize(driveAuthRequest())
            .addOnSuccessListener { r ->
                // hasResolution() means Google needs UI — can't do that in the background.
                cont.resume(if (r.hasResolution()) null else r.accessToken)
            }
            .addOnFailureListener { cont.resume(null) }
    }
