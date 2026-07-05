package gopesh.percinel.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SyncResult(val total: Int, val added: Int)

/**
 * One full two-way sync: pull the cloud copy, union-merge with local (newest wins, no deletes),
 * write the merged set back to the device, and push the merged set to the cloud. Safe to run
 * repeatedly — it converges. Runs on IO.
 */
object SyncManager {
    suspend fun run(repo: Repo, token: String): SyncResult = withContext(Dispatchers.IO) {
        val drive = Drive(token)
        val fileId = drive.findFileId()
        val remote = if (fileId != null) Sync.parse(drive.download(fileId)) else emptyList()

        val local = repo.allForSync()
        val merged = Sync.merge(local, remote)

        repo.applyMerge(merged)

        val payload = Sync.serialize(merged)
        if (fileId == null) drive.create(payload) else drive.update(fileId, payload)

        SyncResult(total = merged.size, added = (merged.size - local.size).coerceAtLeast(0))
    }
}
