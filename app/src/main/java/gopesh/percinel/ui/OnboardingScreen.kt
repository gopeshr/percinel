package gopesh.percinel.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gopesh.percinel.R
import gopesh.percinel.data.Repo

@Composable
fun OnboardingScreen(repo: Repo, onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("percinel", Context.MODE_PRIVATE) }
    var name by remember { mutableStateOf("") }
    var backupOn by remember { mutableStateOf(false) }

    val cloud = rememberCloudSync(
        repo = repo,
        onDone = {
            backupOn = true
            prefs.edit()
                .putBoolean("sync_on", true)
                .putLong("last_sync", System.currentTimeMillis())
                .apply()
            Toast.makeText(context, "Backup is on", Toast.LENGTH_SHORT).show()
        },
        onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() },
    )

    fun finish() {
        prefs.edit()
            .putString("name", name.trim())
            .putBoolean("onboarded", true)
            .apply()
        onDone()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .then(rememberBouncy())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp))) {
                Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
            }

            Spacer(Modifier.height(20.dp))
            Text("Welcome to", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Wordmark(fontSize = 34.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "your movie diary",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )

            Spacer(Modifier.height(40.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("What should we call you?") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
            if (backupOn) {
                Text(
                    "✓  Google Drive backup is on",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                )
            } else {
                TextButton(onClick = { if (!cloud.syncing) cloud.connect() }) {
                    if (cloud.syncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text("  Connecting…")
                    } else {
                        Text("Back up to Google Drive (optional)")
                    }
                }
                Text(
                    "Keep your watches safe and synced across devices. You can also do this later in Settings.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                )
            }

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { finish() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Get started", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
