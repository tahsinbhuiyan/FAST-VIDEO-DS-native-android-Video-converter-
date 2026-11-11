package com.example.fastvideo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fastvideo.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "DS Hardware & Flashcard Guide",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
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
            // Architecture & Engineering Credits (User requested)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("architecture_credits_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ENGINEERING & CREDITS",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Credit goes to Tahsin Bhuiyan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "levanesky.iv@gmail.com",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Universal Hardware Architecture: FastVideoDS Public Android Engine. Built with zero-copy MediaCodec hardware decimation and contiguous 32 kHz IMA-ADPCM audio streaming, calibrated to achieve Samsung Galaxy A70 & Snapdragon performance on all devices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Flashcard Quick Setup Guide
            item {
                GuideCard(
                    icon = Icons.Default.SdCard,
                    title = "1. Flashcard Transfer (R4 / TwilightMenu++)",
                    subtitle = "Getting your .fv files onto physical Nintendo DS hardware"
                ) {
                    GuideStep(number = "1", title = "Save .fv to Downloads", text = "Tap the 'Save to 🗄️ Files / Downloads' button on any converted video in this app.")
                    GuideStep(number = "2", title = "Insert Micro-SD into Phone / PC", text = "Connect your flashcard's micro-SD card (FAT32 formatted) using an OTG USB adapter or PC card reader.")
                    GuideStep(number = "3", title = "Copy .fv into Video Directory", text = "Place the .fv files into a 'videos' folder or the root of the micro-SD card.")
                    GuideStep(number = "4", title = "Launch FastVideoDS Player .nds", text = "Run FastVideoDS.nds in YSMenu, Wood R4, Ace3DS+, or TwilightMenu++ and select your video.")
                }
            }

            // DS vs DSi Performance Comparison
            item {
                GuideCard(
                    icon = Icons.Default.Speed,
                    title = "2. Console Hardware Considerations",
                    subtitle = "ARM9 Processor Speeds & Memory Constraints"
                ) {
                    Text(
                        text = "The Nintendo DS features an ARM946E-S main processor with distinct clock frequencies between models:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Original DS / DS Lite:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text("67 MHz · 4 MB RAM", style = MaterialTheme.typography.labelMedium)
                            }
                            Text(
                                "Best with 256x144, 24 FPS, and under 250 kbps video bitrate. High bitrates can drop frames due to SD card read latency.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Divider()
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Nintendo DSi / DSi XL:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text("133 MHz · 16 MB RAM", style = MaterialTheme.typography.labelMedium)
                            }
                            Text(
                                "DSi mode handles full 256x192 at 30 FPS effortlessly with higher bitrates and stereo audio.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Bitstream Compliance & Physical Testing Note
            item {
                GuideCard(
                    icon = Icons.Default.Gavel,
                    title = "3. Bitstream Validation vs Physical Testing",
                    subtitle = "Gericom FastVideoDS Spec Fidelity"
                ) {
                    Text(
                        text = "This app strictly produces genuine FastVideoDS bitstreams conforming to the official Gericom open-source specification:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Container Magic: 0x53445646 ('FVDS') in 32-bit little-endian\n" +
                                "• Keyframe Index Table: Exact 8-byte entries [fileOffset, audioOffset]\n" +
                                "• Transform: 4x4 integer Discrete Cosine Transform with Trellis quantization\n" +
                                "• Audio: 4-bit IMA-ADPCM at 47605 Hz or 32000 Hz, 256 samples/frame\n" +
                                "• Color Encoding: 15-bit RGB555 with Bayer 4x4 spatial error dithering",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Note: MicroSD read speeds on legacy clone flashcards (e.g. older R4 clones) may vary. If audio stutters, choose the 'DS Lite Economy' preset.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Offline & APK Backup Guide
            item {
                GuideCard(
                    icon = Icons.Default.SaveAlt,
                    title = "4. 1-Tap APK Export & Airplane Mode",
                    subtitle = "100% offline, private and self-contained"
                ) {
                    Text(
                        text = "FV Offline Converter does not communicate with external servers, cloud AI, or telemetry. It operates entirely offline on Android devices like OPPO A16 (Helio G35) and Samsung Galaxy A70 (Snapdragon 675).\n\nUse the 'Download / Save APK' button in the top bar to export this app's installer file directly to your Downloads folder at any time for offline archiving or sharing with other retro enthusiasts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.exportApk() },
                        modifier = Modifier.fillMaxWidth().testTag("guide_export_apk_button")
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export App APK to Downloads Now")
                    }
                }
            }
        }
    }
}

@Composable
fun GuideCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun GuideStep(number: String, title: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
