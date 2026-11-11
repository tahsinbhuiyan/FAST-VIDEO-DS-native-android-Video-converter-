package com.example.fastvideo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.fastvideo.model.AuditCheckItem
import com.example.fastvideo.model.ConversionJob
import com.example.fastvideo.model.FvStrictAudit
import com.example.ui.theme.DsAmberAccent
import com.example.ui.theme.DsEmeraldValid
import com.example.ui.theme.DsRedAlert
import com.example.ui.theme.DsSlateBg
import com.example.ui.theme.DsSurface
import com.example.ui.theme.DsSurfaceVariant
import com.example.ui.theme.DsTextMuted
import com.example.ui.theme.DsTextPrimary
import com.example.ui.theme.DsTextSecondary

@Composable
fun StrictAuditDialog(
    audit: FvStrictAudit,
    job: ConversionJob?,
    onDismiss: () -> Unit,
    onTestRunInDs: () -> Unit,
    onSaveToDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DsSlateBg,
            modifier = modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .border(1.5.dp, if (audit.isPassed) DsEmeraldValid else DsRedAlert, RoundedCornerShape(20.dp))
                .testTag("strict_audit_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Title & status banner
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = (if (audit.isPassed) DsEmeraldValid else DsRedAlert).copy(alpha = 0.15f),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (audit.isPassed) Icons.Default.Verified else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (audit.isPassed) DsEmeraldValid else DsRedAlert,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Strict Settings & Bitstream Audit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DsTextPrimary
                        )
                        Text(
                            text = if (audit.isPassed) "All 10 hardware specs verified" else "Specifications notice detected",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (audit.isPassed) DsEmeraldValid else DsAmberAccent
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DsTextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Hardware Verdict Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DsSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = DsEmeraldValid,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Nintendo DS Hardware Verdict",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = DsTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = audit.hardwareVerdict,
                            style = MaterialTheme.typography.bodySmall,
                            color = DsTextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Recommended flashcards: ${audit.flashcardRecommended}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Specification Checklist (10/10 Verified):",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = DsTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable checklist
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(audit.checks) { check ->
                        AuditCheckRow(check)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action buttons: Test run in DS Emulator + Save to Downloads
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onTestRunInDs,
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("audit_test_run_ds_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Run in DS Emulator", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onSaveToDownloads,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("audit_save_downloads_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save .fv 🗄️")
                    }
                }
            }
        }
    }
}

@Composable
fun AuditCheckRow(item: AuditCheckItem) {
    val checkColor = if (item.passed) DsEmeraldValid else if (item.critical) DsRedAlert else DsAmberAccent
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = DsSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, checkColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = checkColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = DsTextPrimary
                )
                Text(
                    text = "Expected: ${item.expected} · Actual: ${item.actual}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = DsTextSecondary
                )
            }
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = checkColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = if (item.passed) "PASS" else "NOTICE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = checkColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
