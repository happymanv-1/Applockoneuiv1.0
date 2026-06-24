package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScreenOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_SCREEN_OFF) {
            context?.let { ctx ->
                // Clear the unlocked state in the service
                AppLockerService.clearUnlockedState()

                // Broadcast ACTION_CLOSE_OVERLAY to shut down any active OverlayActivity
                val closeIntent = Intent("ACTION_CLOSE_OVERLAY").apply {
                    setPackage(ctx.packageName)
                }
                ctx.sendBroadcast(closeIntent)
            }
        }
    }
}
