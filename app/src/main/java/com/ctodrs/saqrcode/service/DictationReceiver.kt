package com.ctodrs.saqrcode.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ctodrs.saqrcode.SaqrApp
import com.ctodrs.saqrcode.agent.Dictation

/**
 * v0.13.4: the hands-free notification's Stop action. It only flips the persisted mode —
 * the project screen observes the settings change and tears the recognizer down reactively.
 * Starting mic capture from a notification is deliberately NOT supported: Android restricts
 * microphone access from the background, and Saqr never listens without the app in hand.
 */
class DictationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP) return
        (context.applicationContext as? SaqrApp)?.settings?.let { s ->
            s.dictation = Dictation.Mode.OFF.key
        }
        Notifs.cancelDictation(context)
    }

    companion object {
        const val ACTION_STOP = "com.ctodrs.saqrcode.action.DICTATION_STOP"
    }
}
