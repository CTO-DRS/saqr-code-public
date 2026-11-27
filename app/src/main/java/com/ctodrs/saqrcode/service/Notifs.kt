package com.ctodrs.saqrcode.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ctodrs.saqrcode.MainActivity
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.agent.NotifText
import com.ctodrs.saqrcode.agent.RunInfo
import com.ctodrs.saqrcode.agent.RunOutcome
import com.ctodrs.saqrcode.agent.RunWait

/** Builds and posts the agent notifications (channels are created lazily, names follow the app language). */
object Notifs {
    const val CH_RUN = "agent_run"
    const val CH_ATTENTION = "agent_attention"
    const val CH_DONE = "agent_done"
    const val ID_ONGOING = 4100
    private const val ID_RESUME = 4101
    const val GOLD = 0xFFE9B44C.toInt()

    private fun idFor(project: String, kind: Int) = 5000 + (project.hashCode() and 0xFFFF) * 4 + kind

    fun ensureChannels(ctx: Context, lang: String) {
        val nm = NotificationManagerCompat.from(ctx)
        nm.createNotificationChannelsCompat(listOf(
            NotificationChannelCompat.Builder(CH_RUN, NotificationManagerCompat.IMPORTANCE_LOW).setName(NotifText.channelRun(lang)).setShowBadge(false).build(),
            NotificationChannelCompat.Builder(CH_ATTENTION, NotificationManagerCompat.IMPORTANCE_HIGH).setName(NotifText.channelAttention(lang)).build(),
            NotificationChannelCompat.Builder(CH_DONE, NotificationManagerCompat.IMPORTANCE_DEFAULT).setName(NotifText.channelDone(lang)).build(),
        ))
    }

