package com.example.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.domain.model.CameraMovement
import com.example.domain.model.DialogueLanguage
import com.example.domain.model.MovieScene
import com.example.domain.model.SoundEffectType
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.SakuraMagenta
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun VideoPreviewMonitor(
    scene: MovieScene?,
    mergedMoviePath: String?,
    isPlayingMergedFeature: Boolean,
    aspectRatio: String,
    resolutionLabel: String,
    lipSyncEnvelope: List<Float>,
    onToggleMergedPreview: (Boolean) -> Unit,
    onRenderScene: (MovieScene) -> Unit,
    onExportMp4: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeVideoPath = if (isPlayingMergedFeature && !mergedMoviePath.isNullOrBlank()) {
        mergedMoviePath
    } else {
        scene?.videoClipPath.orEmpty()
    }
    val activeVideoFile = remember(activeVideoPath) {
        activeVideoPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() && it.length() > 1024 }
    }
    val keyframeFile = remember(scene?.keyframePath) {
        scene?.keyframePath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() && it.length() > 256 }
    }

    val ratioFloat = when (aspectRatio) {
        "9:16" -> 9f / 16f
        "21:9" -> 21f / 9f
        else -> 16f / 9f
    }.coerceIn(0.56f, 2.35f)

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    LaunchedEffect(activeVideoFile?.absolutePath, activeVideoFile?.lastModified()) {
        if (activeVideoFile != null) {
            exoPlayer.setMediaItem(MediaItem.fromUri(activeVideoFile.toUri()))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Monitor Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (activeVideoFile != null) EmeraldReady.copy(alpha = 0.16f) else CyanElectric.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = when {
                                isPlayingMergedFeature && activeVideoFile != null -> "MERGED FEATURE MP4"
                                activeVideoFile != null -> "REAL MP4 READY"
                                keyframeFile != null -> "KEYFRAME ONLY"
                                else -> "STUDIO MONITOR"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (activeVideoFile != null) EmeraldReady else CyanElectric,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = if (isPlayingMergedFeature) "Full Merged Cut" else scene?.title ?: "Select or Create Scene",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!mergedMoviePath.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { onToggleMergedPreview(!isPlayingMergedFeature) },
                            border = BorderStroke(1.dp, if (isPlayingMergedFeature) SakuraMagenta else ObsidianBorder),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(36.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Movie,
                                contentDescription = "Toggle merged feature preview",
                                tint = if (isPlayingMergedFeature) SakuraMagenta else TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPlayingMergedFeature) "Scene View" else "Full Cut",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary
                            )
                        }
                    }

                    if (activeVideoFile != null) {
                        OutlinedButton(
                            onClick = onExportMp4,
                            border = BorderStroke(1.dp, CyanElectric.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("export_mp4_button"),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = "Export MP4 to Gallery",
                                tint = CyanElectric,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Export",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanElectric
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Video Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (aspectRatio == "9:16") {
                            Modifier.height(320.dp)
                        } else {
                            Modifier.aspectRatio(ratioFloat)
                        }
                    )
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianCanvas),
                contentAlignment = Alignment.Center
            ) {
                when {
                    activeVideoFile != null -> {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = true
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            },
                            update = { view ->
                                if (view.player !== exoPlayer) {
                                    view.player = exoPlayer
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    keyframeFile != null && scene != null -> {
                        AsyncImage(
                            model = keyframeFile,
                            contentDescription = "Generated anime scene keyframe",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, ObsidianCanvas.copy(alpha = 0.85f))
                                    )
                                )
                        )
                        Button(
                            onClick = { onRenderScene(scene) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanElectric,
                                contentColor = ObsidianCanvas
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.testTag("render_scene_mp4_overlay_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Synthesize Video + Voice MP4", fontWeight = FontWeight.Bold)
                        }
                    }

                    scene != null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = null,
                                tint = CyanElectric.copy(alpha = 0.7f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = scene.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Scene prompt ready. Generate real AI keyframe, ${DialogueLanguage.fromCode(scene.dialogueLanguage).label} voice & MP4 clip.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { onRenderScene(scene) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyanElectric,
                                    contentColor = ObsidianCanvas
                                ),
                                modifier = Modifier.testTag("render_scene_mp4_button")
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Generate AI Keyframe & MP4", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    else -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Movie,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Start a Story-to-Movie or Image-to-Video prompt below",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Telemetry & Lip-Sync Strip
            if (scene != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        TelemetryChip(CameraMovement.fromId(scene.cameraMovement).shortTag, CyanElectric)
                        TelemetryChip(DialogueLanguage.fromCode(scene.dialogueLanguage).code, SakuraMagenta)
                        if (scene.soundEffect != SoundEffectType.NONE.id) {
                            TelemetryChip(scene.soundEffect, Color(0xFFF59E0B))
                        }
                        TelemetryChip(resolutionLabel, TextSecondary)
                    }

                    if (activeVideoFile != null) {
                        OutlinedButton(
                            onClick = { onRenderScene(scene) },
                            border = BorderStroke(1.dp, ObsidianBorder),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Regenerate scene MP4",
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Regenerate", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                        }
                    }
                }

                if (lipSyncEnvelope.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LipSyncMiniBar(lipSyncEnvelope)
                }
            }
        }
    }
}

@Composable
private fun TelemetryChip(label: String, tint: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = ObsidianSurfaceElevated,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun LipSyncMiniBar(envelope: List<Float>) {
    val transition = rememberInfiniteTransition(label = "lipSyncPulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Restart),
        label = "lipPhase"
    )
    val sampleCount = 28
    val step = (envelope.size / sampleCount).coerceAtLeast(1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ObsidianSurfaceElevated)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = "LIP-SYNC",
            style = MaterialTheme.typography.labelSmall,
            color = CyanElectric
        )
        Spacer(modifier = Modifier.width(4.dp))
        for (i in 0 until sampleCount) {
            val idx = (i * step).coerceIn(0, envelope.lastIndex)
            val amp = envelope[idx].coerceIn(0.1f, 1f)
            val activePos = (phase * sampleCount).toInt()
            val barColor = if (i == activePos) SakuraMagenta else CyanElectric.copy(alpha = 0.4f + 0.6f * amp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((4 + 10 * amp).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}
