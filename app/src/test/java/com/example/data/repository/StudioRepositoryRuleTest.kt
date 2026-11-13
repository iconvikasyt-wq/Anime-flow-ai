package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.domain.model.AnimeCharacter
import com.example.domain.model.MovieProject
import com.example.domain.model.MovieScene
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndObserveProject_authenticatedOwner_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = StudioRepository(firestore, auth)

        val projectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createProject(
                MovieProject(
                    title = "Neon Ronin 3099",
                    synopsis = "A cyber-samurai protects the orbital sakura shrine.",
                    targetDurationMinutes = 60L
                )
            ).getOrThrow()
        }
        assertTrue(projectId.isNotEmpty())

        val projects = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProjects().first { list -> list.any { it.id == projectId } }
        }
        assertEquals("Neon Ronin 3099", projects.first { it.id == projectId }.title)
    }

    @Test
    fun createCharacterAndScene_authenticatedOwner_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = StudioRepository(firestore, auth)

        val charId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createCharacter(
                AnimeCharacter(
                    name = "Kaito Vance",
                    role = "Protagonist",
                    visualDna = "Silver undercut hair, amber eyes, black tactical coat"
                )
            ).getOrThrow()
        }

        val projectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createProject(
                MovieProject(title = "Eclipse Arc", targetDurationMinutes = 30L)
            ).getOrThrow()
        }

        val sceneId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createScene(
                projectId,
                MovieScene(
                    orderIndex = 0L,
                    actNumber = 1L,
                    title = "Rooftop Rain",
                    visualPrompt = "3D anime cyberpunk rooftop in pouring rain",
                    dialogueText = "The signal starts tonight.",
                    dialogueLanguage = "EN",
                    speakerCharacterId = charId,
                    characterIds = listOf(charId)
                )
            ).getOrThrow()
        }

        val scenes = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeScenes(projectId).first { list -> list.any { it.id == sceneId } }
        }
        assertEquals(1, scenes.count { it.id == sceneId })
    }

    @Test
    fun crossUserProjectRead_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = StudioRepository(firestore, auth)
        val projectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createProject(
                MovieProject(title = "Alice Secret Film", targetDurationMinutes = 25L)
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        var denied = false
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("projects").document(projectId).get().await()
            }
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
            denied = true
        }
        assertTrue("Expected PERMISSION_DENIED on cross-user read", denied)
    }

    @Test
    fun unauthenticatedQuery_failsImmediately() = runBlocking {
        auth.signOut()
        val aliceDbPath = "users/some_user/projects"
        var denied = false
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection(aliceDbPath).get().await()
            }
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
            denied = true
        }
        assertTrue("Expected PERMISSION_DENIED when unauthenticated", denied)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_animeflow@test.com"
        const val BOB_EMAIL = "bob_animeflow@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
