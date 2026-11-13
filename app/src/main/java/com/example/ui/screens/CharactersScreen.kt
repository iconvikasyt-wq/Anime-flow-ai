package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.domain.model.CharacterEmotion
import com.example.domain.model.DialogueLanguage
import com.example.ui.theme.CyanElectric
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
fun CharactersScreen(
    charactersState: UiState<List<AnimeCharacter>>,
    isCompact: Boolean,
    onCreateCharacter: (
        name: String,
        role: String,
        visualNotes: String,
        language: DialogueLanguage,
        pitch: Double,
        speed: Double,
        emotion: String,
        referenceImageUri: Uri?
    ) -> Unit,
    onDeleteCharacter: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }

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
                    text = "3D Character Bible",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Text(
                    text = "Upload reference images to lock faces, hair & outfits across every movie scene.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = CyanElectric, contentColor = ObsidianCanvas),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("add_character_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Cast", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (charactersState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Syncing Character Bible...", color = TextSecondary)
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(charactersState.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                val chars = charactersState.data
                if (chars.isEmpty()) {
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
                            Icon(Icons.Filled.Face, contentDescription = null, tint = CyanElectric, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No Consistent Characters Yet", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Upload a character reference photo or describe a 3D anime hero. Gemini Vision extracts their Visual DNA and locks their appearance across every shot.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showCreateDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanElectric, contentColor = ObsidianCanvas)
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create First 3D Character", fontWeight = FontWeight.Bold)
                            }
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
                        items(chars, key = { it.id }) { character ->
                            CharacterBibleCard(
                                character = character,
                                onDelete = { onDeleteCharacter(character.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateCharacterDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, role, notes, lang, pitch, speed, emotion, uri ->
                showCreateDialog = false
                onCreateCharacter(name, role, notes, lang, pitch, speed, emotion, uri)
            }
        )
    }
}

@Composable
private fun CharacterBibleCard(
    character: AnimeCharacter,
    onDelete: () -> Unit
) {
    val imgFile = remember(character.referenceImagePath) {
        character.referenceImagePath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                if (imgFile != null) {
                    AsyncImage(
                        model = imgFile,
                        contentDescription = "${character.name} 3D anime portrait",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Filled.Face, contentDescription = null, tint = CyanElectric, modifier = Modifier.size(34.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(character.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            text = "${character.role} • ${DialogueLanguage.fromCode(character.voiceLanguage).label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanElectric
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete ${character.name}",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = character.visualDna.ifBlank { "Standard 3D anime silhouette" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(6.dp), color = ObsidianSurfaceElevated) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = SakuraMagenta, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Pitch ${String.format("%.1f", character.voicePitch)}x • ${character.defaultEmotion}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SakuraMagenta
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateCharacterDialog(
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        role: String,
        visualNotes: String,
        language: DialogueLanguage,
        pitch: Double,
        speed: Double,
        emotion: String,
        referenceImageUri: Uri?
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Protagonist") }
    var visualNotes by remember { mutableStateOf("") }
    var selectedLang by remember { mutableStateOf(DialogueLanguage.EN) }
    var selectedEmotion by remember { mutableStateOf(CharacterEmotion.DETERMINED.label) }
    var pitch by remember { mutableFloatStateOf(1.0f) }
    var speed by remember { mutableFloatStateOf(1.0f) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pickedUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Text("Create Consistent 3D Character", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
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
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    border = BorderStroke(1.dp, CyanElectric.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("upload_character_ref_button")
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = CyanElectric)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (pickedUri != null) "Reference Image Attached (Tap to Replace)" else "Upload Reference Image (Photo Picker)",
                        color = CyanElectric
                    )
                }

                if (pickedUri != null) {
                    AsyncImage(
                        model = pickedUri,
                        contentDescription = "Selected character reference",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Character Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanElectric,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("character_name_input")
                )

                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("Role (Protagonist, Rival, Mentor...)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanElectric,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = visualNotes,
                    onValueChange = { visualNotes = it },
                    label = { Text("Visual DNA (Face, Hair, Outfit)") },
                    placeholder = { Text("e.g. Silver spiky hair, neon cyan visor, dark techwear haori") },
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanElectric,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("character_dna_input")
                )

                Text("Voice Acting Language", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogueLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = selectedLang == lang,
                            onClick = { selectedLang = lang },
                            label = { Text(lang.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanElectric.copy(alpha = 0.2f),
                                selectedLabelColor = CyanElectric
                            )
                        )
                    }
                }

                Text("Default Emotion", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CharacterEmotion.entries.forEach { emo ->
                        FilterChip(
                            selected = selectedEmotion == emo.label,
                            onClick = { selectedEmotion = emo.label },
                            label = { Text(emo.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SakuraMagenta.copy(alpha = 0.2f),
                                selectedLabelColor = SakuraMagenta
                            )
                        )
                    }
                }

                Text("Voice Pitch: ${String.format("%.2f", pitch)}x", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Slider(
                    value = pitch,
                    onValueChange = { pitch = it },
                    valueRange = 0.6f..1.6f,
                    colors = SliderDefaults.colors(thumbColor = CyanElectric, activeTrackColor = CyanElectric)
                )

                Text("Speaking Speed: ${String.format("%.2f", speed)}x", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    valueRange = 0.7f..1.5f,
                    colors = SliderDefaults.colors(thumbColor = SakuraMagenta, activeTrackColor = SakuraMagenta)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        name.ifBlank { "Aria Kurosawa" },
                        role.ifBlank { "Protagonist" },
                        visualNotes,
                        selectedLang,
                        pitch.toDouble(),
                        speed.toDouble(),
                        selectedEmotion,
                        pickedUri
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanElectric, contentColor = ObsidianCanvas),
                modifier = Modifier.testTag("confirm_create_character_button")
            ) {
                Text("Generate 3D Sheet", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
