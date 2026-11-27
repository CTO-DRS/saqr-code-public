package com.ctodrs.saqrcode.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.ctodrs.saqrcode.MainActivity
import com.ctodrs.saqrcode.agent.TileModel
import com.ctodrs.saqrcode.ui.Workspace

/**
 * v0.1.24: Quick Settings tile. Shows whether Saqr is running; tapping it while a turn runs stops
 * that turn (nothing is deleted, work so far is kept), tapping it otherwise opens the app, and a
 * turn that is waiting for the user opens the app on that project.
 */
class AgentTileService : TileService() {
    private fun model() = TileModel.of(AgentRuntime.runs().map { it.project to it.waiting }, AgentRuntime.lang() == "ar")

    override fun onStartListening() { render() }

    private fun render() {
        val t = qsTile ?: return
        val m = model()
        t.state = if (m.state == TileModel.State.IDLE) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        t.label = m.label
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = m.subtitle
        t.updateTile()
    }

    override fun onClick() {
        val m = model()
        if (m.tapStops) {
            AgentRuntime.runs().forEach { r -> Workspace.peek(r.project)?.agent?.stop() }
            render()
            return
        }
        val project = AgentRuntime.runs().firstOrNull { it.waiting }?.project
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        project?.let { i.putExtra(MainActivity.EXTRA_PROJECT, it) }
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 7082, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(i)
        }
    }
}
