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
import androidx.compose.material.icons.filled.Settings
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.OfflinePage
import com.example.shizuku.ShizukuHelper
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Browser", "Offline Vault", "Quick Answer", "Efficiency", "Settings")

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "browser"

    Scaffold(
        topBar = {
            if (currentRoute != "browser") {
                TopAppBar(
                    title = { Text("Offline Hub") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        },
        bottomBar = {
            if (currentRoute != "browser") {
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
                                    4 -> navController.navigate("power_settings") { popUpTo("power_settings") { inclusive = true } }
                                }
                            },
                            icon = {
                                Icon(
                                    when (index) {
                                        0 -> Icons.Default.Search
                                        1 -> Icons.Default.List
                                        2 -> Icons.Default.Info
                                        3 -> Icons.Default.Info
                                        else -> Icons.Default.Settings
                                    },
                                    contentDescription = title
                                )
                            },
                            label = { Text(title) }
                        )
                    }
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
                BrowserScreen(viewModel, onNavigate = { route -> 
                    val index = when (route) {
                        "offline_vault" -> 1
                        "quick_answer" -> 2
                        "system_info" -> 3
                        "power_settings" -> 4
                        else -> 0
                    }
                    selectedTab = index
                    navController.navigate(route) { popUpTo(route) { inclusive = true } } 
                })
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
            composable("power_settings") {
                PowerSettingsScreen()
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

    val blockedAds by viewModel.blockedAds.collectAsStateWithLifecycle()
    val blockedVideos by viewModel.blockedVideos.collectAsStateWithLifecycle()
    val blockedImages by viewModel.blockedImages.collectAsStateWithLifecycle()
    
    val dataSavedMb = String.format("%.2f", com.example.util.StatsManager.getDataSavedMb())
    val batterySaved = String.format("%.2f", com.example.util.StatsManager.getBatterySavedPercent())

    LazyColumn(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        item {
            Text("Efficiency Dashboard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Real-time battery & data savings compared to standard browsers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            
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
            
            Text("Items Blocked", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            DashboardCard("Ads & Trackers", blockedAds.toString())
            Spacer(modifier = Modifier.height(8.dp))
            DashboardCard("Heavy Videos", blockedVideos.toString())
            Spacer(modifier = Modifier.height(8.dp))
            DashboardCard("Heavy Images", blockedImages.toString())
    
            Spacer(modifier = Modifier.height(16.dp))
    
            Text("Hardware Monitors (Simulated)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            HardwarePerformanceDashboard()

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

@Composable
fun HardwarePerformanceDashboard() {
    var cpuUsage by remember { mutableFloatStateOf(0f) }
    var memoryUsage by remember { mutableFloatStateOf(0f) }
    var currentClockSpeed by remember { mutableFloatStateOf(2.4f) }
    var offlineCacheBytes by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val cacheService = com.example.network.OfflineCacheService(context)
        offlineCacheBytes = cacheService.getCacheSize()
        while (true) {
            cpuUsage = (10..85).random().toFloat()
            memoryUsage = (40..95).random().toFloat()
            currentClockSpeed = kotlin.random.Random.nextDouble(1.2, 3.6).toFloat()
            kotlinx.coroutines.delay(1500)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("CPU Usage", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${cpuUsage.toInt()}%",
                    fontWeight = FontWeight.Bold,
                    color = if (cpuUsage > 75f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            LinearProgressIndicator(
                progress = { cpuUsage / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Memory Usage", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${memoryUsage.toInt()}%",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            LinearProgressIndicator(
                progress = { memoryUsage / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Core Clock", style = MaterialTheme.typography.bodyMedium)
                Text(String.format("%.2f GHz", currentClockSpeed), fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            val cacheMb = offlineCacheBytes / (1024f * 1024f)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Web Cache Storage", style = MaterialTheme.typography.bodyMedium)
                Text(
                    String.format("%.2f MB", cacheMb),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            // Assuming a modest soft cap of 100MB for visual representation of the progress bar
            LinearProgressIndicator(
                progress = { (cacheMb / 100f).coerceIn(0f, 1f) },
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)
            )
            Text("Soft cap at 100 MB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
