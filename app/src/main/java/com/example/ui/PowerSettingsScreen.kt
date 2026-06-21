package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.util.PowerUserSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerSettingsScreen() {
    val customApiKey by PowerUserSettings.customApiKey.collectAsStateWithLifecycle()
    val customDns by PowerUserSettings.customDns.collectAsStateWithLifecycle()
    val customAdBlockList by PowerUserSettings.customAdBlockList.collectAsStateWithLifecycle()
    
    val aiProvider by PowerUserSettings.aiProvider.collectAsStateWithLifecycle()
    val localLlmUrl by PowerUserSettings.localLlmUrl.collectAsStateWithLifecycle()
    
    var apiKeyInput by remember { mutableStateOf(customApiKey) }
    var providerInput by remember { mutableStateOf(aiProvider) }
    var localLlmUrlInput by remember { mutableStateOf(localLlmUrl) }
    var dnsInput by remember { mutableStateOf(customDns) }
    var adBlockInput by remember { mutableStateOf(customAdBlockList) }
    var showProviderDropdown by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text("Power User Extensibility", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Bring Your Own API (BYOK) & Custom Controls", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("AI Provider (BYOK)", fontWeight = FontWeight.Bold)
                    Text("Choose your inference engine", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = showProviderDropdown,
                        onExpandedChange = { showProviderDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = when (providerInput) {
                                "gemini" -> "Google Gemini (Default)"
                                "openai" -> "OpenAI"
                                "on_device" -> "On-Device SLM (Gemma)"
                                "local" -> "Local LLM / Custom Endpoint"
                                else -> providerInput
                            },
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showProviderDropdown) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = showProviderDropdown,
                            onDismissRequest = { showProviderDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Google Gemini") },
                                onClick = { providerInput = "gemini"; showProviderDropdown = false }
                            )
                            DropdownMenuItem(
                                text = { Text("OpenAI") },
                                onClick = { providerInput = "openai"; showProviderDropdown = false }
                            )
                            DropdownMenuItem(
                                text = { Text("On-Device SLM (Gemma)") },
                                onClick = { providerInput = "on_device"; showProviderDropdown = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Local LLM / Ollama") },
                                onClick = { providerInput = "local"; showProviderDropdown = false }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(if (providerInput == "local" || providerInput == "on_device") "API Key (Optional)" else "sk-...") },
                        label = { Text("API Key") },
                        singleLine = true
                    )

                    if (providerInput == "local") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = localLlmUrlInput,
                            onValueChange = { localLlmUrlInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("http://localhost:11434/v1/chat/completions") },
                            label = { Text("Endpoint URL") },
                            singleLine = true
                        )
                    }

                    var slmModelPathInput by remember { mutableStateOf(PowerUserSettings.slmModelPath.value) }

                    if (providerInput == "on_device") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = slmModelPathInput,
                            onValueChange = { slmModelPathInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("/data/user/0/com.example/files/gemma.bin") },
                            label = { Text("SLM Model File Path") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Download a MediaPipe compatible .bin LLM model and enter its absolute path here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val detectedModel by PowerUserSettings.detectedModel.collectAsStateWithLifecycle()
                    val coroutineScope = rememberCoroutineScope()
                    val context = androidx.compose.ui.platform.LocalContext.current
                    var detectionStatus by remember { mutableStateOf("") }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { 
                            PowerUserSettings.setAiProvider(providerInput)
                            PowerUserSettings.setCustomApiKey(apiKeyInput)
                            PowerUserSettings.setLocalLlmUrl(localLlmUrlInput)
                            PowerUserSettings.setSlmModelPath(slmModelPathInput)
                        }) {
                            Text("Save AI Settings")
                        }
                        OutlinedButton(onClick = {
                            PowerUserSettings.setAiProvider(providerInput)
                            PowerUserSettings.setCustomApiKey(apiKeyInput)
                            PowerUserSettings.setSlmModelPath(slmModelPathInput)
                            coroutineScope.launch {
                                detectionStatus = "Detecting..."
                                if (providerInput == "on_device" && slmModelPathInput.isNotBlank()) {
                                    try {
                                        com.example.network.LocalSlmEngine.initialize(context, slmModelPathInput)
                                    } catch (e: Exception) {
                                        // Ignore init errors since we just want to run detection below to show error or success
                                    }
                                }
                                detectionStatus = com.example.network.EcoAIEngine.detectAvailableModels()
                            }
                        }) {
                            Text("Detect Models")
                        }
                    }
                    if (detectionStatus.isNotBlank()) {
                         Text(detectionStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (detectedModel.isNotBlank()) {
                         Text("Active Auto-Model: $detectedModel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Custom DNS Resolver (Preview)", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = dnsInput,
                        onValueChange = { dnsInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. https://dns.nextdns.io/...") },
                        label = { Text("DNS-over-HTTPS (DoH) URL") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { PowerUserSettings.setCustomDns(dnsInput) }) {
                        Text("Save DNS")
                    }
                }
            }
        }

        item {
            val subscriptions by PowerUserSettings.adBlockSubscriptions.collectAsStateWithLifecycle()
            var newSubUrl by remember { mutableStateOf("") }
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Ad-Block Subscriptions (Hosts/OISD)", fontWeight = FontWeight.Bold)
                    Text("Subscribe to remote filter lists", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (subscriptions.isEmpty()) {
                        Text("No subscriptions yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        subscriptions.forEach { url ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(url, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                IconButton(onClick = { PowerUserSettings.removeAdBlockSubscription(url); com.example.util.AdBlocker.refreshSubscriptions() }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                                }
                            }
                        }
                    }
                    
                    OutlinedTextField(
                        value = newSubUrl,
                        onValueChange = { newSubUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts") },
                        label = { Text("Filter List URL") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { 
                        if (newSubUrl.isNotBlank()) {
                            PowerUserSettings.addAdBlockSubscription(newSubUrl)
                            com.example.util.AdBlocker.refreshSubscriptions()
                            newSubUrl = ""
                        }
                    }) {
                        Text("Add Subscription")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("User Scripts / Styles & Redirects", fontWeight = FontWeight.Bold)
                    Text("Manage injected code and url forwarding rules.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    var domainInput by remember { mutableStateOf("") }
                    var scriptInput by remember { mutableStateOf("") }
                    var styleInput by remember { mutableStateOf("") }
                    var redirectInput by remember { mutableStateOf("") }

                    OutlinedTextField(
                        value = domainInput,
                        onValueChange = { domainInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Domain (e.g. reddit.com)") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = scriptInput,
                        onValueChange = { scriptInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Inject JS") },
                        placeholder = { Text("console.log('hi');") }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = styleInput,
                        onValueChange = { styleInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Inject CSS") },
                        placeholder = { Text("body { background: black !important; }") }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = redirectInput,
                        onValueChange = { redirectInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Redirect to (e.g. teddit.net)") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { 
                        if (domainInput.isNotBlank()) {
                            if (scriptInput.isNotBlank()) PowerUserSettings.addUserScript(domainInput, scriptInput)
                            if (styleInput.isNotBlank()) PowerUserSettings.addUserStyle(domainInput, styleInput)
                            if (redirectInput.isNotBlank()) PowerUserSettings.addUrlRedirect(domainInput, redirectInput)
                            domainInput = ""
                            scriptInput = ""
                            styleInput = ""
                            redirectInput = ""
                        }
                    }) {
                        Text("Save Rule")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Custom Ad-Block Domains", fontWeight = FontWeight.Bold)
                    Text("Comma-separated list of domains to block", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = adBlockInput,
                        onValueChange = { adBlockInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("example.com, trackers.net") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { PowerUserSettings.setCustomAdBlockList(adBlockInput) }) {
                        Text("Save Blocklist")
                    }
                }
            }
        }
    }
}
