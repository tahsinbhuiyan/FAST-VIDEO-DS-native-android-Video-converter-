package com.example.fastvideo.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fastvideo.model.ConversionPreset
import com.example.fastvideo.model.ConversionSettings
import com.example.fastvideo.model.SourceVideoMeta
import com.example.ui.theme.DsAmberAccent
import com.example.ui.theme.DsBorder
import com.example.ui.theme.DsCyanDark
import com.example.ui.theme.DsCyanPrimary
import com.example.ui.theme.DsEmeraldValid
import com.example.ui.theme.DsRedAlert
import com.example.ui.theme.DsSlateBg
import com.example.ui.theme.DsSurface
import com.example.ui.theme.DsSurfaceVariant
import com.example.ui.theme.DsTextMuted
import com.example.ui.theme.DsTextPrimary
import com.example.ui.theme.DsTextSecondary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetupBottomSheet(
    sourceMeta: SourceVideoMeta,
    settings: ConversionSettings,
    freeStorageBytes: Long,
    batchCount: Int = 1,
    onSettingsChanged: (ConversionSettings) -> Unit,
    onStartConversion: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estimatedBytes = settings.estimateOutputSizeBytes(sourceMeta.durationMs) * (if (batchCount > 1) batchCount else 1)
    val estimatedMb = estimatedBytes.toDouble() / (1024 * 1024)
    val freeMb = freeStorageBytes.toDouble() / (1024 * 1024)
    val hasStorageIssue = freeStorageBytes in 1 until (estimatedBytes * 2)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DsSlateBg,
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (batchCount > 1) "BATCH CONVERSION SETTINGS" else "CONVERSION SETTINGS",
                        color = DsCyanPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (batchCount > 1) "$batchCount Videos Selected" else sourceMeta.name,
                        color = DsTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = DsTextSecondary)
                }
            }

            if (batchCount > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DsCyanPrimary.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = DsCyanPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Batch Mode: All $batchCount videos will convert in sequence in the background.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DsTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Source Metadata Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DsSurface),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DsBorder))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "DURATION", color = DsTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(text = sourceMeta.durationFormatted, color = DsTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "SOURCE RES", color = DsTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(text = "${sourceMeta.width}×${sourceMeta.height}", color = DsTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "AUDIO", color = DsTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (sourceMeta.hasAudio) "${sourceMeta.audioSampleRate}Hz" else "None",
                            color = DsTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Preset Selector
            Text(
                text = "CONSOLE TARGET PRESET",
                color = DsTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetOptionCard(
                    title = "DSi Widescreen",
                    dimensions = "256×144 (16:9)",
                    desc = "Letterboxed on 4:3 DS",
                    isSelected = settings.preset == ConversionPreset.DSI_WIDESCREEN,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSettingsChanged(
                            settings.copy(
                                preset = ConversionPreset.DSI_WIDESCREEN,
                                height = 144
                            )
                        )
                    }
                )

                PresetOptionCard(
                    title = "DS Standard",
                    dimensions = "256×192 (4:3)",
                    desc = "Fills full top screen",
                    isSelected = settings.preset == ConversionPreset.DS_STANDARD,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSettingsChanged(
                            settings.copy(
                                preset = ConversionPreset.DS_STANDARD,
                                height = 192
                            )
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Framerate Selector
            Text(
                text = "FRAME RATE (NINTENDO DS PLAYBACK)",
                color = DsTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(12, 15, 20, 24, 30).forEach { fpsValue ->
                    FilterChip(
                        selected = settings.fps == fpsValue,
                        onClick = { onSettingsChanged(settings.copy(fps = fpsValue)) },
                        label = { Text(text = "$fpsValue FPS", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DsCyanPrimary,
                            selectedLabelColor = Color.Black,
                            containerColor = DsSurface,
                            labelColor = DsTextPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = settings.fps == fpsValue,
                            borderColor = DsBorder,
                            selectedBorderColor = DsCyanPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Target Bitrate & Quantizer (q)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TARGET BITRATE & SD BANDWIDTH",
                    color = DsTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "${settings.bitrateKbps} kbps (q=${settings.q})",
                    color = DsAmberAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Controls video payload density to ensure 100% smooth playback without SD card buffer stutter.",
                color = DsTextMuted,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QualityChip(
                    label = "Safe SD",
                    sub = "700k (q=40)",
                    isSelected = settings.bitrateKbps <= 850,
                    modifier = Modifier.weight(1f),
                    onClick = { onSettingsChanged(settings.withBitrate(700)) }
                )
                QualityChip(
                    label = "Compact",
                    sub = "1000k (q=34)",
                    isSelected = settings.bitrateKbps in 851..1200,
                    modifier = Modifier.weight(1f),
                    onClick = { onSettingsChanged(settings.withBitrate(1000)) }
                )
                QualityChip(
                    label = "Balanced ⭐",
                    sub = "1500k (q=30)",
                    isSelected = settings.bitrateKbps in 1201..1900,
                    modifier = Modifier.weight(1f),
                    onClick = { onSettingsChanged(settings.withBitrate(1500)) }
                )
                QualityChip(
                    label = "High",
                    sub = "2500k (q=22)",
                    isSelected = settings.bitrateKbps >= 1901,
                    modifier = Modifier.weight(1f),
                    onClick = { onSettingsChanged(settings.withBitrate(2500)) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = settings.bitrateKbps.toFloat(),
                onValueChange = { onSettingsChanged(settings.withBitrate(it.toInt())) },
                valueRange = 500f..2500f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = DsCyanPrimary,
                    activeTrackColor = DsCyanPrimary,
                    inactiveTrackColor = DsSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Conversion Speed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CONVERSION SPEED (MOTION SEARCH)",
                    color = DsTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = settings.speed.title,
                    color = DsCyanPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Hero 800x Ultimate Fast Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSettingsChanged(settings.copy(speed = com.example.fastvideo.model.ConversionSpeed.ULTIMATE_FAST_800X)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (settings.speed == com.example.fastvideo.model.ConversionSpeed.ULTIMATE_FAST_800X)
                            DsCyanPrimary.copy(alpha = 0.2f) else DsSurface
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (settings.speed == com.example.fastvideo.model.ConversionSpeed.ULTIMATE_FAST_800X)
                                DsCyanPrimary else DsBorder
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "⚡ 800x Ultimate Fast (Recommended)",
                                    color = if (settings.speed == com.example.fastvideo.model.ConversionSpeed.ULTIMATE_FAST_800X) DsCyanPrimary else DsTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Zero-wait hardware streaming. 24-min video converts in ~45 seconds (All-I FastVideoDS stream, size does not matter).",
                                color = DsTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        if (settings.speed == com.example.fastvideo.model.ConversionSpeed.ULTIMATE_FAST_800X) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = DsCyanPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QualityChip(
                        label = "🚀 Turbo",
                        sub = "3-4x Motion Search",
                        isSelected = settings.speed == com.example.fastvideo.model.ConversionSpeed.TURBO,
                        modifier = Modifier.weight(1f),
                        onClick = { onSettingsChanged(settings.copy(speed = com.example.fastvideo.model.ConversionSpeed.TURBO)) }
                    )
                    QualityChip(
                        label = "⚖ Balanced",
                        sub = "Standard",
                        isSelected = settings.speed == com.example.fastvideo.model.ConversionSpeed.BALANCED,
                        modifier = Modifier.weight(1f),
                        onClick = { onSettingsChanged(settings.copy(speed = com.example.fastvideo.model.ConversionSpeed.BALANCED)) }
                    )
                    QualityChip(
                        label = "💎 Fidelity",
                        sub = "Exhaustive",
                        isSelected = settings.speed == com.example.fastvideo.model.ConversionSpeed.QUALITY,
                        modifier = Modifier.weight(1f),
                        onClick = { onSettingsChanged(settings.copy(speed = com.example.fastvideo.model.ConversionSpeed.QUALITY)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Audio Rate
            Text(
                text = "AUDIO ENCODING (IMA-ADPCM)",
                color = DsTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = settings.audioRate == 32000 && settings.stereo,
                    onClick = { onSettingsChanged(settings.copy(audioRate = 32000, stereo = true)) },
                    label = { Text("⚡ 32 kHz (Recommended)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DsCyanPrimary,
                        selectedLabelColor = Color.Black,
                        containerColor = DsSurface,
                        labelColor = DsTextPrimary
                    )
                )

                FilterChip(
                    selected = settings.audioRate == 47605 && settings.stereo,
                    onClick = { onSettingsChanged(settings.copy(audioRate = 47605, stereo = true)) },
                    label = { Text("47,605 Hz (DS High)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DsCyanPrimary,
                        selectedLabelColor = Color.Black,
                        containerColor = DsSurface,
                        labelColor = DsTextPrimary
                    )
                )

                FilterChip(
                    selected = settings.audioRate == 16000,
                    onClick = { onSettingsChanged(settings.copy(audioRate = 16000, stereo = false)) },
                    label = { Text("16 kHz Mono", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DsCyanPrimary,
                        selectedLabelColor = Color.Black,
                        containerColor = DsSurface,
                        labelColor = DsTextPrimary
                    )
                )

                FilterChip(
                    selected = settings.audioRate == 0,
                    onClick = { onSettingsChanged(settings.copy(audioRate = 0)) },
                    label = { Text("Mute", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DsAmberAccent,
                        selectedLabelColor = Color.Black,
                        containerColor = DsSurface,
                        labelColor = DsTextPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Estimated output size
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasStorageIssue) DsRedAlert.copy(alpha = 0.15f) else DsSurfaceVariant.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (hasStorageIssue) DsRedAlert else DsBorder
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasStorageIssue) Icons.Default.Warning else Icons.Default.Storage,
                                contentDescription = "Storage",
                                tint = if (hasStorageIssue) DsRedAlert else DsCyanPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ESTIMATED OUTPUT SIZE",
                                color = DsTextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "%.2f MB (Estimate)".format(estimatedMb),
                            color = DsTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "AVAILABLE STORAGE",
                            color = DsTextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "%.1f MB free".format(freeMb),
                            color = if (hasStorageIssue) DsRedAlert else DsEmeraldValid,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Start conversion button
            Button(
                onClick = onStartConversion,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("start_conversion_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DsCyanPrimary,
                    contentColor = Color.Black
                )
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Start", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (batchCount > 1) "START BATCH CONVERSION ($batchCount VIDEOS)" else "START OFFLINE CONVERSION",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun PresetOptionCard(
    title: String,
    dimensions: String,
    desc: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(if (isSelected) DsCyanPrimary.copy(alpha = 0.15f) else DsSurface, RoundedCornerShape(10.dp))
            .border(
                1.5.dp,
                if (isSelected) DsCyanPrimary else DsBorder,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, color = DsTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (isSelected) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = "Selected", tint = DsCyanPrimary, modifier = Modifier.size(16.dp))
                }
            }
            Text(text = dimensions, color = DsCyanPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(text = desc, color = DsTextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun QualityChip(
    label: String,
    sub: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(if (isSelected) DsAmberAccent.copy(alpha = 0.15f) else DsSurface, RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (isSelected) DsAmberAccent else DsBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = label, color = DsTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text(text = sub, color = DsTextMuted, fontSize = 9.sp)
        }
    }
}
