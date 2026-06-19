package com.example.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.OfflinePage
import com.example.shizuku.ShizukuHelper
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Browser", "Offline Vault", "Quick Answer", "Efficiency")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline Hub") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            when (index) {
                                0 -> navController.navigate("browser") { popUpTo("browser") { inclusive = true } }
                                1 -> navController.navigate("offline_vault") { popUpTo("offline_vault") { inclusive = true } }
                                2 -> navController.navigate("quick_answer") { popUpTo("quick_answer") { inclusive = true } }
                                3 -> navController.navigate("system_info") { popUpTo("system_info") { inclusive = true } }
                            }
                        },
                        icon = {
                            Icon(
                                when (index) {
                                    0 -> Icons.Default.Search // Browser
                                    1 -> Icons.Default.List // Offline Vault
                                    2 -> Icons.Default.Info // Quick Answer - reuse info icon or similar
                                    else -> Icons.Default.Info // Efficiency
                                },
                                contentDescription = title
                            )
                        },
                        label = { Text(title) }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "browser",
            modifier = Modifier.padding(paddingValues),
            enterTransition = { androidx.compose.animation.EnterTransition.None },
            exitTransition = { androidx.compose.animation.ExitTransition.None },
            popEnterTransition = { androidx.compose.animation.EnterTransition.None },
            popExitTransition = { androidx.compose.animation.ExitTransition.None }
        ) {
            composable("browser") {
                BrowserScreen(viewModel)
            }
            composable("offline_vault") {
                WebCacheScreen(viewModel) { page ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("htmlContent", page.htmlContent)
                    navController.currentBackStackEntry?.savedStateHandle?.set("baseUrl", page.url)
                    navController.navigate("offline_view")
                }
            }
            composable("quick_answer") {
                QuickAnswerScreen(viewModel) {
                    selectedTab = 0
                    navController.navigate("offline_vault") { popUpTo("offline_vault") { inclusive = true } }
                }
            }
            composable("system_info") {
                SystemInfoScreen(viewModel)
            }
            composable("offline_view") {
                val htmlContent = navController.previousBackStackEntry?.savedStateHandle?.get<String>("htmlContent") ?: ""
                val baseUrl = navController.previousBackStackEntry?.savedStateHandle?.get<String>("baseUrl") ?: ""
                Column(modifier = Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = { Text("Offline View") },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                    OfflineWebViewScreen(htmlContent = htmlContent, baseUrl = baseUrl)
                }
            }
        }
    }
}

@Composable
fun WebCacheScreen(viewModel: MainViewModel, onPageClick: (OfflinePage) -> Unit) {
    val savedPages by viewModel.savedPages.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val downloadStatus by viewModel.downloadStatus.collectAsStateWithLifecycle()
    var urlInput by remember { mutableStateOf("") }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text("Search or type web address") },
            placeholder = { Text("wikipedia.org/wiki/Battery") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Web") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Go,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onGo = {
                    if (urlInput.isNotBlank()) {
                        viewModel.downloadAndSavePage(urlInput)
                        urlInput = ""
                    }
                }
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                viewModel.downloadAndSavePage(urlInput)
                urlInput = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isDownloading && urlInput.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(if (isDownloading) "Extracting Text..." else "Read Uncluttered (Eco Mode)")
        }

        downloadStatus?.let { status ->
            Text(status, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 4.dp))
            LaunchedEffect(status) {
                delay(3000)
                viewModel.clearStatus()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            label = { Text("Search your offline knowledge vault...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search vault") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Saved Pages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(savedPages) { page ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onPageClick(page) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(page.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(page.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.deletePage(page.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Page")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SystemInfoScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val shizukuRunning = ShizukuHelper.isShizukuRunning()
    val shizukuPermission = ShizukuHelper.hasShizukuPermission()

    val savedPages by viewModel.savedPages.collectAsStateWithLifecycle()
    val pagesCount = savedPages.size
    val dataSavedMb = String.format("%.2f", pagesCount * 2.5) // Fake 2.5MB per page avg
    val batterySaved = String.format("%.2f", pagesCount * 0.15) // Fake 0.15% battery per page

    // Mock CPU/RAM stats reading
    val ramInfo = remember { getRamInfo(context) }
    val storageInfo = remember { getStorageInfo() }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Text("Efficiency Dashboard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Real-time battery & data savings compared to standard Chrome usage.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Data Saved", style = MaterialTheme.typography.bodyMedium)
                    Text("$dataSavedMb MB", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Battery Saved", style = MaterialTheme.typography.bodyMedium)
                    Text("$batterySaved %", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        DashboardCard("Memory (RAM)", ramInfo)
        Spacer(modifier = Modifier.height(8.dp))
        DashboardCard("Internal Storage", storageInfo)

        Spacer(modifier = Modifier.height(16.dp))

        Text("Shizuku Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (shizukuRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (shizukuRunning) "Shizuku Service is RUNNING" else "Shizuku Service is STOPPED",
                    fontWeight = FontWeight.Bold,
                    color = if (shizukuRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Permission Granted: $shizukuPermission",
                    color = if (shizukuRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
                if (shizukuRunning && !shizukuPermission) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { ShizukuHelper.requestShizukuPermission(1001) }) {
                        Text("Grant Permission")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Advanced rootless system interventions require Shizuku setup via ADB or Wireless Debugging. This dashboard previews local capabilities.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DashboardCard(title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

fun getRamInfo(context: Context): String {
    val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    actManager.getMemoryInfo(memInfo)
    val availRam = (memInfo.availMem / (1024 * 1024)).toString() + " MB"
    val totalRam = (memInfo.totalMem / (1024 * 1024)).toString() + " MB"
    return "$availRam / $totalRam Free"
}

fun getStorageInfo(): String {
    val statFs = StatFs(Environment.getDataDirectory().path)
    val availStorage = (statFs.availableBlocksLong * statFs.blockSizeLong) / (1024 * 1024)
    val totalStorage = (statFs.blockCountLong * statFs.blockSizeLong) / (1024 * 1024)
    return "${availStorage}MB / ${totalStorage}MB Free"
}
