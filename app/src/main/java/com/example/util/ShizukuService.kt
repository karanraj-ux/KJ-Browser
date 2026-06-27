package com.example.util

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log

object ShizukuService {
    private const val TAG = "ShizukuService"

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()
    
    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        _isAvailable.value = true
        checkPermission()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _isAvailable.value = false
        _hasPermission.value = false
    }
    
    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 0) {
            _hasPermission.value = grantResult == PackageManager.PERMISSION_GRANTED
        }
    }

    fun init() {
        try {
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
            
            _isAvailable.value = Shizuku.pingBinder()
            if (_isAvailable.value) {
                checkPermission()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Shizuku", e)
        }
    }
    
    private fun checkPermission() {
        if (Shizuku.isPreV11()) {
            // Pre-v11 cannot be used safely with some newer APIs but we allow it for basic usage
            _hasPermission.value = false
            return
        }
        
        try {
            _hasPermission.value = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Shizuku permission", e)
        }
    }

    fun requestPermission() {
        if (!Shizuku.isPreV11() && _isAvailable.value && !_hasPermission.value) {
            Shizuku.requestPermission(0)
        }
    }

    fun destroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }
    
    fun executeCommand(command: String): Result<String> {
        if (!_isAvailable.value || !_hasPermission.value) {
            return Result.failure(IllegalStateException("Shizuku is not available or permission denied"))
        }
        
        return try {
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod = shizukuClass.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            Result.success(output)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command via Shizuku: $command", e)
            Result.failure(e)
        }
    }
}
