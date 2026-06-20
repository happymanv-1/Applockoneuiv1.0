package com.example.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppPreferences
import com.example.data.AppDatabase
import com.example.data.BlockEvent
import com.example.domain.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AppLockerViewModel(application: Application) : AndroidViewModel(application) {

    private val appPreferences = AppPreferences(application)

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val filteredApps: StateFlow<List<AppInfo>> = kotlinx.coroutines.flow.combine(
        _installedApps,
        _searchQuery
    ) { apps, query ->
        if (query.isBlank()) {
            apps
        } else {
            apps.filter { it.appName.contains(query, ignoreCase = true) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    val lockedApps: StateFlow<Set<String>> = appPreferences.lockedAppsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )

    val appCategories: StateFlow<Map<String, String>> = appPreferences.appCategoriesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    val appTimers: StateFlow<Map<String, Long>> = appPreferences.appTimersFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    val gracePeriodMs: StateFlow<Long> = appPreferences.gracePeriodFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 60_000L
    )

    val relockOnScreenOff: StateFlow<Boolean> = appPreferences.relockOnScreenOffFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val unlockPattern: StateFlow<String?> = appPreferences.unlockPatternFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val useDeviceLock: StateFlow<Boolean> = appPreferences.useDeviceLockFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            val apps = resolveInfos.mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                if (packageName == getApplication<Application>().packageName) {
                    return@mapNotNull null
                }
                
                try {
                    val appName = resolveInfo.loadLabel(pm).toString()
                    val icon = resolveInfo.loadIcon(pm)
                    AppInfo(packageName, appName, icon)
                } catch (e: Exception) {
                    Log.e("AppLockerViewModel", "Error loading app info", e)
                    null
                }
            }.sortedBy { it.appName.lowercase() }
            
            _installedApps.value = apps
            _isLoading.value = false
        }
    }

    fun lockApp(packageName: String, category: String, customGracePeriodMs: Long) {
        viewModelScope.launch {
            appPreferences.lockApp(packageName, category, customGracePeriodMs)
        }
    }

    fun unlockApp(packageName: String) {
        viewModelScope.launch {
            appPreferences.unlockApp(packageName)
        }
    }

    fun toggleAppLock(packageName: String) {
        viewModelScope.launch {
            appPreferences.toggleAppLock(packageName)
        }
    }

    fun setGracePeriodMs(timeoutMs: Long) {
        viewModelScope.launch {
            appPreferences.setGracePeriod(timeoutMs)
        }
    }

    fun setRelockOnScreenOff(relock: Boolean) {
        viewModelScope.launch {
            appPreferences.setRelockOnScreenOff(relock)
        }
    }

    fun setUnlockPattern(pattern: String) {
        viewModelScope.launch {
            appPreferences.setUnlockPattern(pattern)
        }
    }

    fun setUseDeviceLock(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setUseDeviceLock(enabled)
        }
    }

    private val database = AppDatabase.getDatabase(getApplication())
    val blockEventsFlow = database.blockEventDao().getAllEventsFlow()

    // Analytics JSON Flow for the WebView Recharts
    val analyticsJson: StateFlow<String> = blockEventsFlow.map { events ->
        val totalBlocks = events.size
        
        // Sum estimated minutes saved (5 mins / 300 seconds per block event)
        val totalTimeSavedMinutes = events.sumOf { it.estimatedTimeSavedSeconds } / 60

        // Get Top Blocked Apps (up to 5)
        val appCounts = events.groupBy { it.appLabel }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .map { entry ->
                mapOf(
                    "name" to entry.key,
                    "count" to entry.value,
                    "time" to entry.value * 5
                )
            }

        // Get Last 7 Days History from furthest to today
        val historyList = mutableListOf<Map<String, Any>>()
        val sdf = SimpleDateFormat("EEE", Locale.getDefault()) // "Mon", "Tue", etc.
        
        for (i in 6 downTo 0) {
            val dateCal = Calendar.getInstance()
            dateCal.add(Calendar.DAY_OF_YEAR, -i)
            
            val label = sdf.format(dateCal.time)
            
            // Start of day
            dateCal.set(Calendar.HOUR_OF_DAY, 0)
            dateCal.set(Calendar.MINUTE, 0)
            dateCal.set(Calendar.SECOND, 0)
            val startMs = dateCal.timeInMillis
            
            // End of day
            dateCal.set(Calendar.HOUR_OF_DAY, 23)
            dateCal.set(Calendar.MINUTE, 59)
            dateCal.set(Calendar.SECOND, 59)
            val endMs = dateCal.timeInMillis

            val countInDay = events.count { it.timestamp in startMs..endMs }
            historyList.add(
                mapOf(
                    "date" to label,
                    "count" to countInDay
                )
            )
        }

        // Generate JSON string safely
        val appsJson = appCounts.joinToString(prefix = "[", postfix = "]") { app ->
            """{"name":"${app["name"]}","count":${app["count"]},"time":${app["time"]}}"""
        }

        val historyJson = historyList.joinToString(prefix = "[", postfix = "]") { day ->
            """{"date":"${day["date"]}","count":${day["count"]}}"""
        }

        val json = """
            {
              "totalBlocks": $totalBlocks,
              "totalTimeSavedMinutes": $totalTimeSavedMinutes,
              "appsData": $appsJson,
              "historyData": $historyJson
            }
        """.trimIndent()
        
        json
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "{}"
    )

    fun insertSampleData() {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(getApplication())
            val apps = listOf(
                Pair("com.instagram.android", "Instagram"),
                Pair("com.facebook.katana", "Facebook"),
                Pair("com.zhiliaoapp.musically", "TikTok"),
                Pair("com.google.android.youtube", "YouTube")
            )
            
            // Clear current data first for clean reset
            db.blockEventDao().clearAllEvents()

            // Generate some random blocks spread over the last 7 days
            for (i in 1..28) {
                val app = apps.random()
                val randomDaysAgo = (0..6).random()
                val calendarInstance = Calendar.getInstance()
                calendarInstance.add(Calendar.DAY_OF_YEAR, -randomDaysAgo)
                calendarInstance.set(Calendar.HOUR_OF_DAY, (8..22).random())
                calendarInstance.set(Calendar.MINUTE, (0..59).random())
                
                val event = BlockEvent(
                    packageName = app.first,
                    appLabel = app.second,
                    timestamp = calendarInstance.timeInMillis
                )
                db.blockEventDao().insertEvent(event)
            }
        }
    }

    fun clearStats() {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(getApplication())
            db.blockEventDao().clearAllEvents()
        }
    }
}
