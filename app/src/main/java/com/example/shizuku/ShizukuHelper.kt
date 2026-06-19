package com.example.shizuku

import rikka.shizuku.Shizuku

object ShizukuHelper {
    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun hasShizukuPermission(): Boolean {
        return if (isShizukuRunning()) {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    }

    fun requestShizukuPermission(requestCode: Int) {
        if (isShizukuRunning() && !hasShizukuPermission()) {
            Shizuku.requestPermission(requestCode)
        }
    }
}
