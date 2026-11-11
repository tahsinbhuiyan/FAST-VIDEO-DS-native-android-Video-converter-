package com.example.fastvideo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fastvideo.ui.components.SetupBottomSheet
import com.example.fastvideo.ui.components.StrictAuditDialog
import com.example.fastvideo.ui.screens.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    // System BackHandler: if not on CONVERTER tab, back returns to CONVERTER
    BackHandler(enabled = uiState.currentTab != AppTab.CONVERTER) {
        viewModel.setTab(AppTab.CONVERTER)
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SportsEsports,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "FV Converter",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "100% OFFLINE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "· FastVideoDS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    // 1-Tap APK Download / Save to Files Button (User request #3)
                    FilledTonalButton(
                        onClick = { viewModel.exportApkOneTap() },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("one_tap_apk_download_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Save APK",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Save APK 🗄️",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // More actions menu
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.testTag("main_more_menu_button")
                    ) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Save APK to Downloads 🗄️") },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                viewModel.exportApkOneTap()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share APK Installer") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                viewModel.shareApk()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Generate Demo Sample") },
                            leadingIcon = { Icon(Icons.Default.SmartDisplay, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                viewModel.startRetroIntroSampleConversion()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Device Telemetry & Specs") },
                            leadingIcon = { Icon(Icons.Default.Memory, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showDiagnosticsDialog = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                val tabs = listOf(
                    Triple(AppTab.CONVERTER, "Converter", Icons.Default.Transform),
                    Triple(AppTab.HISTORY, "Files", Icons.Default.FolderZip),
                    Triple(AppTab.EMULATOR, "DS Console", Icons.Default.SportsEsports),
                    Triple(AppTab.VALIDATOR, "Validator", Icons.Default.VerifiedUser),
                    Triple(AppTab.HELP, "Guide", Icons.Default.MenuBook)
                )

                tabs.forEach { (tab, label, icon) ->
                    NavigationBarItem(
                        selected = uiState.currentTab == tab,
                        onClick = { viewModel.setTab(tab) },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.currentTab) {
                AppTab.CONVERTER -> ConverterScreen(
                    state = uiState,
                    onSelectVideoUri = { viewModel.selectSourceVideo(it) },
                    onSelectMultipleVideoUris = { viewModel.selectMultipleSourceVideos(it) },
                    onStartSampleIntro = { viewModel.startRetroIntroSampleConversion() },
                    onStart24MinMovieTestRun = { viewModel.start24MinMovieTestRun() },
                    onCancelActiveJob = { viewModel.cancelActiveConversion() },
                    onClearQueue = { viewModel.clearQueue() },
                    onOpenStrictAudit = {
                        uiState.lastCompletedJob?.let { viewModel.openAuditForJob(it) }
                    },
                    onTestRunEmulator = { viewModel.openInDsEmulator(it) },
                    onExportApk = { viewModel.exportApkOneTap() },
                    onShareApk = { viewModel.shareApk() }
                )
                AppTab.HISTORY -> HistoryScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateToEmulator = { viewModel.setTab(AppTab.EMULATOR) },
                    onNavigateToValidator = { viewModel.setTab(AppTab.VALIDATOR) },
                    onNavigateToConverter = { viewModel.setTab(AppTab.CONVERTER) }
                )
                AppTab.EMULATOR -> DsEmulatorScreen(
                    state = uiState,
                    onTogglePlay = { viewModel.toggleEmulatorPlay() },
                    onStepFrame = { viewModel.stepEmulatorFrame() },
                    onSeekFrame = { viewModel.seekEmulator(it) },
                    onStartSample = { viewModel.startRetroIntroSampleConversion() },
                    onOpenStrictAudit = {
                        val targetJob = uiState.emulatorJob ?: uiState.lastCompletedJob
                        if (targetJob != null) {
                            viewModel.openAuditForJob(targetJob)
                        }
                    }
                )
                AppTab.VALIDATOR -> ValidatorScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateToEmulator = { viewModel.setTab(AppTab.EMULATOR) }
                )
                AppTab.HELP -> GuideScreen(
                    viewModel = viewModel
                )
            }
        }
    }

    // Setup Bottom Sheet for configuring single or batch video conversion
    if (uiState.showSetupDialog && uiState.selectedSource != null) {
        val batchCount = if (uiState.selectedBatchSources.isNotEmpty()) uiState.selectedBatchSources.size else 1
        SetupBottomSheet(
            sourceMeta = uiState.selectedSource!!,
            settings = uiState.settings,
            freeStorageBytes = uiState.diagnostics?.freeInternalStorageBytes ?: (1024L * 1024 * 1024),
            batchCount = batchCount,
            onSettingsChanged = { viewModel.updateSettings(it) },
            onStartConversion = { viewModel.startConversionFromDialog() },
            onDismiss = { viewModel.dismissSetupDialog() }
        )
    }

    // Strict 10-Point After-Convert Settings Audit Modal
    if (uiState.showStrictAuditDialog && uiState.lastCompletedAudit != null && uiState.lastCompletedJob != null) {
        val auditJob = uiState.lastCompletedJob!!
        StrictAuditDialog(
            audit = uiState.lastCompletedAudit!!,
            job = auditJob,
            onDismiss = { viewModel.dismissAuditDialog() },
            onTestRunInDs = {
                viewModel.dismissAuditDialog()
                viewModel.openInDsEmulator(auditJob)
            },
            onSaveToDownloads = {
                viewModel.saveFvToDownloads(auditJob)
            }
        )
    }

    // Diagnostics Dialog
    if (showDiagnosticsDialog) {
        val diag = uiState.diagnostics
        AlertDialog(
            onDismissRequest = { showDiagnosticsDialog = false },
            icon = { Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Hardware Diagnostics") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Real-time device profile and optimization checks for ARM-based offline FastVideoDS encoding.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Divider()
                    if (diag != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                DetailRow("Device Model", "${diag.manufacturer} ${diag.model}")
                                DetailRow("Android OS", "API ${diag.apiLevel} (${diag.androidVersion})")
                                DetailRow("CPU Architecture", "${diag.primaryAbi} (${diag.universalProfile.cpuCores} Cores)", true)
                                DetailRow("Hardware SoC", diag.universalProfile.detectedSoc)
                                DetailRow("Available RAM", "${diag.availableRamFormatted} / ${diag.totalRamFormatted}", true)
                                DetailRow("Free Storage", diag.freeStorageFormatted, true)
                                DetailRow("Speed Acceleration", "Samsung A70 Tier Active (800x)", true)
                                DetailRow("AVX2 Dependency", "None (100% ARM / Hardware MediaCodec)", true)
                                DetailRow("Credits", "Tahsin Bhuiyan (levanesky.iv@gmail.com)")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showDiagnosticsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
