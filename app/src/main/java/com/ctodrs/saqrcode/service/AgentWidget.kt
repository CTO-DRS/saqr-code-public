package com.ctodrs.saqrcode.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ctodrs.saqrcode.MainActivity
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.agent.TileModel

/**
 * v0.1.25: home-screen widget with the same run-state line as the Quick Settings tile
 * ([TileModel]). Tapping it opens the app (on the waiting project when a turn needs the user).
 * [refreshAll] is called by [AgentRuntime] whenever the run state changes.
 */
class AgentWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, build(context)) }
    }

    companion object {
        fun build(ctx: Context): RemoteViews {
            val runs = AgentRuntime.runs()
            val m = TileModel.of(runs.map { it.project to it.waiting }, AgentRuntime.lang() == "ar")
            val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            runs.firstOrNull { it.waiting }?.let { i.putExtra(MainActivity.EXTRA_PROJECT, it.project) }
            val pi = PendingIntent.getActivity(ctx, 7083, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return RemoteViews(ctx.packageName, R.layout.widget_agent).apply {
                setTextViewText(R.id.widget_title, ctx.getString(R.string.app_name))
                setTextViewText(R.id.widget_sub, m.subtitle)
                setOnClickPendingIntent(R.id.widget_root, pi)
            }
        }

        fun refreshAll(ctx: Context) = runCatching {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, AgentWidget::class.java))
            if (ids.isNotEmpty()) ids.forEach { mgr.updateAppWidget(it, build(ctx)) }
        }
    }
}
