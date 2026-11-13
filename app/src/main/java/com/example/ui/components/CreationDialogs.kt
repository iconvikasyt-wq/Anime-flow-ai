package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.AnimeCharacter
import com.example.domain.model.BgmMood
import com.example.domain.model.CameraMovement
import com.example.domain.model.DialogueLanguage
import com.example.domain.model.SoundEffectType
import com.example.domain.model.VisualStyle
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SakuraMagenta
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoryToMovieDialog(
    characters: List<AnimeCharacter>,
    initialPrompt: String = "",
    onDismiss: () -> Unit,
    onCreateMovie: (
        title: String,
        storyPrompt: String,
        durationMinutes: Long,
        style: VisualStyle,
        language: DialogueLanguage,
        aspectRatio: String,
        characterIds: List<String>,
        sceneCount: Int
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var storyPrompt by remember { mutableStateOf(initialPrompt) }
    var durationMinutes by remember { mutableFloatStateOf(30f) }
    var selectedStyle by remember { mutableStateOf(VisualStyle.CEL_ANIME_3D) }
    var selectedLang by remember { mutableStateOf(DialogueLanguage.EN) }
    var aspectRatio by remember { mutableStateOf("16:9") }
    var sceneCount by remember { mutableIntStateOf(6) }
    val selectedCharIds = remember { mutableStateListOf<String>().apply { addAll(characters.take(2).map { it.id }) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Text("Story to 3D Anime Movie", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Movie Title") },
                    placeholder = { Text("e.g. Neon Sakura Protocol") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanElectric,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("movie_title_input")
                )

                OutlinedTextField(
                    value = storyPrompt,
                    onValueChange = { storyPrompt = it },
                    label = { Text("Story Concept / Screenplay Prompt") },
                    placeholder = { Text("Describe your anime film plot, world, conflicts & climax...") },
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanElectric,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("movie_story_input")
                )

                Text(
                    text = "Target Feature Runtime: ${durationMinutes.toInt()} minutes (15-120 min)",
                    style = MaterialTheme.typography.labelLarge,
                    color = CyanElectric
                )
                Slider(
                    value = durationMinutes,
                    onValueChange = { durationMinutes = it },
                    valueRange = 15f..120f,
                    steps = 20,
                    colors = SliderDefaults.colors(thumbColor = CyanElectric, activeTrackColor = CyanElectric)
                )

                Text("3D Anime Visual Style", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VisualStyle.entries.forEach { style ->
                        FilterChip(
                            selected = selectedStyle == style,
                            onClick = { selectedStyle = style },
                            label = { Text(style.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanElectric.copy(alpha = 0.2f),
                                selectedLabelColor = CyanElectric
                            )
                        )
                    }
                }

                Text("Primary Voice Language", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DialogueLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = selectedLang == lang,
                            onClick = { selectedLang = lang },
                            label = { Text(lang.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SakuraMagenta.copy(alpha = 0.2f),
                                selectedLabelColor = SakuraMagenta
                            )
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Aspect Ratio", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("16:9", "21:9", "9:16").forEach { ar ->
                                FilterChip(
                                    selected = aspectRatio == ar,
                                    onClick = { aspectRatio = ar },
                                    label = { Text(ar) }
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Opening Shots", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(4, 6, 8, 12).forEach { count ->
                                FilterChip(
                                    selected = sceneCount == count,
                                    onClick = { sceneCount = count },
                                    label = { Text("$count") }
                                )
                            }
                        }
                    }
                }

                if (characters.isNotEmpty()) {
                    Text("Consistent Cast Characters", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        characters.forEach { c ->
                            val checked = c.id in selectedCharIds
                            FilterChip(
                                selected = checked,
                                onClick = {
                                    if (checked) selectedCharIds.remove(c.id)
                                    else if (selectedCharIds.size < 8) selectedCharIds.add(c.id)
                                },
                                label = { Text(c.name) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreateMovie(
                        title.ifBlank { "Untitled Anime Feature" },
                        storyPrompt.ifBlank { "A lone cyber-samurai defends Neo-Kyoto under crimson rain." },
                        durationMinutes.toLong(),
                        selectedStyle,
                        selectedLang,
                        aspectRatio,
                        selectedCharIds.toList(),
                        sceneCount
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanElectric, contentColor = ObsidianCanvas),
                modifier = Modifier.testTag("confirm_story_to_movie_button")
            ) {
                Text("Generate Storyboard", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageToVideoDialog(
    characters: List<AnimeCharacter>,
    defaultLanguageCode: String,
    onDismiss: () -> Unit,
    onCreateImageToVideo: (
        imageUri: Uri,
        motionPrompt: String,
        dialogueText: String,
        languageCode: String,
        cameraMovementId: String,
        soundEffectId: String,
        bgmMoodId: String,
        speakerId: String
    ) -> Unit
) {
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var motionPrompt by remember { mutableStateOf("") }
    var dialogueText by remember { mutableStateOf("") }
    var selectedLang by remember { mutableStateOf(DialogueLanguage.fromCode(defaultLanguageCode)) }
    var selectedCamera by remember { mutableStateOf(CameraMovement.DOLLY_FORWARD) }
    var selectedSfx by remember { mutableStateOf(SoundEffectType.RAIN) }
    var selectedBgm by remember { mutableStateOf(BgmMood.EPIC_ORCHESTRAL) }
    var selectedSpeakerId by remember { mutableStateOf(characters.firstOrNull()?.id.orEmpty()) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pickedUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Text("Image / Character to Video", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    border = BorderStroke(1.dp, CyanElectric.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("pick_i2v_image_button")
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = CyanElectric)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (pickedUri != null) "Change Source Image" else "Pick Frame / Character Image", color = CyanElectric)
                }

                if (pickedUri != null) {
                    AsyncImage(
                        model = pickedUri,
                        contentDescription = "Source frame for Image-to-Video",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                OutlinedTextField(
                    value = motionPrompt,
                    onValueChange = { motionPrompt = it },
                    label = { Text("Motion & Action Direction") },
                    placeholder = { Text("e.g. Slow dolly push-in as wind blows her hair") },
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
                    label = { Text("Spoken Dialogue (Lip-Synced)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SakuraMagenta,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Language", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DialogueLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = selectedLang == lang,
                            onClick = { selectedLang = lang },
                            label = { Text(lang.label) }
                        )
                    }
                }

                Text("Camera Movement", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CameraMovement.entries.take(6).forEach { cam ->
                        FilterChip(
                            selected = selectedCamera == cam,
                            onClick = { selectedCamera = cam },
                            label = { Text(cam.shortTag) }
                        )
                    }
                }

                Text("Sound Effect", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SoundEffectType.entries.forEach { sfx ->
                        FilterChip(
                            selected = selectedSfx == sfx,
                            onClick = { selectedSfx = sfx },
                            label = { Text(sfx.label) }
                        )
                    }
                }

                if (characters.isNotEmpty()) {
                    Text("Speaker Voice Profile", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        characters.forEach { c ->
                            FilterChip(
                                selected = selectedSpeakerId == c.id,
                                onClick = { selectedSpeakerId = c.id },
                                label = { Text(c.name) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val uri = pickedUri ?: return@Button
                    onCreateImageToVideo(
                        uri,
                        motionPrompt,
                        dialogueText,
                        selectedLang.code,
                        selectedCamera.id,
                        selectedSfx.id,
                        selectedBgm.id,
                        selectedSpeakerId
                    )
                },
                enabled = pickedUri != null,
                colors = ButtonDefaults.buttonColors(containerColor = CyanElectric, contentColor = ObsidianCanvas),
                modifier = Modifier.testTag("confirm_i2v_button")
            ) {
                Text("Synthesize Video", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
