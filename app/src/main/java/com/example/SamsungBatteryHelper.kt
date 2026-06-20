package com.example

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

object SamsungBatteryHelper {
    private const val TAG = "SamsungBatteryHelper"

    /**
     * Attempts to open the specific Samsung Device Care/Battery exemption screen,
     * falling back to standard Android battery optimization settings.
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val samsungIntents = listOf(
            // Intent 1: Samsung Device Care Dashboard / Battery
            Intent().apply {
                component = ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // Intent 2: Alternative Samsung Device Care Battery Intent
            Intent().apply {
                component = ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // Intent 3: Samsung Device Care Dashboard Activity
            Intent().apply {
                component = ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.DashboardActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // Intent 4: Samsung Device Care Main / Settings
            Intent("com.samsung.android.sm.ACTION_BATTERY").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // Intent 5: China Samsung Devices
            Intent().apply {
                component = ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.ram.RamActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // Intent 6: Samsung Auto-Run / Background activity settings
            Intent().apply {
                component = ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.ram.RamActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )

        for (intent in samsungIntents) {
            try {
                context.startActivity(intent)
                Log.d(TAG, "Successfully started Samsung specialized activity: ${intent.component?.className}")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start Samsung activity: ${intent.component?.className ?: intent.action}", e)
            }
        }

        // Fallback 1: Standard Ignore Battery Optimization prompt for this app directly (high priority)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                if (!isIgnoring) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    Log.d(TAG, "Successfully started ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, trying general settings", e)
            }
        }

        // Fallback 2: General Battery Optimization Settings screen
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Successfully started ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS")
        } catch (e: Exception) {
            Log.e(TAG, "All intents failed to start battery exemption screens", e)
            // Fallback 3: Device general settings
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                Log.e(TAG, "Absolute fallback Settings failed", ex)
            }
        }
    }

    /**
     * Checks if the app is currently ignoring battery optimizations.
     */
    fun isBatteryOptimizingIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        }
        return true
    }
}
