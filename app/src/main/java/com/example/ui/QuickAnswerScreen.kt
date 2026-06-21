package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

@Composable
fun QuickAnswerScreen(viewModel: MainViewModel, onNavigateToWebCache: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var localAnswer by remember { mutableStateOf<String?>(null) }
    var showWebFallback by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        
        Text("Eco AI Assistant", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Answers basic questions by securely processing them in the cloud, saving your device battery.", 
            style = MaterialTheme.typography.bodySmall, 
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { 
                query = it 
                localAnswer = null 
                showWebFallback = false 
            },
            label = { Text("Ask a question (e.g., SI unit of electricity)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (query.isNotBlank()) {
                    coroutineScope.launch {
                        isLoading = true
                        localAnswer = null
                        showWebFallback = false
                        val answer = EcoAIEngine.query(query)
                        if (answer != null) {
                            localAnswer = answer
                        } else {
                            localAnswer = "I encountered an error querying the cloud AI. Please check your internet or API key."
                            showWebFallback = true
                        }
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.align(Alignment.End),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Ask Eco AI")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        localAnswer?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🤖 Eco AI Response:", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (showWebFallback) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val searchUrl = "https://en.wikipedia.org/wiki/Special:Search?search=${query.replace(" ", "+")}"
                    viewModel.downloadAndSavePage(searchUrl)
                    onNavigateToWebCache()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text("Fallback: Eco Web Search", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

/**
 * Real Gemini API interface implemented using lightweight OkHttp + JSONObject
 * Keeps the app small and performs inference on the cloud to save device battery.
 */
object EcoAIEngine {
    private val client = OkHttpClient()

    suspend fun query(q: String): String? = withContext(Dispatchers.IO) {
        val apiKey = com.example.BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Error: Please add your Gemini API Key in the AI Studio Settings menu to use the AI Assistant."
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=\$apiKey"
        
        val requestBodyJson = JSONObject()
        val contentsArray = JSONArray()
        val contentObject = JSONObject()
        val partsArray = JSONArray()
        val partObject = JSONObject()
        
        partObject.put("text", q)
        partsArray.put(partObject)
        contentObject.put("parts", partsArray)
        contentsArray.put(contentObject)
        requestBodyJson.put("contents", contentsArray)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestBodyJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "API Call Failed: \${response.code} \${response.message}"
                }
                val responseData = response.body?.string() ?: return@withContext null
                val root = JSONObject(responseData)
                val candidates = root.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text")
                    }
                }
                return@withContext "No response text found."
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}
