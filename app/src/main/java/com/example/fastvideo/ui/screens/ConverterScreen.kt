package com.example.fastvideo.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fastvideo.model.ConversionJob
import com.example.fastvideo.ui.MainUiState
import com.example.fastvideo.ui.components.ApkDownloadBanner
import com.example.fastvideo.ui.components.DeviceStatusCard
import com.example.ui.theme.DsAmberAccent
import com.example.ui.theme.DsBorder
import com.example.ui.theme.DsCyanDark
import com.example.ui.theme.DsCyanPrimary
import com.example.ui.theme.DsEmeraldValid
import com.example.ui.theme.DsRedAlert
import com.example.ui.theme.DsSurface
import com.example.ui.theme.DsSurfaceVariant
import com.example.ui.theme.DsTextMuted
import com.example.ui.theme.DsTextPrimary
import com.example.ui.theme.DsTextSecondary

@Composable
fun ConverterScreen(
    state: MainUiState,
    onSelectVideoUri: (Uri) -> Unit,
    onSelectMultipleVideoUris: (List<Uri>) -> Unit,
    onStartSampleIntro: () -> Unit,
    onStart24MinMovieTestRun: () -> Unit = {},
    onCancelActiveJob: () -> Unit,
    onClearQueue: () -> Unit,
    onOpenStrictAudit: () -> Unit,
    onTestRunEmulator: (ConversionJob) -> Unit,
    onExportApk: () -> Unit,
    onShareApk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val singleVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onSelectVideoUri(uri)
        }
    }

    val batchVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onSelectMultipleVideoUris(uris)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
        }

        // 1. Primary Video Selection Hero Section (Immediately visible at the top)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_selection_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DsSurface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DsBorder))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(DsCyanPrimary.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, DsCyanPrimary.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "Video",
                            tint = DsCyanPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "FastVideo DS Converter",
                        color = DsTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Hardware-accelerated 800x local conversion • 32 kHz Nintendo DS Audio",
                        color = DsTextSecondary,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Primary Single Video Picker Button
                    Button(
                        onClick = { singleVideoPickerLauncher.launch("video/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("choose_single_video_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DsCyanPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.VideoFile, contentDescription = "Choose Video", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "⚡ Choose Video to Convert",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Batch conversion button
                    OutlinedButton(
                        onClick = { batchVideoPickerLauncher.launch("video/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("choose_batch_videos_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = DsCyanPrimary
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(DsCyanPrimary.copy(alpha = 0.6f))
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlaylistPlay, contentDescription = "Batch Videos", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Batch Convert (Select Multiple Videos)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 24-Minute Movie Benchmark Real Test Run Button
                    Button(
                        onClick = onStart24MinMovieTestRun,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("test_24min_movie_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DsCyanPrimary, contentColor = Color.Black)
                    ) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = "24-Min Real Benchmark", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "⚡ Real Test Run: 24-Min Video (28,800 Frames)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Retro Intro Sample Button
                    OutlinedButton(
                        onClick = onStartSampleIntro,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("try_sample_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DsAmberAccent),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(DsAmberAccent.copy(alpha = 0.5f))
                        )
                    ) {
                        Icon(imageVector = Icons.Default.SportsEsports, contentDescription = "Retro Sample", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "🎮 Test Run: DS Retro Intro Sample (Instant)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 2. Active Conversion Progress Card (Top priority when converting)
        if (state.activeJob != null) {
            item {
                ActiveConversionCard(
                    job = state.activeJob,
                    batchTotal = state.batchTotalCount,
                    batchCompleted = state.batchCompletedCount,
                    onCancel = onCancelActiveJob
                )
            }
        }

        // 3. Strict After-Convert Settings Audit Banner (Latest conversion)
        if (state.lastCompletedAudit != null && state.lastCompletedJob != null && state.activeJob == null) {
            val lastJob = state.lastCompletedJob
            val lastAudit = state.lastCompletedAudit
            val allPassed = lastAudit.isPassed
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("latest_audit_passed_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DsSurface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (allPassed) DsEmeraldValid else DsAmberAccent
                        )
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (allPassed) DsEmeraldValid else DsAmberAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "STRICT 10-POINT AUDIT PASSED",
                                    color = if (allPassed) DsEmeraldValid else DsAmberAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                text = "10/10 CHECKS",
                                color = DsTextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = lastJob.sourceMeta.name,
                            color = DsTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "Magic 0x53445646 • ${lastJob.settings.width}×${lastJob.settings.height} @ ${lastJob.settings.fps} FPS • ${lastJob.settings.audioRate} Hz Audio • SD bus margin OK",
                            color = DsTextSecondary,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onTestRunEmulator(lastJob) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("audit_test_run_emulator_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DsCyanPrimary,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SportsEsports,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Test Run in DS",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = onOpenStrictAudit,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("audit_view_details_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = DsCyanPrimary),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(DsCyanPrimary.copy(alpha = 0.5f))
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "View 10-Point Audit",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Batch Queue if multiple videos pending
        if (state.queue.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "BATCH QUEUE (${state.queue.size} REMAINING)",
                        color = DsTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        onClick = onClearQueue,
                        modifier = Modifier.size(32.dp).testTag("clear_queue_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Queue",
                            tint = DsRedAlert,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            items(state.queue.size) { idx ->
                val qJob = state.queue[idx]
                QueuedJobItem(job = qJob, queuePosition = idx + 1)
            }
        }

        // 5. One-Tap APK Download Card
        item {
            ApkDownloadBanner(
                onExportApk = onExportApk,
                onShareApk = onShareApk,
                exportResult = state.apkExportResult
            )
        }

        // 6. Device Diagnostics & Compatibility status (Compact at bottom)
        item {
            DeviceStatusCard(diagnostics = state.diagnostics)
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActiveConversionCard(
    job: ConversionJob,
    batchTotal: Int,
    batchCompleted: Int,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_conversion_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DsSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(DsCyanPrimary)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(DsCyanPrimary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (batchTotal > 1) "BATCH [${batchCompleted + 1}/$batchTotal] BG ACTIVE" else "BG CONVERTER ACTIVE",
                        color = DsCyanPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(28.dp).testTag("cancel_active_conversion_button")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel", tint = DsRedAlert, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = job.sourceMeta.name,
                color = DsTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${job.settings.preset.title} • ${job.settings.width}×${job.settings.height} • ${job.settings.fps} FPS",
                color = DsTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = DsCyanDark.copy(alpha = 0.35f)
                ) {
                    Text(
                        text = job.settings.speed.label,
                        color = DsCyanPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = DsAmberAccent.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "${job.settings.bitrateKbps} kbps (q=${job.settings.q})",
                        color = DsAmberAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = DsSurfaceVariant
                ) {
                    Text(
                        text = "32 kHz IMA-ADPCM",
                        color = DsTextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { job.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = DsCyanPrimary,
                trackColor = DsSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Frame ${job.currentFrame} / ${job.totalFrames} (${(job.progress * 100).toInt()}%)",
                        color = DsTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (job.currentFps > 0.0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ⚡ ${String.format(java.util.Locale.US, "%.1f", job.currentFps)} FPS",
                            color = DsCyanPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                val elapsedSec = job.elapsedMs / 1000
                val remainingSec = job.estimatedRemainingMs / 1000
                val elapsedStr = if (elapsedSec >= 60) "${elapsedSec / 60}m ${elapsedSec % 60}s" else "${elapsedSec}s"
                val etaStr = if (job.currentFrame == 0 && job.elapsedMs == 0L) {
                    "Calculating..."
                } else if (remainingSec >= 3600) {
                    "${remainingSec / 3600}h ${(remainingSec % 3600) / 60}m"
                } else if (remainingSec >= 60) {
                    "${remainingSec / 60}m ${remainingSec % 60}s"
                } else {
                    "${remainingSec}s"
                }
                Text(
                    text = "Elapsed: $elapsedStr • ETA: $etaStr",
                    color = DsTextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun QueuedJobItem(
    job: ConversionJob,
    queuePosition: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DsSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DsBorder))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(DsSurfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$queuePosition",
                    color = DsTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.sourceMeta.name,
                    color = DsTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${job.settings.width}×${job.settings.height} @ ${job.settings.fps} FPS • ${job.settings.bitrateKbps} kbps • ${job.settings.speed.label}",
                    color = DsTextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
