package com.example.fastvideo.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fastvideo.ui.MainUiState
import com.example.ui.theme.DsAmberAccent
import com.example.ui.theme.DsBorder
import com.example.ui.theme.DsCyanDark
import com.example.ui.theme.DsCyanPrimary
import com.example.ui.theme.DsDpadPlastic
import com.example.ui.theme.DsEmeraldValid
import com.example.ui.theme.DsRedAlert
import com.example.ui.theme.DsLcdBezel
import com.example.ui.theme.DsLcdBottomBg
import com.example.ui.theme.DsLcdTopBg
import com.example.ui.theme.DsSurface
import com.example.ui.theme.DsSurfaceVariant
import com.example.ui.theme.DsTextMuted
import com.example.ui.theme.DsTextPrimary
import com.example.ui.theme.DsTextSecondary
import kotlin.math.abs

@Composable
fun DsEmulatorScreen(
    state: MainUiState,
    onTogglePlay: () -> Unit,
    onStepFrame: () -> Unit,
    onSeekFrame: (Int) -> Unit,
    onStartSample: () -> Unit,
    onOpenStrictAudit: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val job = state.emulatorJob
    val frame = state.emulatorFrame

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Nintendo DS Console Housing
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ds_console_frame"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A29)),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF334155))
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Screen Bezel with power LED
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NINTENDO DS™",
                        color = DsTextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(DsEmeraldValid, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "POWER",
                            color = DsEmeraldValid,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // TOP LCD SCREEN (256x192 / 256x144)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(256f / 192f)
                        .background(DsLcdTopBg, RoundedCornerShape(8.dp))
                        .border(3.dp, DsLcdBezel, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (frame != null) {
                        Image(
                            bitmap = frame.bitmap.asImageBitmap(),
                            contentDescription = "DS Video Playback",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        // Top Screen On-Screen Display (OSD)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (frame.isKeyframe) "I-FRAME" else "P-FRAME",
                                        color = if (frame.isKeyframe) DsAmberAccent else DsCyanPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "%.1f FPS".format(state.emulatorFps),
                                        color = DsEmeraldValid,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Audio VU Level Meters
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "F: ${frame.frameIndex + 1} / ${job?.totalFrames ?: '?'}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                val leftMax = frame.audioSamplesLeft.maxOfOrNull { abs(it.toInt()) } ?: 0
                                val rightMax = frame.audioSamplesRight.maxOfOrNull { abs(it.toInt()) } ?: 0
                                val leftPct = if (state.isEmulatorPlaying && state.emulatorAudioLevelL > 0f) state.emulatorAudioLevelL else (leftMax / 32768f).coerceIn(0f, 1f)
                                val rightPct = if (state.isEmulatorPlaying && state.emulatorAudioLevelR > 0f) state.emulatorAudioLevelR else (rightMax / 32768f).coerceIn(0f, 1f)

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "L", color = DsCyanPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    VuBar(level = leftPct)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "R", color = DsCyanPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    VuBar(level = rightPct)
                                }
                            }
                        }
                    } else {
                        // Empty emulator placeholder
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = "DS Emulator",
                                tint = DsCyanPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Nintendo DS Top LCD (256×192)",
                                color = DsTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "No FastVideoDS (.fv) file loaded",
                                color = DsTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // HINGE AND STEREO SPEAKERS
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpeakerGrille()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .padding(horizontal = 16.dp)
                            .background(Color(0xFF0F172A), RoundedCornerShape(3.dp))
                    )
                    SpeakerGrille()
                }
                Spacer(modifier = Modifier.height(10.dp))

                // BOTTOM TOUCHSCREEN (Playback Controls & Telemetry)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(256f / 192f)
                        .background(DsLcdBottomBg, RoundedCornerShape(8.dp))
                        .border(3.dp, DsLcdBezel, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = job?.sourceMeta?.name ?: "FastVideoDS Player v1.0",
                                color = DsCyanPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "TOUCHSCREEN",
                                color = DsTextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Telemetry Grid
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "CPU ARCH:", color = DsTextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text(text = "ARM946E-S @ 67 MHz", color = DsTextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "AUDIO DAC:", color = DsTextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text(text = "${job?.settings?.audioRate ?: 47605} Hz IMA-ADPCM", color = DsAmberAccent, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "CONTAINER:", color = DsTextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text(text = "FVDS • Keyframe GOP Table", color = DsEmeraldValid, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }

                        // Scrubber Slider
                        val currentF = frame?.frameIndex ?: 0
                        val maxF = (job?.totalFrames ?: 100).coerceAtLeast(1)
                        Slider(
                            value = currentF.toFloat(),
                            onValueChange = { onSeekFrame(it.toInt()) },
                            valueRange = 0f..maxF.toFloat(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = DsCyanPrimary,
                                activeTrackColor = DsCyanPrimary,
                                inactiveTrackColor = DsSurfaceVariant
                            )
                        )

                        // Playback Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onSeekFrame(0) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(DsDpadPlastic, CircleShape)
                            ) {
                                Icon(imageVector = Icons.Default.FastRewind, contentDescription = "Start", tint = DsTextPrimary, modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = onTogglePlay,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(DsCyanPrimary, CircleShape)
                                    .testTag("emulator_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (state.isEmulatorPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (state.isEmulatorPlaying) "Pause" else "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            IconButton(
                                onClick = onStepFrame,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(DsDpadPlastic, CircleShape)
                                    .testTag("emulator_step_frame_button")
                            ) {
                                Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next Frame", tint = DsTextPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick test run sample button if no video loaded
        if (job == null) {
            Button(
                onClick = onStartSample,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("test_run_sample_emulator_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DsCyanPrimary,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Test Run", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Make a Test Run: Encode & Play in DS Emulator",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Playing: ${job.outputFilePath.substringAfterLast("/")}",
                    color = DsTextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (onOpenStrictAudit != null) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onOpenStrictAudit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("emulator_view_strict_audit_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DsCyanPrimary),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(DsCyanPrimary.copy(alpha = 0.5f))
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "View 10-Point After-Convert Audit",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeakerGrille() {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(Color(0xFF0F172A), CircleShape)
            )
        }
    }
}

@Composable
private fun VuBar(level: Float) {
    Box(
        modifier = Modifier
            .width(28.dp)
            .height(5.dp)
            .background(Color(0xFF1E293B), RoundedCornerShape(2.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(level)
                .height(5.dp)
                .background(
                    if (level > 0.8f) DsRedAlert else if (level > 0.5f) DsAmberAccent else DsEmeraldValid,
                    RoundedCornerShape(2.dp)
                )
        )
    }
}
