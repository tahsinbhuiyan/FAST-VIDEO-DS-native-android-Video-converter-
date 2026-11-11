package com.example.fastvideo.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fastvideo.device.DeviceDiagnosticsReport
import com.example.ui.theme.DsAmberAccent
import com.example.ui.theme.DsBorder
import com.example.ui.theme.DsCyanPrimary
import com.example.ui.theme.DsEmeraldValid
import com.example.ui.theme.DsSurface
import com.example.ui.theme.DsSurfaceVariant
import com.example.ui.theme.DsTextMuted
import com.example.ui.theme.DsTextPrimary
import com.example.ui.theme.DsTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceStatusCard(
    diagnostics: DeviceDiagnosticsReport?,
    modifier: Modifier = Modifier
) {
    if (diagnostics == null) return
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("device_status_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DsSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DsBorder))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = "Device Info",
                        tint = DsCyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "${diagnostics.manufacturer} ${diagnostics.model}",
                            color = DsTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Android ${diagnostics.androidVersion} (API ${diagnostics.apiLevel}) • ${diagnostics.primaryAbi}",
                            color = DsTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(DsEmeraldValid.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .border(1.dp, DsEmeraldValid.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "ARM Ready",
                            color = DsEmeraldValid,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = DsTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Metrics Chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricChip(
                    icon = Icons.Default.Storage,
                    label = "Free Storage",
                    value = diagnostics.freeStorageFormatted,
                    tint = DsCyanPrimary
                )
                MetricChip(
                    icon = Icons.Default.Memory,
                    label = "RAM Avail",
                    value = "${diagnostics.availableRamFormatted} / ${diagnostics.totalRamFormatted}",
                    tint = if (diagnostics.isLowMemory) DsAmberAccent else DsCyanPrimary
                )
                MetricChip(
                    icon = Icons.Default.Thermostat,
                    label = "Thermals",
                    value = diagnostics.thermalStatus,
                    tint = if (diagnostics.thermalStatus.contains("Throttl", ignoreCase = true)) DsAmberAccent else DsEmeraldValid
                )
            }

            // Expanded Hardware Architecture & Credits details
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = "UNIVERSAL HARDWARE ARCHITECTURE (PUBLIC ENGINE)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = DsTextMuted,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DsSurfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .border(1.dp, DsCyanPrimary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "⚡ ${diagnostics.universalProfile.detectedSoc}",
                            color = DsCyanPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = diagnostics.universalProfile.speedCapability,
                            color = DsEmeraldValid,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = diagnostics.universalProfile.codecStatus,
                            color = DsTextSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = diagnostics.universalProfile.optimizationNotes,
                            color = DsTextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Architecture & Engineering Credits
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DsAmberAccent.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DsAmberAccent.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "CREDITS & ENGINEERING",
                                color = DsAmberAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Credit goes to Tahsin Bhuiyan",
                                color = DsTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "levanesky.iv@gmail.com",
                                color = DsTextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color
) {
    Row(
        modifier = Modifier
            .background(DsSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = "$label: ", color = DsTextSecondary, fontSize = 11.sp)
        Text(text = value, color = DsTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TargetDeviceItem(
    check: com.example.fastvideo.device.TargetDeviceCheck,
    isCurrent: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DsSurfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .border(
                1.dp,
                if (isCurrent) DsCyanPrimary.copy(alpha = 0.6f) else DsBorder.copy(alpha = 0.3f),
                RoundedCornerShape(10.dp)
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Compatible",
                    tint = DsEmeraldValid,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = check.deviceName,
                    color = DsTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            if (isCurrent) {
                Text(
                    text = "THIS PHONE",
                    color = DsCyanPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${check.chipArchitecture} • Target: ${check.androidTargetOs}",
            color = DsTextSecondary,
            fontSize = 11.sp
        )
        Text(
            text = check.statusSummary,
            color = DsEmeraldValid,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = check.optimizationNotes,
            color = DsTextMuted,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}
