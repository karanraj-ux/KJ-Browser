package com.example.network

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object LocalSlmEngine {
    private var llmInference: LlmInference? = null
    var isInitialized = false
        private set

    // A real SLM implementation requires a physical model file 
    // (.bin / .task) like Gemma 2B to be downloaded to internal storage.
    suspend fun initialize(context: Context, modelPath: String) = withContext(Dispatchers.IO) {
        if (!File(modelPath).exists()) {
            throw Exception("SLM model file not found at path: $modelPath")
        }
        
        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .build()
                
            llmInference = LlmInference.createFromOptions(context, options)
            isInitialized = true
        } catch (e: Exception) {
            isInitialized = false
            throw Exception("Failed to initialize SLM: ${e.message}")
        }
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.Default) {
        if (llmInference == null || !isInitialized) {
            return@withContext "Error: SLM is not initialized."
        }
        
        try {
            val response = llmInference?.generateResponse(prompt)
            response ?: "No response from SLM"
        } catch (e: Exception) {
            "SLM Error: ${e.message}"
        }
    }
}
