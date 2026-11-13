package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.CameraMovement
import com.example.domain.model.MovieProject
import com.example.domain.model.MovieScene
import com.example.ui.theme.AmberRender
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

@Composable
fun StoryboardScreen(
    project: MovieProject?,
    scenes: List<MovieScene>,
    selectedSceneId: String?,
    isCompact: Boolean,
    onSelectSceneInStudio: (String) -> Unit,
    onRenderScene: (MovieProject, MovieScene) -> Unit,
    onExtendScene: (MovieProject, MovieScene) -> Unit,
    onDeleteScene: (String, String) -> Unit,
    onRenderAllAndMerge: (MovieProject) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project?.title ?: "Storyboard Editor",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val readyCount = scenes.count { it.renderStatus == "video_ready" }
                Text(
                    text = if (project != null) {
                        "${scenes.size} scenes • $readyCount rendered • ${project.targetDurationMinutes}-min arc"
                    } else {
                        "Select or create a movie project in Studio first."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            if (project != null && scenes.isNotEmpty()) {
                Button(
                    onClick = { onRenderAllAndMerge(project) },
                    colors = ButtonDefaults.buttonColors(containerColor = SakuraMagenta, contentColor = TextPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("batch_render_merge_button")
                ) {
                    Icon(Icons.Filled.MovieFilter, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Render & Merge All", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (project == null || scenes.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.ViewCarousel, contentDescription = null, tint = CyanElectric, modifier = Modifier.size(42.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Storyboard Board Empty", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Use Story-to-Movie in the Studio tab to generate a multi-act anime storyboard.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isCompact) 1 else 2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(scenes.sortedBy { it.orderIndex }, key = { it.id }) { scene ->
                    StoryboardCard(
                        scene = scene,
                        isSelected = scene.id == selectedSceneId,
                        onOpenInStudio = { onSelectSceneInStudio(scene.id) },
                        onRender = { onRenderScene(project, scene) },
                        onExtend = { onExtendScene(project, scene) },
                        onDelete = { onDeleteScene(project.id, scene.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryboardCard(
    scene: MovieScene,
    isSelected: Boolean,
    onOpenInStudio: () -> Unit,
    onRender: () -> Unit,
    onExtend: () -> Unit,
    onDelete: () -> Unit
) {
    val kfFile = remember(scene.keyframePath) {
        scene.keyframePath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
    }
    val statusColor = when (scene.renderStatus) {
        "video_ready" -> EmeraldReady
        "keyframe_ready" -> CyanElectric
        "error" -> MaterialTheme.colorScheme.error
        else -> AmberRender
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, if (isSelected) CyanElectric else ObsidianBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenInStudio() }
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(6.dp), color = ObsidianSurfaceElevated) {
                        Text(
                            text = "ACT ${scene.actNumber} • #${scene.orderIndex + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanElectric,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.15f)) {
                        Text(
                            text = scene.renderStatus.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete scene", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianCanvas),
                contentAlignment = Alignment.Center
            ) {
                if (kfFile != null) {
                    AsyncImage(
                        model = kfFile,
                        contentDescription = scene.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "Keyframe Pending",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }

            Text(scene.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(scene.visualPrompt, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)

            if (scene.dialogueText.isNotBlank()) {
                Text(
                    text = "[${scene.dialogueLanguage}] \"${scene.dialogueText}\"",
                    style = MaterialTheme.typography.labelMedium,
                    color = SakuraMagenta,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "${CameraMovement.fromId(scene.cameraMovement).label} • ${scene.durationSeconds}s • SFX: ${scene.soundEffect}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onRender,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanElectric, contentColor = ObsidianCanvas),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = if (scene.renderStatus == "video_ready") Icons.Filled.Refresh else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (scene.renderStatus == "video_ready") "Regenerate" else "Render MP4", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onExtend,
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = SakuraMagenta, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Extend Shot", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                }
            }
        }
    }
}
