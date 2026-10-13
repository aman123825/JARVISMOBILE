package com.mindfortress.app.data

import android.content.Context
import android.content.SharedPreferences
import com.mindfortress.app.service.BlockerPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mind_fortress_prefs", Context.MODE_PRIVATE)

    private val _blockedCountFlow = MutableStateFlow(0)
    val blockedCountFlow: StateFlow<Int> = _blockedCountFlow.asStateFlow()

    private val _cognitivePointsFlow = MutableStateFlow(0)
    val cognitivePointsFlow: StateFlow<Int> = _cognitivePointsFlow.asStateFlow()

    private val _dailyScreenOnMinutesFlow = MutableStateFlow(0)
    val dailyScreenOnMinutesFlow: StateFlow<Int> = _dailyScreenOnMinutesFlow.asStateFlow()

    private val _dailyUnlocksCountFlow = MutableStateFlow(0)
    val dailyUnlocksCountFlow: StateFlow<Int> = _dailyUnlocksCountFlow.asStateFlow()

    private val _dailyShortsBlockedFlow = MutableStateFlow(0)
    val dailyShortsBlockedFlow: StateFlow<Int> = _dailyShortsBlockedFlow.asStateFlow()

    init {
        _blockedCountFlow.value = getBlockedCount()
        _cognitivePointsFlow.value = getCognitivePoints()
        checkAndResetDailyStats()
        _dailyScreenOnMinutesFlow.value = prefs.getInt(KEY_DAILY_SCREEN_ON_MINUTES, 0)
        _dailyUnlocksCountFlow.value = prefs.getInt(KEY_DAILY_UNLOCKS_COUNT, 0)
        _dailyShortsBlockedFlow.value = prefs.getInt(KEY_DAILY_SHORTS_BLOCKED, 0)
    }

    var isWebShieldActive: Boolean
        get() = prefs.getBoolean(KEY_WEB_SHIELD, true)
        set(value) = prefs.edit().putBoolean(KEY_WEB_SHIELD, value).apply()

    var isAppBlockerActive: Boolean
        get() = prefs.getBoolean(KEY_APP_BLOCKER, true)
        set(value) = prefs.edit().putBoolean(KEY_APP_BLOCKER, value).apply()

    var isMonkModeActive: Boolean
        get() = prefs.getBoolean(KEY_MONK_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_MONK_MODE, value).apply()

    var isSituationalGuardianActive: Boolean
        get() = prefs.getBoolean(KEY_SITUATIONAL_GUARDIAN, false)
        set(value) = prefs.edit().putBoolean(KEY_SITUATIONAL_GUARDIAN, value).apply()

    var isInvisibleInterventionMode: Boolean
        get() = prefs.getBoolean(KEY_INVISIBLE_INTERVENTION, true)
        set(value) = prefs.edit().putBoolean(KEY_INVISIBLE_INTERVENTION, value).apply()

    var isHandsFreeVoiceActive: Boolean
        get() = prefs.getBoolean(KEY_HANDS_FREE_VOICE, true)
        set(value) = prefs.edit().putBoolean(KEY_HANDS_FREE_VOICE, value).apply()

    var brainServerUrl: String
        get() = prefs.getString(KEY_BRAIN_SERVER_URL, "http://10.0.2.2:8080") ?: "http://10.0.2.2:8080"
        set(value) = prefs.edit().putString(KEY_BRAIN_SERVER_URL, value).apply()

    fun getBlockedPackages(): Set<String> {
        val custom = prefs.getStringSet(KEY_CUSTOM_BLOCKED_PACKAGES, emptySet()) ?: emptySet()
        return BlockerPolicy.DEFAULT_BLOCKED_PACKAGES + custom
    }

    fun getCustomBlockedPackages(): Set<String> {
        return prefs.getStringSet(KEY_CUSTOM_BLOCKED_PACKAGES, emptySet()) ?: emptySet()
    }

    fun addBlockedPackage(pkg: String) {
        val current = prefs.getStringSet(KEY_CUSTOM_BLOCKED_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(pkg)
        prefs.edit().putStringSet(KEY_CUSTOM_BLOCKED_PACKAGES, current).apply()
    }

    fun removeBlockedPackage(pkg: String) {
        val current = prefs.getStringSet(KEY_CUSTOM_BLOCKED_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.remove(pkg)
        prefs.edit().putStringSet(KEY_CUSTOM_BLOCKED_PACKAGES, current).apply()
    }

    fun getBlockedCount(): Int = prefs.getInt(KEY_BLOCKED_COUNT, 0)

    fun incrementBlockedCount() {
        val newCount = getBlockedCount() + 1
        prefs.edit().putInt(KEY_BLOCKED_COUNT, newCount).apply()
        _blockedCountFlow.value = newCount
    }

    fun getCognitivePoints(): Int = prefs.getInt(KEY_COGNITIVE_POINTS, 0)

    fun addCognitivePoints(pts: Int) {
        val newPts = getCognitivePoints() + pts
        prefs.edit().putInt(KEY_COGNITIVE_POINTS, newPts).apply()
        _cognitivePointsFlow.value = newPts
    }

    var frontierApiKey: String
        get() = prefs.getString(KEY_FRONTIER_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_FRONTIER_API_KEY, value).apply()

    var frontierProvider: String
        get() = prefs.getString(KEY_FRONTIER_PROVIDER, "PC_BRAIN") ?: "PC_BRAIN"
        set(value) = prefs.edit().putString(KEY_FRONTIER_PROVIDER, value).apply()

    var isFloatingOverlayActive: Boolean
        get() = prefs.getBoolean(KEY_FLOATING_OVERLAY, true)
        set(value) = prefs.edit().putBoolean(KEY_FLOATING_OVERLAY, value).apply()

    var userRealName: String
        get() = prefs.getString(KEY_USER_NAME, "Vivek") ?: "Vivek"
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var targetSleepTime: String
        get() = prefs.getString(KEY_SLEEP_TIME, "22:30") ?: "22:30"
        set(value) = prefs.edit().putString(KEY_SLEEP_TIME, value).apply()

    var targetWakeTime: String
        get() = prefs.getString(KEY_WAKE_TIME, "06:30") ?: "06:30"
        set(value) = prefs.edit().putString(KEY_WAKE_TIME, value).apply()

    var primaryAmbition: String
        get() = prefs.getString(KEY_AMBITION, "MIT STEM Mastery & Peak Cognitive Discipline") ?: "MIT STEM Mastery & Peak Cognitive Discipline"
        set(value) = prefs.edit().putString(KEY_AMBITION, value).apply()

    var voiceSpeechRate: Float
        get() = prefs.getFloat(KEY_VOICE_SPEECH_RATE, 1.05f)
        set(value) = prefs.edit().putFloat(KEY_VOICE_SPEECH_RATE, value).apply()

    var voicePitch: Float
        get() = prefs.getFloat(KEY_VOICE_PITCH, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_VOICE_PITCH, value).apply()

    var isAcousticRadarActive: Boolean
        get() = prefs.getBoolean(KEY_ACOUSTIC_RADAR, true)
        set(value) = prefs.edit().putBoolean(KEY_ACOUSTIC_RADAR, value).apply()

    var isLightSensingActive: Boolean
        get() = prefs.getBoolean(KEY_LIGHT_SENSING, true)
        set(value) = prefs.edit().putBoolean(KEY_LIGHT_SENSING, value).apply()

    var isMotionTrackingActive: Boolean
        get() = prefs.getBoolean(KEY_MOTION_TRACKING, true)
        set(value) = prefs.edit().putBoolean(KEY_MOTION_TRACKING, value).apply()

    fun getStreak(): Int = prefs.getInt(KEY_STREAK, 1)

    fun setStreak(days: Int) {
        prefs.edit().putInt(KEY_STREAK, days).apply()
    }

    private fun checkAndResetDailyStats() {
        val currentDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        val savedDay = prefs.getInt(KEY_STATS_DAY_OF_YEAR, -1)
        if (savedDay != currentDay) {
            prefs.edit()
                .putInt(KEY_STATS_DAY_OF_YEAR, currentDay)
                .putInt(KEY_DAILY_SCREEN_ON_MINUTES, 0)
                .putInt(KEY_DAILY_UNLOCKS_COUNT, 0)
                .putInt(KEY_DAILY_SHORTS_BLOCKED, 0)
                .apply()
            _dailyScreenOnMinutesFlow.value = 0
            _dailyUnlocksCountFlow.value = 0
            _dailyShortsBlockedFlow.value = 0
        }
    }

    fun getDailyScreenOnMinutes(): Int {
        checkAndResetDailyStats()
        return prefs.getInt(KEY_DAILY_SCREEN_ON_MINUTES, 0)
    }

    fun addScreenOnMinutes(minutes: Int) {
        checkAndResetDailyStats()
        val updated = getDailyScreenOnMinutes() + minutes
        prefs.edit().putInt(KEY_DAILY_SCREEN_ON_MINUTES, updated).apply()
        _dailyScreenOnMinutesFlow.value = updated
    }

    fun getDailyUnlocksCount(): Int {
        checkAndResetDailyStats()
        return prefs.getInt(KEY_DAILY_UNLOCKS_COUNT, 0)
    }

    fun incrementDailyUnlocks() {
        checkAndResetDailyStats()
        val updated = getDailyUnlocksCount() + 1
        prefs.edit().putInt(KEY_DAILY_UNLOCKS_COUNT, updated).apply()
        _dailyUnlocksCountFlow.value = updated
    }

    fun getDailyShortsBlocked(): Int {
        checkAndResetDailyStats()
        return prefs.getInt(KEY_DAILY_SHORTS_BLOCKED, 0)
    }

    fun incrementShortsBlocked() {
        checkAndResetDailyStats()
        val updated = getDailyShortsBlocked() + 1
        prefs.edit().putInt(KEY_DAILY_SHORTS_BLOCKED, updated).apply()
        _dailyShortsBlockedFlow.value = updated
    }

    companion object {
        private const val KEY_WEB_SHIELD = "key_web_shield"
        private const val KEY_APP_BLOCKER = "key_app_blocker"
        private const val KEY_MONK_MODE = "key_monk_mode"
        private const val KEY_SITUATIONAL_GUARDIAN = "key_situational_guardian"
        private const val KEY_INVISIBLE_INTERVENTION = "key_invisible_intervention"
        private const val KEY_HANDS_FREE_VOICE = "key_hands_free_voice"
        private const val KEY_BRAIN_SERVER_URL = "key_brain_server_url"
        private const val KEY_CUSTOM_BLOCKED_PACKAGES = "key_custom_blocked_packages"
        private const val KEY_BLOCKED_COUNT = "key_blocked_count"
        private const val KEY_COGNITIVE_POINTS = "key_cognitive_points"
        private const val KEY_STREAK = "key_streak"
        private const val KEY_FRONTIER_API_KEY = "key_frontier_api_key"
        private const val KEY_FRONTIER_PROVIDER = "key_frontier_provider"
        private const val KEY_FLOATING_OVERLAY = "key_floating_overlay"
        private const val KEY_USER_NAME = "key_user_name"
        private const val KEY_SLEEP_TIME = "key_sleep_time"
        private const val KEY_WAKE_TIME = "key_wake_time"
        private const val KEY_AMBITION = "key_ambition"
        private const val KEY_STATS_DAY_OF_YEAR = "key_stats_day_of_year"
        private const val KEY_DAILY_SCREEN_ON_MINUTES = "key_daily_screen_on_minutes"
        private const val KEY_DAILY_UNLOCKS_COUNT = "key_daily_unlocks_count"
        private const val KEY_DAILY_SHORTS_BLOCKED = "key_daily_shorts_blocked"
        private const val KEY_VOICE_SPEECH_RATE = "key_voice_speech_rate"
        private const val KEY_VOICE_PITCH = "key_voice_pitch"
        private const val KEY_ACOUSTIC_RADAR = "key_acoustic_radar"
        private const val KEY_LIGHT_SENSING = "key_light_sensing"
        private const val KEY_MOTION_TRACKING = "key_motion_tracking"

        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
