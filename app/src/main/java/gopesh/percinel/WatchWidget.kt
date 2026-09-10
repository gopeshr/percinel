package gopesh.percinel

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import gopesh.percinel.data.Insights
import gopesh.percinel.data.Repo
import java.util.Locale

/**
 * Home-screen widget: the last thing you logged, plus a nudge from the watchlist. Built with
 * plain RemoteViews rather than Glance so it adds no dependency and no download size.
 *
 * Refreshed by the system every half hour and explicitly via [refresh] whenever the diary changes.
 */
class WatchWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val snapshot = readSnapshot(context)
        ids.forEach { id -> manager.updateAppWidget(id, render(context, snapshot)) }
    }

    private data class Snapshot(
        val title: String?,
        val rating: Double?,
        val watchedAt: Long?,
        val totalFilms: Int,
        val nextUp: String?,
    )

    /**
     * onUpdate runs on the main thread, so this stays deliberately cheap: two indexed reads and
     * no taste modelling. Anything heavier belongs in the app, not a widget refresh.
     */
    private fun readSnapshot(context: Context): Snapshot {
        return runCatching {
            val repo = Repo(context)
            val watched = repo.list()
            val latest = watched.maxByOrNull { it.watchedAt }
            Snapshot(
                title = latest?.title,
                rating = latest?.rating,
                watchedAt = latest?.watchedAt,
                totalFilms = Insights.perFilm(watched).size,
                // Newest thing you added to the watchlist — no ranking, just a nudge.
                nextUp = repo.watchlist().firstOrNull()?.title,
            )
        }.getOrElse { Snapshot(null, null, null, 0, null) }
    }

    private fun render(context: Context, s: Snapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_last_watch)

        if (s.title == null) {
            views.setTextViewText(R.id.widget_label, "PERCINEL")
            views.setTextViewText(R.id.widget_title, "Nothing logged yet")
            views.setTextViewText(R.id.widget_meta, "Tap to add your first watch")
            views.setTextViewText(R.id.widget_footer, "")
        } else {
            views.setTextViewText(R.id.widget_label, "LAST WATCHED")
            views.setTextViewText(R.id.widget_title, s.title)
            views.setTextViewText(
                R.id.widget_meta,
                buildString {
                    s.rating?.let { append(String.format(Locale.US, "%.2f", it)).append(" / 10") }
                    s.watchedAt?.let {
                        if (isNotEmpty()) append("   ·   ")
                        append(relativeDay(it))
                    }
                },
            )
            views.setTextViewText(
                R.id.widget_footer,
                s.nextUp?.let { "Next up: $it" }
                    ?: "${s.totalFilms} ${if (s.totalFilms == 1) "film" else "films"} logged",
            )
        }

        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context,
                0,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        return views
    }

    private fun relativeDay(millis: Long): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(
            Insights.dayOf(millis),
            Insights.dayOf(System.currentTimeMillis()),
        )
        return when {
            days <= 0L -> "today"
            days == 1L -> "yesterday"
            days < 7L -> "$days days ago"
            days < 30L -> "${days / 7} ${if (days / 7 == 1L) "week" else "weeks"} ago"
            else -> "${days / 30} ${if (days / 30 == 1L) "month" else "months"} ago"
        }
    }

    companion object {
        /** Push fresh content to every placed widget — call after the diary changes. */
        fun refresh(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(context, WatchWidget::class.java))
                if (ids.isEmpty()) return
                context.sendBroadcast(
                    Intent(context, WatchWidget::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    },
                )
            }
        }
    }
}
