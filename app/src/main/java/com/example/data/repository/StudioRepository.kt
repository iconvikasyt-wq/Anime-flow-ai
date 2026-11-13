package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.firebase.OperationType
import com.example.core.firebase.handleFirestoreError
import com.example.domain.model.AnimeCharacter
import com.example.domain.model.MovieProject
import com.example.domain.model.MovieScene
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StudioRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun projectsCollection(uid: String) =
        db.collection("users").document(uid).collection("projects")

    private fun charactersCollection(uid: String) =
        db.collection("users").document(uid).collection("characters")

    private fun scenesCollection(uid: String, projectId: String) =
        projectsCollection(uid).document(projectId).collection("scenes")

    // --- PROJECTS ---

    fun observeProjects(): Flow<List<MovieProject>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/projects"
        emitAll(
            projectsCollection(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MovieProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun createProject(project: MovieProject): Result<String> {
        return try {
            val uid = requireUserId()
            val docRef = projectsCollection(uid).document()
            val payload = mapOf(
                "userId" to uid,
                "title" to project.title.trim().take(120).ifEmpty { "Untitled Anime Film" },
                "synopsis" to project.synopsis.take(4000),
                "visualStyle" to project.visualStyle,
                "targetDurationMinutes" to project.targetDurationMinutes.coerceIn(15L, 120L),
                "aspectRatio" to project.aspectRatio,
                "defaultLanguage" to project.defaultLanguage,
                "videoModelId" to project.videoModelId,
                "imageModelId" to project.imageModelId,
                "storyModelId" to project.storyModelId,
                "status" to project.status,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/${auth.currentUser?.uid}/projects", auth)
            Result.failure(e)
        }
    }

    suspend fun updateProject(project: MovieProject): Result<Unit> {
        return try {
            val uid = requireUserId()
            require(project.id.isNotBlank()) { "Project ID must not be blank for update." }
            val docRef = projectsCollection(uid).document(project.id)
            val updates = mapOf(
                "title" to project.title.trim().take(120).ifEmpty { "Untitled Anime Film" },
                "synopsis" to project.synopsis.take(4000),
                "visualStyle" to project.visualStyle,
                "targetDurationMinutes" to project.targetDurationMinutes.coerceIn(15L, 120L),
                "aspectRatio" to project.aspectRatio,
                "defaultLanguage" to project.defaultLanguage,
                "videoModelId" to project.videoModelId,
                "imageModelId" to project.imageModelId,
                "storyModelId" to project.storyModelId,
                "status" to project.status,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}/projects/${project.id}", auth)
            Result.failure(e)
        }
    }

    suspend fun deleteProject(projectId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val scenesSnap = scenesCollection(uid, projectId).get().await()
            val batch = db.batch()
            scenesSnap.documents.forEach { batch.delete(it.reference) }
            batch.delete(projectsCollection(uid).document(projectId))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/${auth.currentUser?.uid}/projects/$projectId", auth)
            Result.failure(e)
        }
    }

    // --- CHARACTERS ---

    fun observeCharacters(): Flow<List<AnimeCharacter>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/characters"
        emitAll(
            charactersCollection(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AnimeCharacter::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun createCharacter(character: AnimeCharacter): Result<String> {
        return try {
            val uid = requireUserId()
            val docRef = charactersCollection(uid).document()
            val payload = mapOf(
                "userId" to uid,
                "name" to character.name.trim().take(80).ifEmpty { "Unnamed Character" },
                "role" to character.role.take(60),
                "visualDna" to character.visualDna.take(2000),
                "voiceLanguage" to character.voiceLanguage,
                "voicePitch" to character.voicePitch.coerceIn(0.5, 2.0),
                "voiceSpeed" to character.voiceSpeed.coerceIn(0.5, 2.0),
                "defaultEmotion" to character.defaultEmotion.take(40),
                "referenceImagePath" to character.referenceImagePath.take(500),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/${auth.currentUser?.uid}/characters", auth)
            Result.failure(e)
        }
    }

    suspend fun updateCharacter(character: AnimeCharacter): Result<Unit> {
        return try {
            val uid = requireUserId()
            require(character.id.isNotBlank()) { "Character ID must not be blank for update." }
            val docRef = charactersCollection(uid).document(character.id)
            val updates = mapOf(
                "name" to character.name.trim().take(80).ifEmpty { "Unnamed Character" },
                "role" to character.role.take(60),
                "visualDna" to character.visualDna.take(2000),
                "voiceLanguage" to character.voiceLanguage,
                "voicePitch" to character.voicePitch.coerceIn(0.5, 2.0),
                "voiceSpeed" to character.voiceSpeed.coerceIn(0.5, 2.0),
                "defaultEmotion" to character.defaultEmotion.take(40),
                "referenceImagePath" to character.referenceImagePath.take(500),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}/characters/${character.id}", auth)
            Result.failure(e)
        }
    }

    suspend fun deleteCharacter(characterId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            charactersCollection(uid).document(characterId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/${auth.currentUser?.uid}/characters/$characterId", auth)
            Result.failure(e)
        }
    }

    // --- SCENES ---

    fun observeScenes(projectId: String): Flow<List<MovieScene>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/projects/$projectId/scenes"
        emitAll(
            scenesCollection(uid, projectId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MovieScene::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.orderIndex }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun createScenesBatch(projectId: String, scenes: List<MovieScene>): Result<List<String>> {
        return try {
            val uid = requireUserId()
            val batch = db.batch()
            val createdIds = mutableListOf<String>()
            scenes.forEach { scene ->
                val docRef = scenesCollection(uid, projectId).document()
                createdIds.add(docRef.id)
                batch.set(docRef, scenePayloadMap(uid, projectId, scene))
            }
            batch.commit().await()
            Result.success(createdIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/${auth.currentUser?.uid}/projects/$projectId/scenes", auth)
            Result.failure(e)
        }
    }

    suspend fun createScene(projectId: String, scene: MovieScene): Result<String> {
        return try {
            val uid = requireUserId()
            val docRef = scenesCollection(uid, projectId).document()
            docRef.set(scenePayloadMap(uid, projectId, scene)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/${auth.currentUser?.uid}/projects/$projectId/scenes", auth)
            Result.failure(e)
        }
    }

    suspend fun updateScene(projectId: String, scene: MovieScene): Result<Unit> {
        return try {
            val uid = requireUserId()
            require(scene.id.isNotBlank()) { "Scene ID must not be blank for update." }
            val docRef = scenesCollection(uid, projectId).document(scene.id)
            val updates = mapOf(
                "orderIndex" to scene.orderIndex.coerceIn(0L, 1000L),
                "actNumber" to scene.actNumber.coerceIn(1L, 12L),
                "title" to scene.title.trim().take(120).ifEmpty { "Scene" },
                "visualPrompt" to scene.visualPrompt.take(3000),
                "dialogueText" to scene.dialogueText.take(1000),
                "dialogueLanguage" to scene.dialogueLanguage,
                "speakerCharacterId" to scene.speakerCharacterId.take(128),
                "characterIds" to scene.characterIds.take(8),
                "emotion" to scene.emotion.take(40),
                "cameraMovement" to scene.cameraMovement.take(40),
                "soundEffect" to scene.soundEffect.take(40),
                "bgmMood" to scene.bgmMood.take(40),
                "durationSeconds" to scene.durationSeconds.coerceIn(3L, 120L),
                "keyframePath" to scene.keyframePath.take(500),
                "videoClipPath" to scene.videoClipPath.take(500),
                "audioPath" to scene.audioPath.take(500),
                "renderStatus" to scene.renderStatus,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}/projects/$projectId/scenes/${scene.id}", auth)
            Result.failure(e)
        }
    }

    suspend fun deleteScene(projectId: String, sceneId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            scenesCollection(uid, projectId).document(sceneId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/${auth.currentUser?.uid}/projects/$projectId/scenes/$sceneId", auth)
            Result.failure(e)
        }
    }

    private fun scenePayloadMap(uid: String, projectId: String, scene: MovieScene): Map<String, Any> = mapOf(
        "userId" to uid,
        "projectId" to projectId,
        "orderIndex" to scene.orderIndex.coerceIn(0L, 1000L),
        "actNumber" to scene.actNumber.coerceIn(1L, 12L),
        "title" to scene.title.trim().take(120).ifEmpty { "Scene" },
        "visualPrompt" to scene.visualPrompt.take(3000),
        "dialogueText" to scene.dialogueText.take(1000),
        "dialogueLanguage" to scene.dialogueLanguage,
        "speakerCharacterId" to scene.speakerCharacterId.take(128),
        "characterIds" to scene.characterIds.take(8),
        "emotion" to scene.emotion.take(40),
        "cameraMovement" to scene.cameraMovement.take(40),
        "soundEffect" to scene.soundEffect.take(40),
        "bgmMood" to scene.bgmMood.take(40),
        "durationSeconds" to scene.durationSeconds.coerceIn(3L, 120L),
        "keyframePath" to scene.keyframePath.take(500),
        "videoClipPath" to scene.videoClipPath.take(500),
        "audioPath" to scene.audioPath.take(500),
        "renderStatus" to scene.renderStatus,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
