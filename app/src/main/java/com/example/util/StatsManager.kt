package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object StatsManager {
    private const val PREFS_NAME = "offline_hub_stats"
    private const val KEY_BLOCKED_ADS = "blocked_ads"
    private const val KEY_BLOCKED_VIDEOS = "blocked_videos"
    private const val KEY_BLOCKED_IMAGES = "blocked_images"

    private lateinit var prefs: SharedPreferences

    private val _blockedAds = MutableStateFlow(0)
    val blockedAds: StateFlow<Int> = _blockedAds.asStateFlow()

    private val _blockedVideos = MutableStateFlow(0)
    val blockedVideos: StateFlow<Int> = _blockedVideos.asStateFlow()

    private val _blockedImages = MutableStateFlow(0)
    val blockedImages: StateFlow<Int> = _blockedImages.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _blockedAds.value = prefs.getInt(KEY_BLOCKED_ADS, 0)
        _blockedVideos.value = prefs.getInt(KEY_BLOCKED_VIDEOS, 0)
        _blockedImages.value = prefs.getInt(KEY_BLOCKED_IMAGES, 0)
    }

    fun incrementBlockedAds() {
        val current = _blockedAds.value + 1
        _blockedAds.value = current
        prefs.edit().putInt(KEY_BLOCKED_ADS, current).apply()
    }

    fun incrementBlockedVideos() {
        val current = _blockedVideos.value + 1
        _blockedVideos.value = current
        prefs.edit().putInt(KEY_BLOCKED_VIDEOS, current).apply()
    }

    fun incrementBlockedImages() {
        val current = _blockedImages.value + 1
        _blockedImages.value = current
        prefs.edit().putInt(KEY_BLOCKED_IMAGES, current).apply()
    }

    // Rough estimates
    // 1 Ad = 50KB = 0.05MB
    // 1 Video = 10MB
    // 1 Image = 0.5MB
    fun getDataSavedMb(): Float {
        return (_blockedAds.value * 0.05f) + (_blockedVideos.value * 10f) + (_blockedImages.value * 0.5f)
    }

    // 1 Ad = 0.01%
    // 1 Video = 0.5%
    // 1 Image = 0.02%
    fun getBatterySavedPercent(): Float {
        return (_blockedAds.value * 0.01f) + (_blockedVideos.value * 0.5f) + (_blockedImages.value * 0.02f)
    }
}
