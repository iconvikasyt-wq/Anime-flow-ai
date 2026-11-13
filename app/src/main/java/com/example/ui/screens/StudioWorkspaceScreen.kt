package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.AnimeCharacter
import com.example.domain.model.BgmMood
import com.example.domain.model.CameraMovement
import com.example.domain.model.CharacterEmotion
import com.example.domain.model.DialogueLanguage
import com.example.domain.model.MovieProject
import com.example.domain.model.MovieScene
import com.example.domain.model.SoundEffectType
import com.example.domain.model.VisualStyle
import com.example.ui.components.ImageToVideoDialog
import com.example.ui.components.StoryToMovieDialog
import com.example.ui.components.VideoPreviewMonitor
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
import com.example.ui.viewmodel.UiState
import java.io.File

@Composable
fun StudioWorkspaceScreen(
    projectsState: UiState<List<MovieProject>>,
    characters: List<AnimeCharacter>,
    selectedProjectId: String?,
    scenes: List<MovieScene>,
    selectedSceneId: String?,
    mergedMoviePath: String?,
    exportResolutionLabel: String,
    lipSyncMap: Map<String, List<Float>>,
    isCompact: Boolean,
    onSelectProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onSelectScene: (String) -> Unit,
    onCreateStoryMovie: (
        title: String,
        storyPrompt: String,
        durationMinutes: Long,
        style: VisualStyle,
        language: DialogueLanguage,
        aspectRatio: String,
        characterIds: List<String>,
        sceneCount: Int
    ) -> Unit,
    onCreateImageToVideo: (
        project: MovieProject,
        imageUri: Uri,
        motionPrompt: String,
        dialogueText: String,
        languageCode: String,
        cameraMovementId: String,
        soundEffectId: String,
        bgmMoodId: String,
        speakerId: String
    ) -> Unit,
    onUpdateScene: (String, MovieScene) -> Unit,
    onRenderScene: (MovieProject, MovieScene) -> Unit,
    onExtendScene: (MovieProject, MovieScene) -> Unit,
    onRenderAllAndMerge: (MovieProject) -> Unit,
    onExportMp4: (MovieProject) -> Unit,
    modifier: Modifier = Modifier
) {
    val projects = (projectsState as? UiState.Success)?.data.orEmpty()
    val activeProject = projects.firstOrNull { it.id == selectedProjectId } ?: projects.firstOrNull()
    val activeScene = scenes.firstOrNull { it.id == selectedSceneId } ?: scenes.firstOrNull()

    var showStoryDialog by remember { mutableStateOf(false) }
    var showI2vDialog by remember { mutableStateOf(false) }
    var quickPrompt by remember { mutableStateOf("") }
    var isPlayingMerged by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // Top Project Bar + Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Director Studio",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (activeProject != null) {
                    OutlinedButton(
                        onClick = { showI2vDialog = true },
                        border = BorderStroke(1.dp, ObsidianBorder),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("open_i2v_button")
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = SakuraMagenta, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Image→Video", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                    }
                }
                Button(
                    onClick = { showStoryDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanElectric, contentColor = ObsidianCanvas),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("new_movie_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Story→Movie", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Project Selector Strip
        if (projects.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(projects, key = { it.id }) { proj ->
                    val selected = proj.id == activeProject?.id
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) CyanElectric.copy(alpha = 0.16f) else ObsidianSurface,
                        border = BorderStroke(1.dp, if (selected) CyanElectric else ObsidianBorder),
                        modifier = Modifier.clickable {
                            isPlayingMerged = false
                            onSelectProject(proj.id)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${proj.title} (${proj.targetDurationMinutes}m)",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) CyanElectric else TextPrimary,
                                maxLines = 1
                            )
                            if (selected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { onDeleteProject(proj.id) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Delete project",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (activeProject == null) {
            // Empty Studio Hero Prompt Dock (Focal / Flow style)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                VideoPreviewMonitor(
                    scene = null,
                    mergedMoviePath = null,
                    isPlayingMergedFeature = false,
                    aspectRatio = "16:9",
                    resolutionLabel = exportResolutionLabel,
                    lipSyncEnvelope = emptyList(),
                    onToggleMergedPreview = {},
                    onRenderScene = {},
                    onExportMp4 = {}
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ObsidianSurface,
                    border = BorderStroke(1.dp, CyanElectric.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Direct Your First 3D Anime Film",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "Enter a concept below for instant Story-to-Movie generation (15–120 min multi-act arc, consistent 3D characters, Hindi/Japanese/English voice & SFX).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        OutlinedTextField(
                            value = quickPrompt,
                            onValueChange = { quickPrompt = it },
                            placeholder = { Text("e.g. A mech-pilot nun and a rogue hacker race through floating Neo-Varanasi...") },
                            minLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanElectric,
                                unfocusedBorderColor = ObsidianBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("quick_prompt_input")
                        )
                        Button(
                            onClick = { showStoryDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanElectric, contentColor = ObsidianCanvas),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("launch_story_dialog_button")
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Configure & Generate Movie", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (isCompact) {
            // Mobile Vertical Studio Layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VideoPreviewMonitor(
                    scene = activeScene,
                    mergedMoviePath = mergedMoviePath,
                    isPlayingMergedFeature = isPlayingMerged,
                    aspectRatio = activeProject.aspectRatio,
                    resolutionLabel = exportResolutionLabel,
                    lipSyncEnvelope = activeScene?.id?.let { lipSyncMap[it] }.orEmpty(),
                    onToggleMergedPreview = { isPlayingMerged = it },
                    onRenderScene = { scene -> onRenderScene(activeProject, scene) },
                    onExportMp4 = { onExportMp4(activeProject) }
                )

                SceneTimelineStrip(
                    project = activeProject,
                    scenes = scenes,
                    selectedSceneId = activeScene?.id,
                    onSelectScene = {
                        isPlayingMerged = false
                        onSelectScene(it)
                    },
                    onRenderAllAndMerge = {
                        isPlayingMerged = true
                        onRenderAllAndMerge(activeProject)
                    }
                )

                if (activeScene != null) {
                    SceneInspectorCard(
                        project = activeProject,
                        scene = activeScene,
                        characters = characters,
                        onSaveEdits = { onUpdateScene(activeProject.id, it) },
                        onRenderScene = { onRenderScene(activeProject, it) },
                        onExtendScene = { onExtendScene(activeProject, it) }
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        } else {
            // Tablet / Desktop Responsive Side-by-Side Workspace
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.25f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VideoPreviewMonitor(
                        scene = activeScene,
                        mergedMoviePath = mergedMoviePath,
                        isPlayingMergedFeature = isPlayingMerged,
                        aspectRatio = activeProject.aspectRatio,
                        resolutionLabel = exportResolutionLabel,
                        lipSyncEnvelope = activeScene?.id?.let { lipSyncMap[it] }.orEmpty(),
                        onToggleMergedPreview = { isPlayingMerged = it },
                        onRenderScene = { scene -> onRenderScene(activeProject, scene) },
                        onExportMp4 = { onExportMp4(activeProject) }
                    )
                    SceneTimelineStrip(
                        project = activeProject,
                        scenes = scenes,
                        selectedSceneId = activeScene?.id,
                        onSelectScene = {
                            isPlayingMerged = false
                            onSelectScene(it)
                        },
                        onRenderAllAndMerge = {
                            isPlayingMerged = true
                            onRenderAllAndMerge(activeProject)
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(0.95f)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (activeScene != null) {
                        SceneInspectorCard(
                            project = activeProject,
                            scene = activeScene,
                            characters = characters,
                            onSaveEdits = { onUpdateScene(activeProject.id, it) },
                            onRenderScene = { onRenderScene(activeProject, it) },
                            onExtendScene = { onExtendScene(activeProject, it) }
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }

    if (showStoryDialog) {
        StoryToMovieDialog(
            characters = characters,
            initialPrompt = quickPrompt,
            onDismiss = { showStoryDialog = false },
            onCreateMovie = { title, prompt, dur, style, lang, ar, charIds, count ->
                showStoryDialog = false
                onCreateStoryMovie(title, prompt, dur, style, lang, ar, charIds, count)
            }
        )
    }

    if (showI2vDialog && activeProject != null) {
        ImageToVideoDialog(
            characters = characters,
            defaultLanguageCode = activeProject.defaultLanguage,
            onDismiss = { showI2vDialog = false },
            onCreateImageToVideo = { uri, prompt, dlg, lang, cam, sfx, bgm, spk ->
                showI2vDialog = false
                onCreateImageToVideo(activeProject, uri, prompt, dlg, lang, cam, sfx, bgm, spk)
            }
        )
    }
}

@Composable
private fun SceneTimelineStrip(
    project: MovieProject,
    scenes: List<MovieScene>,
    selectedSceneId: String?,
    onSelectScene: (String) -> Unit,
    onRenderAllAndMerge: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val totalSec = scenes.sumOf { it.durationSeconds }
                Text(
                    text = "SCENE TIMELINE • ${scenes.size} CLIPS (${totalSec}s preview / ${project.targetDurationMinutes}m film)",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanElectric
                )
                OutlinedButton(
                    onClick = onRenderAllAndMerge,
                    border = BorderStroke(1.dp, SakuraMagenta.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp).testTag("timeline_merge_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Filled.MovieFilter, contentDescription = null, tint = SakuraMagenta, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Merge All Clips", style = MaterialTheme.typography.labelSmall, color = SakuraMagenta)
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(scenes.sortedBy { it.orderIndex }, key = { it.id }) { scene ->
                    val selected = scene.id == selectedSceneId
                    val kfFile = remember(scene.keyframePath) {
                        scene.keyframePath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
                    }
                    val dotColor = when (scene.renderStatus) {
                        "video_ready" -> EmeraldReady
                        "keyframe_ready" -> CyanElectric
                        else -> AmberRender
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianSurfaceElevated,
                        border = BorderStroke(1.5.dp, if (selected) CyanElectric else ObsidianBorder),
                        modifier = Modifier
                            .width(134.dp)
                            .clickable { onSelectScene(scene.id) }
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
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
                                    Text("#${scene.orderIndex + 1}", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                                }
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(dotColor)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = scene.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SceneInspectorCard(
    project: MovieProject,
    scene: MovieScene,
    characters: List<AnimeCharacter>,
    onSaveEdits: (MovieScene) -> Unit,
    onRenderScene: (MovieScene) -> Unit,
    onExtendScene: (MovieScene) -> Unit
) {
    var visualPrompt by remember(scene.id, scene.visualPrompt) { mutableStateOf(scene.visualPrompt) }
    var dialogueText by remember(scene.id, scene.dialogueText) { mutableStateOf(scene.dialogueText) }
    var languageCode by remember(scene.id, scene.dialogueLanguage) { mutableStateOf(scene.dialogueLanguage) }
    var emotion by remember(scene.id, scene.emotion) { mutableStateOf(scene.emotion) }
    var cameraMovement by remember(scene.id, scene.cameraMovement) { mutableStateOf(scene.cameraMovement) }
    var soundEffect by remember(scene.id, scene.soundEffect) { mutableStateOf(scene.soundEffect) }
    var bgmMood by remember(scene.id, scene.bgmMood) { mutableStateOf(scene.bgmMood) }
    var speakerId by remember(scene.id, scene.speakerCharacterId) { mutableStateOf(scene.speakerCharacterId) }
    var duration by remember(scene.id, scene.durationSeconds) { mutableFloatStateOf(scene.durationSeconds.toFloat()) }
    val selectedChars = remember(scene.id, scene.characterIds) {
        mutableStateListOf<String>().apply { addAll(scene.characterIds) }
    }

    fun buildEditedScene(): MovieScene = scene.copy(
        visualPrompt = visualPrompt,
        dialogueText = dialogueText,
        dialogueLanguage = languageCode,
        emotion = emotion,
        cameraMovement = cameraMovement,
        soundEffect = soundEffect,
        bgmMood = bgmMood,
        speakerCharacterId = speakerId,
        characterIds = selectedChars.toList(),
        durationSeconds = duration.toLong()
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "SCENE INSPECTOR • ACT ${scene.actNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = CyanElectric
            )

            OutlinedTextField(
                value = visualPrompt,
                onValueChange = { visualPrompt = it },
                label = { Text("3D Anime Visual Prompt") },
                minLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanElectric,
                    unfocusedBorderColor = ObsidianBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = dialogueText,
                onValueChange = { dialogueText = it },
                label = { Text("Dialogue (Auto Lip-Sync + Voice Acting)") },
                minLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SakuraMagenta,
                    unfocusedBorderColor = ObsidianBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (characters.isNotEmpty()) {
                Text("Characters in Scene (Multi-Character Consistency)", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    characters.forEach { char ->
                        val active = char.id in selectedChars
                        FilterChip(
                            selected = active,
                            onClick = {
                                if (active) selectedChars.remove(char.id)
                                else if (selectedChars.size < 8) selectedChars.add(char.id)
                                if (speakerId.isBlank() && !active) speakerId = char.id
                            },
                            label = { Text(char.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanElectric.copy(alpha = 0.2f),
                                selectedLabelColor = CyanElectric
                            )
                        )
                    }
                }
            }

            Text("Dialogue Language (EN / JA / HI)", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DialogueLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = languageCode == lang.code,
                        onClick = { languageCode = lang.code },
                        label = { Text(lang.label) }
                    )
                }
            }

            Text("Character Emotion", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CharacterEmotion.entries.forEach { emo ->
                    FilterChip(
                        selected = emotion == emo.label,
                        onClick = { emotion = emo.label },
                        label = { Text(emo.label) }
                    )
                }
            }

            Text("3D Camera Movement", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CameraMovement.entries.forEach { cam ->
                    FilterChip(
                        selected = cameraMovement == cam.id,
                        onClick = { cameraMovement = cam.id },
                        label = { Text(cam.shortTag) }
                    )
                }
            }

            Text("Sound Effect Layer", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SoundEffectType.entries.forEach { sfx ->
                    FilterChip(
                        selected = soundEffect == sfx.id,
                        onClick = { soundEffect = sfx.id },
                        label = { Text(sfx.label) }
                    )
                }
            }

            Text("Background Music", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BgmMood.entries.forEach { bgm ->
                    FilterChip(
                        selected = bgmMood == bgm.id,
                        onClick = { bgmMood = bgm.id },
                        label = { Text(bgm.label) }
                    )
                }
            }

            Text("Clip Duration: ${duration.toInt()}s", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Slider(
                value = duration,
                onValueChange = { duration = it },
                valueRange = 4f..15f,
                colors = SliderDefaults.colors(thumbColor = CyanElectric, activeTrackColor = CyanElectric)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onSaveEdits(buildEditedScene()) },
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save", color = TextPrimary)
                }
                OutlinedButton(
                    onClick = { onExtendScene(buildEditedScene()) },
                    border = BorderStroke(1.dp, SakuraMagenta.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).testTag("extend_scene_button")
                ) {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = SakuraMagenta, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Extend", color = SakuraMagenta)
                }
            }

            Button(
                onClick = {
                    val edited = buildEditedScene()
                    onSaveEdits(edited)
                    onRenderScene(edited)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanElectric, contentColor = ObsidianCanvas),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("inspector_render_mp4_button")
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Render Scene MP4 (${project.imageModelId.take(16)})", fontWeight = FontWeight.Bold)
            }
        }
    }
}
