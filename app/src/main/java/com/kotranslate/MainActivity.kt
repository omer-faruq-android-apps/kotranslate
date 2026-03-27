package com.kotranslate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op, best effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1565C0),
                    secondary = Color(0xFF42A5F5),
                    surface = Color(0xFFFAFAFA),
                    background = Color(0xFFF5F5F5)
                )
            ) {
                MainScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh battery optimization state when returning from settings
        val vm = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[MainViewModel::class.java]
        vm.refreshBatteryOptimization()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val vm: MainViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as android.app.Application
        )
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("KOTranslate", fontWeight = FontWeight.Bold)
                        Text(
                            "Offline Google Translator for KOReader",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refreshModels(); vm.refreshIp() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Server Status Card
            item {
                Spacer(Modifier.height(4.dp))
                ServerStatusCard(
                    state = state,
                    onStart = { vm.startServer() },
                    onStop = { vm.stopServer() },
                    onCopyAddress = {
                        val address = "http://${state.ipAddress}:${state.port}"
                        clipboardManager.setText(AnnotatedString(address))
                    }
                )
            }

            // Battery optimization warning
            if (state.batteryOptimized) {
                item {
                    BatteryOptimizationCard(
                        onRequestExemption = {
                            val intent = Intent(
                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // Error message
            state.errorMessage?.let { error ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                error,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp
                            )
                            IconButton(onClick = { vm.clearError() }) {
                                Icon(
                                    Icons.Default.Close, "Dismiss",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Downloaded Languages
            val downloaded = state.models.filter { it.downloaded }
            if (downloaded.isNotEmpty()) {
                item {
                    SectionHeader("Downloaded Languages (${downloaded.size})")
                }
                items(downloaded, key = { "dl-${it.code}" }) { model ->
                    LanguageCard(
                        model = model,
                        isDownloading = model.code in state.downloadingLanguages,
                        onDelete = { vm.deleteModel(model.code) }
                    )
                }
            }

            // Available to Download
            val available = state.models.filter { !it.downloaded }
            if (available.isNotEmpty()) {
                item {
                    SectionHeader("Available to Download (${available.size})")
                }
                items(available, key = { "av-${it.code}" }) { model ->
                    LanguageCard(
                        model = model,
                        isDownloading = model.code in state.downloadingLanguages,
                        onDownload = { vm.downloadModel(model.code) }
                    )
                }
            }

            // Loading indicator
            if (state.isLoadingModels) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Attribution
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Powered by Google",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun ServerStatusCard(
    state: UiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCopyAddress: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Status indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            if (state.serverRunning) Color(0xFF4CAF50) else Color(0xFFBDBDBD),
                            RoundedCornerShape(6.dp)
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.serverRunning) "Server Running" else "Server Stopped",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }

            if (state.serverRunning) {
                Spacer(Modifier.height(12.dp))
                Text("Connect from KOReader:", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))

                // External device address
                Surface(
                    color = Color(0xFFE3F2FD),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "http://${state.ipAddress}:${state.port}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onCopyAddress, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.ContentCopy, "Copy",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Same device: http://localhost:${state.port}",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { if (state.serverRunning) onStop() else onStart() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.serverRunning)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    if (state.serverRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (state.serverRunning) "Stop Server" else "Start Server")
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        title,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
fun LanguageCard(
    model: ModelInfo,
    isDownloading: Boolean,
    onDownload: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            if (model.downloaded) {
                Icon(
                    Icons.Default.CheckCircle, "Downloaded",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    Icons.Default.Language, "Available",
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            // Language name and code
            Column(modifier = Modifier.weight(1f)) {
                Text(model.name, fontWeight = FontWeight.Medium)
                Text(
                    "${model.code} · ~${model.sizeMb} MB",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            // Action button
            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else if (model.downloaded && onDelete != null) {
                // Don't allow deleting English (required as pivot)
                if (model.code != "en") {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete, "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else if (!model.downloaded && onDownload != null) {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, "Download", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun BatteryOptimizationCard(onRequestExemption: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFE65100),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Battery Optimization Active",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFFE65100)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Android may stop the server when the screen is off. " +
                    "Disable battery optimization for reliable background operation.",
                fontSize = 13.sp,
                color = Color(0xFF795548)
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRequestExemption,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE65100)
                )
            ) {
                Icon(
                    Icons.Default.BatteryAlert,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Disable Battery Optimization")
            }
        }
    }
}
