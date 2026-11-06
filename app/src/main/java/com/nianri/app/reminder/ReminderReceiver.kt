package com.nianri.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_DAILY_CHECK) return
        val pendingResult = goAsync()
        Thread {
            try {
                ReminderEngine.deliverDueReminders(context.applicationContext)
            } finally {
                ReminderScheduler.scheduleNext(context.applicationContext)
                pendingResult.finish()
            }
        }.start()
    }
}
