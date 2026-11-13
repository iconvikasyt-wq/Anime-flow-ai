package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.media.ExportCapabilities
import com.example.data.media.ExportResolution
import com.example.domain.model.AiModelCatalog
import com.example.domain.model.AiModelSpec
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
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun ModelsAndCostsScreen(
    selectedStoryModelId: String,
    selectedImageModelId: String,
    selectedVideoModelId: String,
    exportResolutionId: String,
    exportCapabilities: ExportCapabilities,
    savedHfToken: String,
    onSelectModels: (storyId: String, imageId: String, videoId: String) -> Unit,
    onSelectExportResolution: (String) -> Unit,
    onSaveHfToken: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var storyId by remember(selectedStoryModelId) { mutableStateOf(selectedStoryModelId) }
    var imageId by remember(selectedImageModelId) { mutableStateOf(selectedImageModelId) }
    var videoId by remember(selectedVideoModelId) { mutableStateOf(selectedVideoModelId) }
    var hfTokenInput by remember(savedHfToken) { mutableStateOf(savedHfToken) }
    val userEmail = Firebase.auth.currentUser?.email ?: "Signed-in Creator"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "AI Models, Costs & Export Specs",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
            Text(
                text = "Choose free no-subscription models or upgrade to Blaze / Hugging Face open-source endpoints. All API quotas & device codec limits are shown transparently.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        // Export Resolution & Device MediaCodec Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VideoSettings, contentDescription = null, tint = CyanElectric)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hardware MP4 Export Resolution", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }
                    Text(
                        text = "Detected Encoder: ${exportCapabilities.encoderName} • Max ${exportCapabilities.maxWidth}x${exportCapabilities.maxHeight}",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanElectric
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ExportResolution.entries.forEach { res ->
                            val supported = when (res) {
                                ExportResolution.HD_720 -> true
                                ExportResolution.FHD_1080 -> exportCapabilities.supports1080p
                                ExportResolution.UHD_4K -> exportCapabilities.supports4k
                            }
                            val selected = exportResolutionId == res.id
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) CyanElectric.copy(alpha = 0.16f) else ObsidianSurfaceElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) CyanElectric else ObsidianBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = supported) { onSelectExportResolution(res.id) }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = res.id,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (supported) TextPrimary else TextSecondary.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = if (supported) "Supported" else "Unsupported",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (supported) EmeraldReady else AmberRender
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Screenwriting Models
        item {
            ModelGroupCard(
                title = "Story & Screenwriting AI",
                icon = Icons.Filled.Psychology,
                tint = CyanElectric,
                models = AiModelCatalog.storyModels(),
                selectedId = storyId,
                onSelect = {
                    storyId = it
                    onSelectModels(storyId, imageId, videoId)
                }
            )
        }

        // Image / Keyframe Models
        item {
            ModelGroupCard(
                title = "3D Anime Keyframe & Character Sheet AI",
                icon = Icons.Filled.Memory,
                tint = SakuraMagenta,
                models = AiModelCatalog.imageModels(),
                selectedId = imageId,
                onSelect = {
                    imageId = it
                    onSelectModels(storyId, imageId, videoId)
                }
            )
        }

        // Video Models
        item {
            ModelGroupCard(
                title = "Video Generation & Rendering Engine",
                icon = Icons.Filled.Movie,
                tint = EmeraldReady,
                models = AiModelCatalog.videoModels(),
                selectedId = videoId,
                onSelect = {
                    videoId = it
                    onSelectModels(storyId, imageId, videoId)
                }
            )
        }

        // Voice & Sound Engine Disclosure
        item {
            ModelGroupCard(
                title = "Voice Acting (EN / JA / HI) & Foley SFX",
                icon = Icons.Filled.RecordVoiceOver,
                tint = AmberRender,
                models = AiModelCatalog.voiceModels(),
                selectedId = AiModelCatalog.ANDROID_NEURAL_TTS_DSP.id,
                onSelect = {}
            )
        }

        // Optional Free Hugging Face Token Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Key, contentDescription = null, tint = AmberRender)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Optional Free Hugging Face Token", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }
                    Text(
                        text = "Required only if you select Wan 2.1 T2V or CogVideoX-2B. Free Hugging Face accounts include monthly serverless inference credits with no subscription. Stored only on-device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = hfTokenInput,
                        onValueChange = { hfTokenInput = it },
                        label = { Text("hf_... (Optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanElectric,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onSaveHfToken(hfTokenInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanElectric, contentColor = ObsidianCanvas),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("save_hf_token_button")
                    ) {
                        Text("Save Token Locally", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // API Quotas & Account Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = CyanElectric)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("API Limits & Creator Session", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }
                    Text(
                        text = "• Firebase AI Logic (Gemini 3.8 Flash / 3.5 Flash-Lite): Free tier included with App Check attestation.\n• Nano Banana Image Models & Gemini 4 Argon: Require Firebase Blaze pay-as-you-go.\n• Pollinations Flux Anime: Free open-access, queue-throttled if burst requested.\n• AnimeFlow 3D MP4 + Multilingual TTS + DSP SFX: 100% free & unlimited on-device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(userEmail, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                        OutlinedButton(
                            onClick = onSignOut,
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out", tint = Color(0xFFFCA5A5), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign Out", color = Color(0xFFFCA5A5))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModelGroupCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    models: List<AiModelSpec>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            models.forEach { spec ->
                val isSelected = spec.id == selectedId
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) tint.copy(alpha = 0.12f) else ObsidianSurfaceElevated,
                    border = BorderStroke(1.dp, if (isSelected) tint else ObsidianBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(spec.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(spec.id) },
                            colors = RadioButtonDefaults.colors(selectedColor = tint)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(spec.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                if (spec.isFreeWithoutSubscription) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = EmeraldReady.copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = "FREE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldReady,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(spec.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${spec.costSummary} • ${spec.rateLimitInfo}",
                                style = MaterialTheme.typography.labelSmall,
                                color = tint
                            )
                        }
                        if (isSelected) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = tint, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
