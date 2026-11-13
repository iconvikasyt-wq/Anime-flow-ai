package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.ai.AnimeAiService
import com.example.data.media.AnimeVideoRenderEngine
import com.example.data.media.ExportCapabilities
import com.example.data.media.ExportResolution
import com.example.data.media.VoiceAndFoleyEngine
import com.example.data.prefs.StudioPreferences
import com.example.data.repository.StudioRepository
import com.example.domain.model.AiModelCatalog
import com.example.domain.model.AnimeCharacter
import com.example.domain.model.DialogueLanguage
import com.example.domain.model.MovieProject
import com.example.domain.model.MovieScene
import com.example.domain.model.VisualStyle
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private const val TAG = "StudioViewModel"

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface GenerationTaskState {
    data object Idle : GenerationTaskState
    data class Running(
        val stage: String,
        val detail: String,
        val progress: Float
    ) : GenerationTaskState
    data class Completed(
        val message: String,
        val exportedUri: Uri? = null
    ) : GenerationTaskState
    data class Failed(val error: String) : GenerationTaskState
}

@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModel(
    private val appContext: Context,
    private val repository: StudioRepository,
    private val aiService: AnimeAiService,
    private val voiceEngine: VoiceAndFoleyEngine,
    private val videoEngine: AnimeVideoRenderEngine,
    private val preferences: StudioPreferences,
    private val currentUserId: String
) : ViewModel() {

    val exportCapabilities: ExportCapabilities = videoEngine.detectExportCapabilities()

    val projectsState: StateFlow<UiState<List<MovieProject>>> = repository.observeProjects()
        .map<List<MovieProject>, UiState<List<MovieProject>>> { list ->
            if (_selectedProjectId.value == null && list.isNotEmpty()) {
                _selectedProjectId.value = list.first().id
            }
            UiState.Success(list)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing projects", root)
            emit(UiState.Error(root.message ?: "Failed to load movie projects."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val charactersState: StateFlow<UiState<List<AnimeCharacter>>> = repository.observeCharacters()
        .map<List<AnimeCharacter>, UiState<List<AnimeCharacter>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing characters", root)
            emit(UiState.Error(root.message ?: "Failed to load characters."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _selectedProjectId = MutableStateFlow<String?>(null)
    val selectedProjectId: StateFlow<String?> = _selectedProjectId.asStateFlow()

    val scenesState: StateFlow<List<MovieScene>> = _selectedProjectId
        .flatMapLatest { pid ->
            if (pid.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeScenes(pid)
                    .catch { error ->
                        Log.w(TAG, "Error observing scenes for $pid", error)
                        emit(emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _selectedSceneId = MutableStateFlow<String?>(null)
    val selectedSceneId: StateFlow<String?> = _selectedSceneId.asStateFlow()

    private val _taskState = MutableStateFlow<GenerationTaskState>(GenerationTaskState.Idle)
    val taskState: StateFlow<GenerationTaskState> = _taskState.asStateFlow()

    private val _lipSyncMap = MutableStateFlow<Map<String, List<Float>>>(emptyMap())
    val lipSyncMap: StateFlow<Map<String, List<Float>>> = _lipSyncMap.asStateFlow()

    private val _mergedMoviePath = MutableStateFlow<String?>(null)
    val mergedMoviePath: StateFlow<String?> = _mergedMoviePath.asStateFlow()

    val hfToken = preferences.hfToken.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")
    val exportResolution = preferences.exportResolution.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "1080p")
    val defaultStoryModel = preferences.defaultStoryModel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AiModelCatalog.GEMINI_3_8_FLASH.id)
    val defaultImageModel = preferences.defaultImageModel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AiModelCatalog.POLLINATIONS_FLUX_ANIME.id)
    val defaultVideoModel = preferences.defaultVideoModel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AiModelCatalog.MOTION_3D_RENDERER.id)

    fun selectProject(projectId: String) {
        _selectedProjectId.value = projectId
        _selectedSceneId.value = null
        _mergedMoviePath.value = null
    }

    fun selectScene(sceneId: String) {
        _selectedSceneId.value = sceneId
    }

    fun clearTaskStatus() {
        _taskState.value = GenerationTaskState.Idle
    }

    fun saveHfToken(token: String) {
        viewModelScope.launch {
            preferences.setHfToken(token)
            _taskState.value = GenerationTaskState.Completed("Hugging Face token saved locally.")
        }
    }

    fun setExportResolution(resId: String) {
        viewModelScope.launch {
            preferences.setExportResolution(resId)
        }
    }

    fun setDefaultModels(storyId: String, imageId: String, videoId: String) {
        viewModelScope.launch {
            preferences.setDefaultModels(storyId, imageId, videoId)
            val currentProject = (projectsState.value as? UiState.Success)?.data
                ?.firstOrNull { it.id == _selectedProjectId.value }
            if (currentProject != null) {
                repository.updateProject(
                    currentProject.copy(
                        storyModelId = storyId,
                        imageModelId = imageId,
                        videoModelId = videoId
                    )
                )
            }
            _taskState.value = GenerationTaskState.Completed("AI model pipeline updated.")
        }
    }

    /**
     * Generates a complete Story-to-Movie project (15-120 min) using Gemini and renders Scene 1 immediately.
     */
    fun createStoryToMovieProject(
        title: String,
        storyPrompt: String,
        targetDurationMinutes: Long,
        visualStyle: VisualStyle,
        language: DialogueLanguage,
        aspectRatio: String,
        selectedCharacterIds: List<String>,
        sceneCount: Int
    ) {
        viewModelScope.launch {
            try {
                _taskState.value = GenerationTaskState.Running(
                    stage = "Screenplay AI (${defaultStoryModel.value})",
                    detail = "Breaking ${targetDurationMinutes}-minute story into $sceneCount 3D anime scenes...",
                    progress = 0.2f
                )

                val allChars = (charactersState.value as? UiState.Success)?.data.orEmpty()
                val chosenChars = if (selectedCharacterIds.isNotEmpty()) {
                    allChars.filter { it.id in selectedCharacterIds }
                } else {
                    allChars.take(4)
                }

                val storyModel = defaultStoryModel.value
                val imageModel = defaultImageModel.value
                val videoModel = defaultVideoModel.value

                val project = MovieProject(
                    userId = currentUserId,
                    title = title.ifBlank { "Untitled Anime Film" },
                    synopsis = storyPrompt,
                    visualStyle = visualStyle.id,
                    targetDurationMinutes = targetDurationMinutes.coerceIn(15L, 120L),
                    aspectRatio = aspectRatio,
                    defaultLanguage = language.code,
                    videoModelId = videoModel,
                    imageModelId = imageModel,
                    storyModelId = storyModel,
                    status = "generating"
                )
                val projectId = repository.createProject(project).getOrThrow()
                _selectedProjectId.value = projectId

                val drafts = aiService.generateStoryToMovieScenes(
                    storyPrompt = storyPrompt,
                    targetDurationMinutes = targetDurationMinutes,
                    visualStyle = visualStyle,
                    language = language,
                    characters = chosenChars,
                    storyModelId = storyModel,
                    sceneCount = sceneCount
                )

                val sceneModels = drafts.mapIndexed { idx, draft ->
                    val matchedIds = chosenChars.filter { c ->
                        draft.characterNames.any { it.contains(c.name, ignoreCase = true) || c.name.contains(it, ignoreCase = true) }
                    }.map { it.id }.ifEmpty { chosenChars.take(2).map { it.id } }

                    val speakerId = chosenChars.firstOrNull {
                        draft.speakerCharacterName.contains(it.name, ignoreCase = true)
                    }?.id ?: matchedIds.firstOrNull().orEmpty()

                    MovieScene(
                        userId = currentUserId,
                        projectId = projectId,
                        orderIndex = idx.toLong(),
                        actNumber = draft.actNumber,
                        title = draft.title,
                        visualPrompt = draft.visualPrompt,
                        dialogueText = draft.dialogueText,
                        dialogueLanguage = draft.dialogueLanguage,
                        speakerCharacterId = speakerId,
                        characterIds = matchedIds.take(8),
                        emotion = draft.emotion,
                        cameraMovement = draft.cameraMovement,
                        soundEffect = draft.soundEffect,
                        bgmMood = draft.bgmMood,
                        durationSeconds = draft.durationSeconds,
                        renderStatus = "pending"
                    )
                }

                val createdSceneIds = repository.createScenesBatch(projectId, sceneModels).getOrThrow()
                val firstSceneId = createdSceneIds.firstOrNull()
                if (firstSceneId != null) {
                    _selectedSceneId.value = firstSceneId
                    val firstScene = sceneModels.first().copy(id = firstSceneId)
                    val savedProject = project.copy(id = projectId)
                    renderSceneInternal(savedProject, firstScene, chosenChars)
                }
                repository.updateProject(project.copy(id = projectId, status = "ready"))
                _taskState.value = GenerationTaskState.Completed(
                    "Created ${sceneModels.size}-scene storyboard & rendered opening clip."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Story-to-Movie generation failed", e)
                _taskState.value = GenerationTaskState.Failed(e.localizedMessage ?: "Story generation failed.")
            }
        }
    }

    /**
     * Image-to-Video / Character-to-Video quick scene creation from Photo Picker URI.
     */
    fun createImageToVideoScene(
        project: MovieProject,
        imageUri: Uri,
        motionPrompt: String,
        dialogueText: String,
        languageCode: String,
        cameraMovementId: String,
        soundEffectId: String,
        bgmMoodId: String,
        speakerId: String
    ) {
        viewModelScope.launch {
            try {
                _taskState.value = GenerationTaskState.Running("Importing Reference Frame", "Processing selected image...", 0.25f)
                val localImageFile = copyUriToLocalPng(imageUri, "i2v_${UUID.randomUUID()}.png")
                val currentScenes = scenesState.value
                val nextIndex = (currentScenes.maxOfOrNull { it.orderIndex } ?: -1L) + 1L
                val scene = MovieScene(
                    userId = currentUserId,
                    projectId = project.id,
                    orderIndex = nextIndex,
                    actNumber = (nextIndex / 2L + 1L).coerceIn(1L, 12L),
                    title = motionPrompt.take(48).ifBlank { "Image-to-Video Shot ${nextIndex + 1}" },
                    visualPrompt = motionPrompt.ifBlank { "Animate reference frame with 3D anime depth and lighting" },
                    dialogueText = dialogueText,
                    dialogueLanguage = languageCode,
                    speakerCharacterId = speakerId,
                    characterIds = if (speakerId.isNotBlank()) listOf(speakerId) else emptyList(),
                    cameraMovement = cameraMovementId,
                    soundEffect = soundEffectId,
                    bgmMood = bgmMoodId,
                    durationSeconds = 6L,
                    keyframePath = localImageFile.absolutePath,
                    renderStatus = "keyframe_ready"
                )
                val sceneId = repository.createScene(project.id, scene).getOrThrow()
                _selectedSceneId.value = sceneId
                val allChars = (charactersState.value as? UiState.Success)?.data.orEmpty()
                renderSceneInternal(project, scene.copy(id = sceneId), allChars, reuseExistingKeyframe = true)
                _taskState.value = GenerationTaskState.Completed("Image-to-Video clip synthesized!")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image-to-Video failed", e)
                _taskState.value = GenerationTaskState.Failed(e.localizedMessage ?: "Image-to-Video failed.")
            }
        }
    }

    /**
     * Uploads a character reference photo, extracts locked Visual DNA via Gemini Vision,
     * and generates a consistent 3D anime character sheet.
     */
    fun createConsistentCharacter(
        name: String,
        role: String,
        userVisualNotes: String,
        voiceLanguage: DialogueLanguage,
        voicePitch: Double,
        voiceSpeed: Double,
        defaultEmotion: String,
        referenceImageUri: Uri?
    ) {
        viewModelScope.launch {
            try {
                var refBitmap: Bitmap? = null
                var finalVisualDna = userVisualNotes.trim()

                if (referenceImageUri != null) {
                    _taskState.value = GenerationTaskState.Running(
                        stage = "Gemini Vision Analysis",
                        detail = "Extracting locked face, hair & outfit Visual DNA from reference image...",
                        progress = 0.3f
                    )
                    val copied = copyUriToLocalPng(referenceImageUri, "char_ref_${UUID.randomUUID()}.png")
                    refBitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(copied.absolutePath) }
                    if (refBitmap != null) {
                        val extractedDna = aiService.extractCharacterVisualDna(refBitmap, name, role)
                        finalVisualDna = if (finalVisualDna.isNotBlank()) {
                            "$extractedDna. Additional notes: $finalVisualDna"
                        } else {
                            extractedDna
                        }
                    }
                }

                _taskState.value = GenerationTaskState.Running(
                    stage = "3D Character Sheet",
                    detail = "Generating consistent 3D anime character portrait...",
                    progress = 0.65f
                )

                val imageModel = defaultImageModel.value
                val sheetPrompt = "3D cel-shaded anime character turnaround portrait, crisp studio lighting, dark obsidian backdrop, $name ($role), $finalVisualDna"
                val sheetFile = aiService.generateImageAsset(
                    prompt = sheetPrompt,
                    imageModelId = imageModel,
                    aspectRatio = "1:1",
                    referenceBitmaps = listOfNotNull(refBitmap),
                    outputFileName = "char_sheet_${UUID.randomUUID()}.png"
                )
                refBitmap?.recycle()

                val character = AnimeCharacter(
                    userId = currentUserId,
                    name = name,
                    role = role,
                    visualDna = finalVisualDna,
                    voiceLanguage = voiceLanguage.code,
                    voicePitch = voicePitch,
                    voiceSpeed = voiceSpeed,
                    defaultEmotion = defaultEmotion,
                    referenceImagePath = sheetFile.absolutePath
                )
                repository.createCharacter(character).getOrThrow()
                _taskState.value = GenerationTaskState.Completed("Character '$name' created with locked Visual DNA.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Character creation failed", e)
                _taskState.value = GenerationTaskState.Failed(e.localizedMessage ?: "Character creation failed.")
            }
        }
    }

    fun renderScene(project: MovieProject, scene: MovieScene) {
        viewModelScope.launch {
            try {
                val allChars = (charactersState.value as? UiState.Success)?.data.orEmpty()
                renderSceneInternal(project, scene, allChars, reuseExistingKeyframe = false)
                _taskState.value = GenerationTaskState.Completed("Scene '${scene.title}' rendered to MP4.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Scene render failed", e)
                repository.updateScene(project.id, scene.copy(renderStatus = "error"))
                _taskState.value = GenerationTaskState.Failed(e.localizedMessage ?: "Scene rendering failed.")
            }
        }
    }

    fun updateSceneEdits(projectId: String, updatedScene: MovieScene) {
        viewModelScope.launch {
            repository.updateScene(projectId, updatedScene)
                .onFailure { e ->
                    _taskState.value = GenerationTaskState.Failed(e.localizedMessage ?: "Failed to save scene changes.")
                }
        }
    }

    fun extendScene(project: MovieProject, scene: MovieScene) {
        viewModelScope.launch {
            try {
                _taskState.value = GenerationTaskState.Running("Extend Video AI", "Writing seamless continuation shot...", 0.35f)
                val allChars = (charactersState.value as? UiState.Success)?.data.orEmpty()
                val draft = aiService.generateContinuationScene(project, scene, allChars)

                // Shift subsequent scenes forward
                val currentScenes = scenesState.value.sortedBy { it.orderIndex }
                currentScenes.filter { it.orderIndex > scene.orderIndex }.forEach { later ->
                    repository.updateScene(project.id, later.copy(orderIndex = later.orderIndex + 1L))
                }

                val newScene = MovieScene(
                    userId = currentUserId,
                    projectId = project.id,
                    orderIndex = scene.orderIndex + 1L,
                    actNumber = draft.actNumber,
                    title = draft.title,
                    visualPrompt = draft.visualPrompt,
                    dialogueText = draft.dialogueText,
                    dialogueLanguage = draft.dialogueLanguage,
                    speakerCharacterId = scene.speakerCharacterId,
                    characterIds = scene.characterIds,
                    emotion = draft.emotion,
                    cameraMovement = draft.cameraMovement,
                    soundEffect = draft.soundEffect,
                    bgmMood = draft.bgmMood,
                    durationSeconds = draft.durationSeconds,
                    renderStatus = "pending"
                )
                val newId = repository.createScene(project.id, newScene).getOrThrow()
                _selectedSceneId.value = newId
                renderSceneInternal(project, newScene.copy(id = newId), allChars)
                _taskState.value = GenerationTaskState.Completed("Extended '${scene.title}' into next shot.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Extend scene failed", e)
                _taskState.value = GenerationTaskState.Failed(e.localizedMessage ?: "Failed to extend scene.")
            }
        }
    }

    fun renderAllAndMergeMovie(project: MovieProject) {
        viewModelScope.launch {
            try {
                val scenes = scenesState.value.sortedBy { it.orderIndex }
                if (scenes.isEmpty()) {
                    _taskState.value = GenerationTaskState.Failed("No scenes in this project yet.")
                    return@launch
                }
                val allChars = (charactersState.value as? UiState.Success)?.data.orEmpty()
                val clipFiles = mutableListOf<File>()

                scenes.forEachIndexed { idx, scene ->
                    val existingClip = File(scene.videoClipPath)
                    if (scene.renderStatus == "video_ready" && existingClip.exists() && existingClip.length() > 1024) {
                        clipFiles.add(existingClip)
                    } else {
                        _taskState.value = GenerationTaskState.Running(
                            stage = "Batch Rendering (${idx + 1}/${scenes.size})",
                            detail = "Synthesizing '${scene.title}'...",
                            progress = (idx.toFloat() / scenes.size.toFloat()) * 0.85f
                        )
                        val rendered = renderSceneInternal(project, scene, allChars)
                        clipFiles.add(rendered)
                    }
                }

                _taskState.value = GenerationTaskState.Running(
                    stage = "Merging Timeline",
                    detail = "Joining ${clipFiles.size} clips into continuous MP4 feature...",
                    progress = 0.92f
                )
                val merged = videoEngine.mergeClipsToMovieMp4(
                    clipFiles = clipFiles,
                    outputFileName = "movie_${project.id}_${System.currentTimeMillis()}.mp4"
                )
                _mergedMoviePath.value = merged.absolutePath
                _taskState.value = GenerationTaskState.Completed("Merged ${clipFiles.size} scenes into full MP4 preview!")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Batch render & merge failed", e)
                _taskState.value = GenerationTaskState.Failed(e.localizedMessage ?: "Merge failed.")
            }
        }
    }

    fun exportMovieToGallery(project: MovieProject) {
        viewModelScope.launch {
            try {
                val scenes = scenesState.value.sortedBy { it.orderIndex }
                val readyFiles = scenes.map { File(it.videoClipPath) }.filter { it.exists() && it.length() > 1024 }
                val sourceMp4 = _mergedMoviePath.value?.let(::File)?.takeIf { it.exists() }
                    ?: if (readyFiles.isNotEmpty()) {
                        _taskState.value = GenerationTaskState.Running("Merging Clips", "Preparing MP4 export...", 0.5f)
                        videoEngine.mergeClipsToMovieMp4(readyFiles, "export_${project.id}.mp4").also {
                            _mergedMoviePath.value = it.absolutePath
                        }
                    } else {
                        _taskState.value = GenerationTaskState.Failed("Render at least one scene clip before exporting.")
                        return@launch
                    }

                _taskState.value = GenerationTaskState.Running("Exporting MP4", "Saving to Movies/AnimeFlow...", 0.85f)
                val uri = videoEngine.exportToGallery(sourceMp4, project.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_"))
                _taskState.value = GenerationTaskState.Completed(
                    message = "Exported '${project.title}.mp4' to Movies/AnimeFlow!",
                    exportedUri = uri
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                _taskState.value = GenerationTaskState.Failed(e.localizedMessage ?: "Gallery export failed.")
            }
        }
    }

    fun deleteScene(projectId: String, sceneId: String) {
        viewModelScope.launch {
            repository.deleteScene(projectId, sceneId)
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            if (_selectedProjectId.value == projectId) {
                _selectedProjectId.value = null
                _selectedSceneId.value = null
            }
        }
    }

    fun deleteCharacter(characterId: String) {
        viewModelScope.launch {
            repository.deleteCharacter(characterId)
        }
    }

    private suspend fun renderSceneInternal(
        project: MovieProject,
        scene: MovieScene,
        allCharacters: List<AnimeCharacter>,
        reuseExistingKeyframe: Boolean = false
    ): File {
        val speaker = allCharacters.firstOrNull { it.id == scene.speakerCharacterId }
            ?: allCharacters.firstOrNull { it.id in scene.characterIds }
        val fullPrompt = aiService.buildConsistentScenePrompt(project, scene, allCharacters)

        // 1. Keyframe image generation
        val existingKf = File(scene.keyframePath)
        val keyframeFile = if (reuseExistingKeyframe && existingKf.exists() && existingKf.length() > 512) {
            existingKf
        } else {
            _taskState.value = GenerationTaskState.Running(
                stage = "Keyframe AI (${project.imageModelId})",
                detail = "Rendering 3D anime frame for '${scene.title}'...",
                progress = 0.25f
            )
            val refBitmaps = allCharacters.filter { it.id in scene.characterIds && it.referenceImagePath.isNotBlank() }
                .mapNotNull { c ->
                    val f = File(c.referenceImagePath)
                    if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
                }
            val kf = aiService.generateImageAsset(
                prompt = fullPrompt,
                imageModelId = project.imageModelId,
                aspectRatio = project.aspectRatio,
                referenceBitmaps = refBitmaps,
                outputFileName = "kf_${scene.id}_${System.currentTimeMillis()}.png"
            )
            refBitmaps.forEach { it.recycle() }
            repository.updateScene(
                project.id,
                scene.copy(keyframePath = kf.absolutePath, renderStatus = "keyframe_ready")
            )
            kf
        }

        // 2. Multilingual Voice + Lip-Sync + Foley SFX + BGM synthesis
        _taskState.value = GenerationTaskState.Running(
            stage = "Voice & Foley Mixer",
            detail = "Synthesizing ${DialogueLanguage.fromCode(scene.dialogueLanguage).label} voice + ${scene.soundEffect} SFX...",
            progress = 0.55f
        )
        val audioResult = voiceEngine.synthesizeSceneAudio(
            sceneId = scene.id,
            dialogueText = scene.dialogueText,
            languageCode = scene.dialogueLanguage,
            emotionLabel = scene.emotion,
            basePitch = (speaker?.voicePitch ?: 1.0).toFloat(),
            baseSpeed = (speaker?.voiceSpeed ?: 1.0).toFloat(),
            soundEffectId = scene.soundEffect,
            bgmMoodId = scene.bgmMood,
            targetDurationSec = scene.durationSeconds.toInt()
        )
        _lipSyncMap.value = _lipSyncMap.value + (scene.id to audioResult.lipSyncEnvelope)

        // 3. Video MP4 Synthesis (HF Open-Source Video OR On-Device 1080p/4K MediaCodec Engine)
        val resChoice = ExportResolution.fromId(exportResolution.value).let { desired ->
            when {
                desired == ExportResolution.UHD_4K && !exportCapabilities.supports4k ->
                    if (exportCapabilities.supports1080p) ExportResolution.FHD_1080 else ExportResolution.HD_720
                desired == ExportResolution.FHD_1080 && !exportCapabilities.supports1080p ->
                    ExportResolution.HD_720
                else -> desired
            }
        }

        _taskState.value = GenerationTaskState.Running(
            stage = "Video Synthesis (${project.videoModelId})",
            detail = "Encoding ${resChoice.label} MP4 with ${scene.cameraMovement} & lip-sync...",
            progress = 0.80f
        )

        val mp4File = if (project.videoModelId == AiModelCatalog.HF_WAN_2_1.id ||
            project.videoModelId == AiModelCatalog.HF_COGVIDEOX_2B.id
        ) {
            aiService.generateVideoViaHuggingFace(
                modelId = project.videoModelId,
                prompt = fullPrompt,
                hfToken = preferences.hfToken.first(),
                outputFileName = "hf_${scene.id}_${System.currentTimeMillis()}.mp4"
            )
        } else {
            videoEngine.renderSceneClipMp4(
                sceneId = scene.id,
                keyframeFile = keyframeFile,
                wavAudioFile = audioResult.wavFile,
                aspectRatio = project.aspectRatio,
                resolution = resChoice,
                cameraMovementId = scene.cameraMovement,
                soundEffectId = scene.soundEffect,
                dialogueText = scene.dialogueText,
                speakerName = speaker?.name.orEmpty(),
                lipSyncEnvelope = audioResult.lipSyncEnvelope,
                durationSec = (audioResult.durationMs / 1000L).toInt().coerceIn(4, 15)
            )
        }

        repository.updateScene(
            project.id,
            scene.copy(
                keyframePath = keyframeFile.absolutePath,
                audioPath = audioResult.wavFile.absolutePath,
                videoClipPath = mp4File.absolutePath,
                renderStatus = "video_ready"
            )
        )
        return mp4File
    }

    private suspend fun copyUriToLocalPng(uri: Uri, fileName: String): File = withContext(Dispatchers.IO) {
        val dir = File(appContext.filesDir, "animeflow/uploads").apply { mkdirs() }
        val target = File(dir, fileName)
        val bmp = appContext.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: throw IllegalStateException("Could not read selected image.")
        FileOutputStream(target).use { fos ->
            bmp.compress(Bitmap.CompressFormat.PNG, 95, fos)
        }
        bmp.recycle()
        target
    }

    companion object {
        fun provideFactory(context: Context, currentUserId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val appCtx = context.applicationContext
                    val dbId = appCtx.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(dbId)
                    StudioViewModel(
                        appContext = appCtx,
                        repository = StudioRepository(db),
                        aiService = AnimeAiService(appCtx),
                        voiceEngine = VoiceAndFoleyEngine(appCtx),
                        videoEngine = AnimeVideoRenderEngine(appCtx),
                        preferences = StudioPreferences(appCtx),
                        currentUserId = currentUserId
                    )
                }
            }
    }
}
