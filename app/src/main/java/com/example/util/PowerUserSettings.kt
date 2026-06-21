package com.example.util

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PowerUserSettings {
    private const val PREFS_NAME = "offline_hub_power_user"
    private const val SECURE_PREFS_NAME = "offline_hub_power_user_secure"
    private const val KEY_CUSTOM_API_KEY = "custom_gemini_api_key"
    private const val KEY_AI_PROVIDER = "ai_provider"
    private const val KEY_DETECTED_MODEL = "detected_model"
    private const val KEY_LOCAL_LLM_URL = "local_llm_url"
    private const val KEY_CUSTOM_DNS = "custom_dns_resolver"
    private const val KEY_CUSTOM_AD_BLOCK_LIST = "custom_ad_block_list"
    private const val KEY_AD_BLOCK_SUBSCRIPTIONS = "ad_block_subscriptions"
    private const val KEY_DISABLED_JS_DOMAINS = "disabled_js_domains"

    private const val KEY_SLM_MODEL_PATH = "slm_model_path"

    private const val KEY_USER_SCRIPTS = "user_scripts"
    private const val KEY_USER_STYLES = "user_styles"
    private const val KEY_URL_REDIRECTS = "url_redirects"

    private lateinit var prefs: SharedPreferences
    private lateinit var securePrefs: SharedPreferences

    private val _customApiKey = MutableStateFlow("")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    private val _aiProvider = MutableStateFlow("gemini")
    val aiProvider: StateFlow<String> = _aiProvider.asStateFlow()

    private val _detectedModel = MutableStateFlow("")
    val detectedModel: StateFlow<String> = _detectedModel.asStateFlow()

    private val _localLlmUrl = MutableStateFlow("http://localhost:11434/v1/chat/completions")
    val localLlmUrl: StateFlow<String> = _localLlmUrl.asStateFlow()

    private val _slmModelPath = MutableStateFlow("")
    val slmModelPath: StateFlow<String> = _slmModelPath.asStateFlow()

    private val _customDns = MutableStateFlow("")
    val customDns: StateFlow<String> = _customDns.asStateFlow()

    private val _customAdBlockList = MutableStateFlow("")
    val customAdBlockList: StateFlow<String> = _customAdBlockList.asStateFlow()

    private val _adBlockSubscriptions = MutableStateFlow<Set<String>>(emptySet())
    val adBlockSubscriptions: StateFlow<Set<String>> = _adBlockSubscriptions.asStateFlow()

    private val _disabledJsDomains = MutableStateFlow<Set<String>>(emptySet())
    val disabledJsDomains: StateFlow<Set<String>> = _disabledJsDomains.asStateFlow()

    private val _userScripts = MutableStateFlow<Map<String, String>>(emptyMap())
    val userScripts: StateFlow<Map<String, String>> = _userScripts.asStateFlow()

    private val _userStyles = MutableStateFlow<Map<String, String>>(emptyMap())
    val userStyles: StateFlow<Map<String, String>> = _userStyles.asStateFlow()

    private val _urlRedirects = MutableStateFlow<Map<String, String>>(emptyMap())
    val urlRedirects: StateFlow<Map<String, String>> = _urlRedirects.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            securePrefs = EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback if encrypted prefs fail (sometimes happens on older or broken keystores)
            securePrefs = prefs
        }

        // Migrate Old key to secure if it exists
        if (prefs.contains(KEY_CUSTOM_API_KEY)) {
            val oldKey = prefs.getString(KEY_CUSTOM_API_KEY, "") ?: ""
            if (oldKey.isNotBlank()) {
                securePrefs.edit().putString(KEY_CUSTOM_API_KEY, oldKey).apply()
            }
            prefs.edit().remove(KEY_CUSTOM_API_KEY).apply()
        }

        _customApiKey.value = securePrefs.getString(KEY_CUSTOM_API_KEY, "") ?: ""
        _aiProvider.value = prefs.getString(KEY_AI_PROVIDER, "gemini") ?: "gemini"
        _detectedModel.value = prefs.getString(KEY_DETECTED_MODEL, "") ?: ""
        _localLlmUrl.value = prefs.getString(KEY_LOCAL_LLM_URL, "http://localhost:11434/v1/chat/completions") ?: "http://localhost:11434/v1/chat/completions"
        _slmModelPath.value = prefs.getString(KEY_SLM_MODEL_PATH, "") ?: ""
        _customDns.value = prefs.getString(KEY_CUSTOM_DNS, "") ?: ""
        _customAdBlockList.value = prefs.getString(KEY_CUSTOM_AD_BLOCK_LIST, "") ?: ""
        _adBlockSubscriptions.value = prefs.getStringSet(KEY_AD_BLOCK_SUBSCRIPTIONS, emptySet()) ?: emptySet()
        _disabledJsDomains.value = prefs.getStringSet(KEY_DISABLED_JS_DOMAINS, emptySet()) ?: emptySet()
        
        _userScripts.value = parseStringMap(prefs.getString(KEY_USER_SCRIPTS, "{}") ?: "{}")
        _userStyles.value = parseStringMap(prefs.getString(KEY_USER_STYLES, "{}") ?: "{}")
        _urlRedirects.value = parseStringMap(prefs.getString(KEY_URL_REDIRECTS, "{}") ?: "{}")
    }

    private fun parseStringMap(jsonString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val jsonObject = org.json.JSONObject(jsonString)
            jsonObject.keys().forEach { key ->
                map[key] = jsonObject.getString(key)
            }
        } catch (e: Exception) {
            // ignore
        }
        return map
    }
    
    private fun mapToString(map: Map<String, String>): String {
        return org.json.JSONObject(map).toString()
    }

    fun setCustomApiKey(key: String) {
        _customApiKey.value = key
        securePrefs.edit().putString(KEY_CUSTOM_API_KEY, key).apply()
    }

    fun setAiProvider(provider: String) {
        _aiProvider.value = provider
        prefs.edit().putString(KEY_AI_PROVIDER, provider).apply()
    }

    fun setDetectedModel(model: String) {
        _detectedModel.value = model
        prefs.edit().putString(KEY_DETECTED_MODEL, model).apply()
    }

    fun setLocalLlmUrl(url: String) {
        _localLlmUrl.value = url
        prefs.edit().putString(KEY_LOCAL_LLM_URL, url).apply()
    }

    fun setSlmModelPath(path: String) {
        _slmModelPath.value = path
        prefs.edit().putString(KEY_SLM_MODEL_PATH, path).apply()
    }

    fun setCustomDns(dns: String) {
        _customDns.value = dns
        prefs.edit().putString(KEY_CUSTOM_DNS, dns).apply()
    }

    fun setCustomAdBlockList(list: String) {
        _customAdBlockList.value = list
        prefs.edit().putString(KEY_CUSTOM_AD_BLOCK_LIST, list).apply()
    }

    fun addAdBlockSubscription(url: String) {
        val currentSet = _adBlockSubscriptions.value.toMutableSet()
        currentSet.add(url)
        _adBlockSubscriptions.value = currentSet
        prefs.edit().putStringSet(KEY_AD_BLOCK_SUBSCRIPTIONS, currentSet).apply()
    }

    fun removeAdBlockSubscription(url: String) {
        val currentSet = _adBlockSubscriptions.value.toMutableSet()
        currentSet.remove(url)
        _adBlockSubscriptions.value = currentSet
        prefs.edit().putStringSet(KEY_AD_BLOCK_SUBSCRIPTIONS, currentSet).apply()
    }

    fun toggleJsForDomain(url: String) {
        val domain = extractDomain(url) ?: return
        val currentSet = _disabledJsDomains.value.toMutableSet()
        if (currentSet.contains(domain)) {
            currentSet.remove(domain)
        } else {
            currentSet.add(domain)
        }
        _disabledJsDomains.value = currentSet
        prefs.edit().putStringSet(KEY_DISABLED_JS_DOMAINS, currentSet).apply()
    }

    fun isJsDisabledForUrl(url: String): Boolean {
        val domain = extractDomain(url) ?: return false
        return _disabledJsDomains.value.contains(domain)
    }

    private fun extractDomain(url: String): String? {
        return try {
            val host = Uri.parse(url).host?.lowercase()
            host?.removePrefix("www.")
        } catch (e: Exception) {
            null
        }
    }

    fun addUserScript(domain: String, script: String) {
        val current = _userScripts.value.toMutableMap()
        current[domain] = script
        _userScripts.value = current
        prefs.edit().putString(KEY_USER_SCRIPTS, mapToString(current)).apply()
    }

    fun removeUserScript(domain: String) {
        val current = _userScripts.value.toMutableMap()
        current.remove(domain)
        _userScripts.value = current
        prefs.edit().putString(KEY_USER_SCRIPTS, mapToString(current)).apply()
    }

    fun addUserStyle(domain: String, style: String) {
        val current = _userStyles.value.toMutableMap()
        current[domain] = style
        _userStyles.value = current
        prefs.edit().putString(KEY_USER_STYLES, mapToString(current)).apply()
    }

    fun removeUserStyle(domain: String) {
        val current = _userStyles.value.toMutableMap()
        current.remove(domain)
        _userStyles.value = current
        prefs.edit().putString(KEY_USER_STYLES, mapToString(current)).apply()
    }

    fun addUrlRedirect(matchDomain: String, replacementDomain: String) {
        val current = _urlRedirects.value.toMutableMap()
        current[matchDomain] = replacementDomain
        _urlRedirects.value = current
        prefs.edit().putString(KEY_URL_REDIRECTS, mapToString(current)).apply()
    }

    fun removeUrlRedirect(matchDomain: String) {
        val current = _urlRedirects.value.toMutableMap()
        current.remove(matchDomain)
        _urlRedirects.value = current
        prefs.edit().putString(KEY_URL_REDIRECTS, mapToString(current)).apply()
    }
}
