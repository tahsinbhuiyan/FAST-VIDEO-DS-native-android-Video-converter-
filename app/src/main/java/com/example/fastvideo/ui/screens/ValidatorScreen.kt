package com.example.fastvideo.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fastvideo.format.FvFormat
import com.example.fastvideo.format.FvValidationReport
import com.example.fastvideo.model.ConversionJob
import com.example.fastvideo.ui.MainUiState
import com.example.fastvideo.ui.MainViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValidatorScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onNavigateToEmulator: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isImporting by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<File?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            try {
                // Copy selected file to temp file to validate
                val tempFile = File(context.cacheDir, "inspect_${System.currentTimeMillis()}.fv")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                selectedFile = tempFile
                viewModel.inspectValidationFile(tempFile)
            } catch (e: Exception) {
                viewModel.showToast("Failed to read file: ${e.message}")
            } finally {
                isImporting = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Bitstream Validator",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.testTag("import_fv_to_validate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = "Open .fv File",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Header card explaining validator
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Strict FastVideoDS Specification Check",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Validates header magic 'FVDS', keyframe index table, packet offset bounds, 4-bit IMA-ADPCM audio chunk framing, and ARM9 hardware playback feasibility.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledTonalButton(
                                onClick = { filePicker.launch(arrayOf("*/*")) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("pick_fv_file_button")
                            ) {
                                Icon(imageVector = Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Inspect .fv File", style = MaterialTheme.typography.labelMedium)
                            }
                            if (uiState.history.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        uiState.history.firstOrNull()?.let {
                                            val f = File(it.outputFilePath)
                                            selectedFile = f
                                            viewModel.inspectValidationFile(f)
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("validate_latest_button")
                                ) {
                                    Text("Validate Latest Converted", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            // Validation results section
            item {
                val report = uiState.inspectionReport
                if (report == null) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentPasteSearch,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No File Inspected Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Tap 'Inspect .fv File' above or select 'Inspect Bitstream' from the History screen to inspect bitstream compliance.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    ValidationReportCard(
                        report = report,
                        file = selectedFile,
                        onPlayInEmulator = {
                            selectedFile?.let { f ->
                                viewModel.selectFileForPlayer(f.absolutePath)
                                onNavigateToEmulator()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ValidationReportCard(
    report: FvValidationReport,
    file: File?,
    onPlayInEmulator: () -> Unit
) {
    val statusColor = when {
        !report.isValid -> MaterialTheme.colorScheme.error
        report.warnings.isNotEmpty() -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, statusColor.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .testTag("validation_report_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Status banner
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                !report.isValid -> Icons.Default.Cancel
                                report.warnings.isNotEmpty() -> Icons.Default.Warning
                                else -> Icons.Default.CheckCircle
                            },
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            !report.isValid -> "INVALID FASTVIDEODS STREAM"
                            report.warnings.isNotEmpty() -> "VALID WITH NOTICES"
                            else -> "100% SPECIFICATION COMPLIANT"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Text(
                        text = file?.name ?: "FastVideoDS Bitstream",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(14.dp))

            // Console Hardware Compatibility Verdict
            Text(
                text = "Console Hardware Feasibility",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            val header = report.header
            if (header != null) {
                val isDsiOnly = header.fps > 30.0 || (header.width * header.height > 256 * 144 && header.fps > 25.0)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HardwareTargetPill(
                        console = "Nintendo DS / DS Lite",
                        clock = "67 MHz ARM9",
                        supported = !isDsiOnly,
                        note = if (isDsiOnly) "High bitrate / resolution" else "Full Speed (100%)",
                        modifier = Modifier.weight(1f)
                    )
                    HardwareTargetPill(
                        console = "Nintendo DSi / XL",
                        clock = "133 MHz ARM9",
                        supported = true,
                        note = "Optimal Performance",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metadata Grid
            if (header != null) {
                Text(
                    text = "Stream Header Details",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DetailRow("Container Signature", "0x53445646 ('FVDS')", true)
                        DetailRow("Resolution", "${header.width} x ${header.height} px (4x4 blocks)", true)
                        DetailRow("Video Frame Rate", "${String.format(Locale.US, "%.1f", header.fps)} FPS", true)
                        DetailRow("Total Video Frames", "${header.nrFrames} frames (~${String.format(Locale.US, "%.1f", header.durationSeconds)}s)", true)
                        DetailRow("Total Keyframes", "${report.keyframeCount} I-frames indexed", true)
                        DetailRow("Audio Format", "4-bit IMA-ADPCM (${header.audioChannels} ch @ ${header.audioRate} Hz)", true)
                        DetailRow("Average Bitrate", "${String.format(Locale.US, "%.1f", report.avgBitrateKbps)} kbps", true)
                        DetailRow("File Size on Disk", "${report.fileSize} bytes (${String.format(Locale.US, "%.2f", report.fileSize / (1024.0 * 1024.0))} MB)", true)
                    }
                }
            }

            // Errors List
            if (report.errors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Specification Errors (${report.errors.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(6.dp))
                for (err in report.errors) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Warnings List
            if (report.warnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Advisories & Notices (${report.warnings.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF59E0B)
                )
                Spacer(modifier = Modifier.height(6.dp))
                for (warn in report.warnings) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = warn,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action button: Test run in DS Emulator
            if (report.isValid && file != null) {
                Button(
                    onClick = onPlayInEmulator,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("play_validated_in_emulator_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.SportsEsports, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Run in DS Emulator", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun HardwareTargetPill(
    console: String,
    clock: String,
    supported: Boolean,
    note: String,
    modifier: Modifier = Modifier
) {
    val tint = if (supported) Color(0xFF10B981) else Color(0xFFF59E0B)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.border(1.dp, tint.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (supported) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = console,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = clock,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = tint
            )
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, isMono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default
        )
    }
}
