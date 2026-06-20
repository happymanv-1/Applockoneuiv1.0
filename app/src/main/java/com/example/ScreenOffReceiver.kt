package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScreenOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_SCREEN_OFF) {
            context?.let {
                // Clear the unlocked state in the service
                AppLockerService.clearUnlockedState()
            }
        }
    }
}
