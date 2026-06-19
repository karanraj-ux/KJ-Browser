package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun QuickAnswerScreen(viewModel: MainViewModel, onNavigateToWebCache: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var localAnswer by remember { mutableStateOf<String?>(null) }
    var showWebFallback by remember { mutableStateOf(false) }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        
        Text("Local AI Assistant", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Answers basic questions instantly with zero internet and near-zero battery usage.", 
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
                val answer = LocalAIEngine.query(query)
                if (answer != null) {
                    localAnswer = answer
                    showWebFallback = false
                } else {
                    localAnswer = "I don't know the answer locally. My compressed knowledge base is limited to save space."
                    showWebFallback = true
                }
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Ask Local AI")
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
                    Text("🤖 Local AI Response:", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Fallback: Eco Web Search (Minimal Data)")
            }
        }
    }
}

/**
 * Mock representation of the highly compressed local AI (like quantized Gemma).
 * In a real production environment, this would interface with MediaPipe LLM.
 */
object LocalAIEngine {
    fun query(q: String): String? {
        val lower = q.lowercase()
        return when {
            lower.contains("si unit of electricity") || lower.contains("current") -> "The SI unit of electric current is the Ampere (A)."
            lower.contains("capital of france") -> "The capital of France is Paris."
            lower.contains("speed of light") -> "The speed of light is approximately 299,792,458 meters per second in a vacuum."
            lower.contains("hello") -> "Hello! I am your on-device AI. I run purely on your phone's processor to save battery and data."
            lower.contains("planet") || lower.contains("mars") -> "Mars is the fourth planet from the Sun."
            else -> null
        }
    }
}
