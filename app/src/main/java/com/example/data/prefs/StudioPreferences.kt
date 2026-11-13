package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "animeflow_studio_prefs"
)

class StudioPreferences(private val context: Context) {

    private object Keys {
        val HF_TOKEN = stringPreferencesKey("hf_token")
        val EXPORT_RESOLUTION = stringPreferencesKey("export_resolution")
        val DEFAULT_STORY_MODEL = stringPreferencesKey("default_story_model")
        val DEFAULT_IMAGE_MODEL = stringPreferencesKey("default_image_model")
        val DEFAULT_VIDEO_MODEL = stringPreferencesKey("default_video_model")
    }

    val hfToken: Flow<String> = context.studioDataStore.data.map { it[Keys.HF_TOKEN].orEmpty() }

    val exportResolution: Flow<String> =
        context.studioDataStore.data.map { it[Keys.EXPORT_RESOLUTION] ?: "1080p" }

    val defaultStoryModel: Flow<String> =
        context.studioDataStore.data.map { it[Keys.DEFAULT_STORY_MODEL] ?: "gemini-3.8-flash" }

    val defaultImageModel: Flow<String> =
        context.studioDataStore.data.map { it[Keys.DEFAULT_IMAGE_MODEL] ?: "pollinations-flux-anime" }

    val defaultVideoModel: Flow<String> =
        context.studioDataStore.data.map { it[Keys.DEFAULT_VIDEO_MODEL] ?: "animeflow-motion-3d" }

    suspend fun setHfToken(token: String) {
        context.studioDataStore.edit { it[Keys.HF_TOKEN] = token.trim() }
    }

    suspend fun setExportResolution(resolution: String) {
        context.studioDataStore.edit { it[Keys.EXPORT_RESOLUTION] = resolution }
    }

    suspend fun setDefaultModels(storyId: String, imageId: String, videoId: String) {
        context.studioDataStore.edit {
            it[Keys.DEFAULT_STORY_MODEL] = storyId
            it[Keys.DEFAULT_IMAGE_MODEL] = imageId
            it[Keys.DEFAULT_VIDEO_MODEL] = videoId
        }
    }
}