    private fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private const val IMMUTABLE = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    /** Opens the project's chat (and, with [resume], continues an interrupted turn). */
    fun openIntent(ctx: Context, project: String?, resume: Boolean = false, req: Int = 0): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setAction(if (resume) MainActivity.ACTION_RESUME else MainActivity.ACTION_OPEN)
        if (project != null) i.putExtra(MainActivity.EXTRA_PROJECT, project)
        return PendingIntent.getActivity(ctx, req + (project?.hashCode() ?: 0) * 8 + if (resume) 1 else 0, i, IMMUTABLE)
    }

    private fun serviceIntent(ctx: Context, action: String, project: String, req: Int, extras: Intent.() -> Unit = {}): PendingIntent {
        val i = Intent(ctx, AgentService::class.java).setAction(action).putExtra(MainActivity.EXTRA_PROJECT, project).apply(extras)
        return PendingIntent.getService(ctx, req, i, IMMUTABLE)
    }

    /** The foreground notification: project · step · current tool, a chronometer, Stop and Open. */
    fun ongoing(ctx: Context, run: RunInfo?, others: Int, lang: String): Notification {
        ensureChannels(ctx, lang)
        val b = NotificationCompat.Builder(ctx, CH_RUN)
            .setSmallIcon(R.drawable.ic_stat_saqr).setColor(GOLD).setColorized(false)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (run == null) {
            return b.setContentTitle(ctx.getString(R.string.app_name)).setContentText(NotifText.activity(com.ctodrs.saqrcode.agent.RunActivity.THINKING, lang))
                .setContentIntent(openIntent(ctx, null)).build()
        }
        return b.setContentTitle(NotifText.ongoingTitle(run, others, lang))
            .setContentText(NotifText.ongoing(run, lang))
            .setWhen(run.startedAt).setUsesChronometer(true).setShowWhen(true)
            .setProgress(0, 0, run.wait == null)
            .setContentIntent(openIntent(ctx, run.project))
            .addAction(0, NotifText.stop(lang), serviceIntent(ctx, AgentService.ACTION_STOP, run.project, 11))
            .addAction(0, NotifText.open(lang), openIntent(ctx, run.project, req = 2))
            .build()
    }

    /** "Saqr needs your answer": opens the card; single-choice questions with ≤ 3 options get inline buttons. */
    @SuppressLint("MissingPermission") // checked by canPost()
    fun attention(ctx: Context, project: String, w: RunWait, lang: String) {
        if (!canPost(ctx)) return
        ensureChannels(ctx, lang)
        val body = NotifText.attentionBody(project, w, lang)
        val b = NotificationCompat.Builder(ctx, CH_ATTENTION)
            .setSmallIcon(R.drawable.ic_stat_saqr).setColor(GOLD)
            .setContentTitle(NotifText.attentionTitle(lang)).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true).setContentIntent(openIntent(ctx, project, req = 3))
        if (w.questionId != null) w.options.take(RunWait.MAX_INLINE).forEachIndexed { i, (id, label) ->
            b.addAction(0, label, serviceIntent(ctx, AgentService.ACTION_ANSWER, project, 20 + i + (project.hashCode() and 0xFFF) * 32) {
                putExtra(AgentService.EXTRA_QUESTION, w.questionId); putExtra(AgentService.EXTRA_OPTION, id)
            })
        }
        runCatching { NotificationManagerCompat.from(ctx).notify(idFor(project, 1), b.build()) }
    }

    fun cancelAttention(ctx: Context, project: String) = NotificationManagerCompat.from(ctx).cancel(idFor(project, 1))

    @SuppressLint("MissingPermission") // checked by canPost()
    fun finished(ctx: Context, project: String, o: RunOutcome, lang: String) {
        if (!canPost(ctx)) return
        ensureChannels(ctx, lang)
        val body = NotifText.finishedBody(project, o, lang)
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_saqr).setColor(GOLD)
            .setContentTitle(NotifText.finishedTitle(o, lang)).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true).setContentIntent(openIntent(ctx, project, req = 4))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(idFor(project, 2), n) }
    }

    /** After a reboot: offer to resume (a tap opens the app; nothing runs on its own at boot). */
    @SuppressLint("MissingPermission") // checked by canPost()
    fun resumeOffer(ctx: Context, projects: Set<String>, lang: String) {
        if (projects.isEmpty() || !canPost(ctx)) return
        ensureChannels(ctx, lang)
        val first = projects.first()
        val body = NotifText.resumeBody(projects, lang)
        val n = NotificationCompat.Builder(ctx, CH_ATTENTION)
            .setSmallIcon(R.drawable.ic_stat_saqr).setColor(GOLD)
            .setContentTitle(NotifText.resumeTitle(lang)).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx, first, resume = true, req = 5))
            .addAction(0, NotifText.resume(lang), openIntent(ctx, first, resume = true, req = 6))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID_RESUME, n) }
    }

    private const val ID_DIGEST = 4103

    /** v0.1.22: the Sentinel morning digest. */
    fun digest(ctx: Context, title: String, body: String, lang: String) {
        if (!canPost(ctx)) return
        ensureChannels(ctx, lang)
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_saqr).setColor(GOLD)
            .setContentTitle(title).setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx, null, req = 7))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID_DIGEST, n) }
    }

    fun cancelResumeOffer(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID_RESUME)

    // ---- v0.13.4: hands-free dictation

    private const val ID_DICTATION = 4102

    /** Ongoing, silent note while continuous dictation listens; Stop flips the persisted mode. */
    @SuppressLint("MissingPermission") // checked by canPost()
    fun dictation(ctx: Context, lang: String) {
        if (!canPost(ctx)) return
        ensureChannels(ctx, lang)
        val n = NotificationCompat.Builder(ctx, CH_RUN)
            .setSmallIcon(R.drawable.ic_stat_saqr).setColor(GOLD)
            .setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentTitle(NotifText.dictationTitle(lang))
            .setContentText(NotifText.dictationBody(lang))
            .setContentIntent(openIntent(ctx, null, req = 7))
            .addAction(0, NotifText.stop(lang), dictationStopIntent(ctx))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID_DICTATION, n) }
    }

    fun cancelDictation(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID_DICTATION)

    private fun dictationStopIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, DictationReceiver::class.java).setAction(DictationReceiver.ACTION_STOP)
        return PendingIntent.getBroadcast(ctx, 41, i, IMMUTABLE)
    }
}
