package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.domain.model.AiModelCatalog
import com.example.domain.model.AnimeCharacter
import com.example.domain.model.BgmMood
import com.example.domain.model.CameraMovement
import com.example.domain.model.CharacterEmotion
import com.example.domain.model.DialogueLanguage
import com.example.domain.model.MovieProject
import com.example.domain.model.MovieScene
import com.example.domain.model.SoundEffectType
import com.example.domain.model.VisualStyle
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private const val TAG = "AnimeAiService"

data class GeneratedSceneDraft(
    val actNumber: Long,
    val title: String,
    val visualPrompt: String,
    val dialogueText: String,
    val dialogueLanguage: String,
    val speakerCharacterName: String,
    val characterNames: List<String>,
    val emotion: String,
    val cameraMovement: String,
    val soundEffect: String,
    val bgmMood: String,
    val durationSeconds: Long
)

class AnimeAiService(private val context: Context) {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private fun resolveGeminiStoryModel(modelId: String): String = when (modelId) {
        AiModelCatalog.GEMINI_3_5_FLASH_LITE.id -> "gemini-3.5-flash-lite"
        AiModelCatalog.GEMINI_4_ARGON.id -> "gemini-4-argon"
        else -> "gemini-3.8-flash"
    }

    private val storyboardSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "scenes" to Schema.array(
                    description = "Ordered list of cinematic 3D anime scenes for the movie",
                    items = Schema.obj(
                        properties = mapOf(
                            "actNumber" to Schema.integer(description = "Act number 1 to 6"),
                            "title" to Schema.string(description = "Concise evocative scene title"),
                            "visualPrompt" to Schema.string(description = "Detailed 3D anime visual frame prompt including lighting, environment, action, and consistent character appearance"),
                            "dialogueText" to Schema.string(description = "Spoken dialogue line in the requested target language"),
                            "speakerCharacterName" to Schema.string(description = "Name of the speaking character or Narrator"),
                            "characterNames" to Schema.array(items = Schema.string(), description = "Characters appearing in this scene"),
                            "emotion" to Schema.enumeration(CharacterEmotion.entries.map { it.label }),
                            "cameraMovement" to Schema.enumeration(CameraMovement.entries.map { it.id }),
                            "soundEffect" to Schema.enumeration(SoundEffectType.entries.map { it.id }),
                            "bgmMood" to Schema.enumeration(BgmMood.entries.map { it.id }),
                            "durationSeconds" to Schema.integer(description = "Clip duration in seconds between 4 and 12")
                        )
                    )
                )
            )
        )
    }

    /**
     * Breaks down a user's story or prompt into a structured multi-act anime movie storyboard.
     */
    suspend fun generateStoryToMovieScenes(
        storyPrompt: String,
        targetDurationMinutes: Long,
        visualStyle: VisualStyle,
        language: DialogueLanguage,
        characters: List<AnimeCharacter>,
        storyModelId: String,
        sceneCount: Int = 6
    ): List<GeneratedSceneDraft> {
        val modelName = resolveGeminiStoryModel(storyModelId)
        val model = Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = storyboardSchema
            }
        )

        val characterRoster = if (characters.isEmpty()) {
            "No pre-saved characters provided; invent 2 distinct, memorable 3D anime leads and keep their outfits, hair, and eye colors identical in every visualPrompt."
        } else {
            characters.joinToString("\n") { c ->
                "- ${c.name} (${c.role}): Visual DNA = ${c.visualDna.ifBlank { "3D anime hero in stylized attire" }}, Default Emotion = ${c.defaultEmotion}"
            }
        }

        val prompt = """
            You are an acclaimed 3D anime film director (inspired by Makoto Shinkai, Ufotable, and Studio Trigger).
            Create a cohesive $sceneCount-scene production storyboard representing key sequences across a ${targetDurationMinutes}-minute feature film.

            STORY / CONCEPT:
            $storyPrompt

            VISUAL STYLE:
            ${visualStyle.label} (${visualStyle.promptDirective})

            TARGET DIALOGUE LANGUAGE:
            ${language.label} (code: ${language.code}). Write ALL dialogueText naturally in ${language.label}.
            Keep visualPrompt in vivid English for image/video diffusion models.

            CHARACTER BIBLE (KEEP FACES, HAIRSTYLES, AND OUTFITS CONSISTENT):
            $characterRoster

            Rules:
            - Distribute scenes across Acts 1 through 4+ to establish beginning, escalation, climax, and resolution for the $targetDurationMinutes-minute arc.
            - Whenever a character appears in a scene, embed their exact visual attributes from the Character Bible into visualPrompt.
            - Vary cameraMovement, soundEffect (FIGHTING, WALKING, RAIN, EXPLOSION, NONE), and bgmMood dynamically.
            - Keep each dialogueText punchy and speakable within 4-8 seconds.
        """.trimIndent()

        val response = withTimeout(60_000L) {
            model.generateContent(prompt)
        }
        val rawJson = response.text ?: throw IllegalStateException("Screenwriter model returned an empty response.")
        val root = JSONObject(rawJson)
        val scenesArray = root.getJSONArray("scenes")
        return buildList {
            for (i in 0 until scenesArray.length()) {
                val obj = scenesArray.getJSONObject(i)
                val charsArr = obj.optJSONArray("characterNames") ?: JSONArray()
                val charNames = (0 until charsArr.length()).map { charsArr.getString(it) }
                add(
                    GeneratedSceneDraft(
                        actNumber = obj.optLong("actNumber", 1L).coerceIn(1L, 12L),
                        title = obj.optString("title", "Scene ${i + 1}").take(120),
                        visualPrompt = obj.optString("visualPrompt", storyPrompt).take(3000),
                        dialogueText = obj.optString("dialogueText", "").take(1000),
                        dialogueLanguage = language.code,
                        speakerCharacterName = obj.optString("speakerCharacterName", charNames.firstOrNull() ?: "Narrator"),
                        characterNames = charNames,
                        emotion = obj.optString("emotion", CharacterEmotion.DETERMINED.label),
                        cameraMovement = obj.optString("cameraMovement", CameraMovement.DOLLY_FORWARD.id),
                        soundEffect = obj.optString("soundEffect", SoundEffectType.NONE.id),
                        bgmMood = obj.optString("bgmMood", BgmMood.EPIC_ORCHESTRAL.id),
                        durationSeconds = obj.optLong("durationSeconds", 6L).coerceIn(4L, 12L)
                    )
                )
            }
        }
    }

    /**
     * Uses Gemini Vision to extract a locked Visual DNA description from a user-uploaded character reference photo.
     */
    suspend fun extractCharacterVisualDna(
        referenceBitmap: Bitmap,
        characterName: String,
        role: String
    ): String {
        val model = Firebase.ai.generativeModel("gemini-3.8-flash")
        val scaled = scaleBitmapDown(referenceBitmap, 1024)
        val response = withTimeout(60_000L) {
            model.generateContent(
                content {
                    image(scaled)
                    text(
                        """
                        Analyze this character reference image for a 3D anime film character named "$characterName" ($role).
                        Produce a single, dense, ultra-consistent Visual DNA prompt description (60-110 words) specifying:
                        1. Face shape, skin tone, eye color & shape, any scars/markings
                        2. Exact hairstyle, hair length, and hair color/highlights
                        3. Exact outfit layers, colors, fabric textures, armor/accessories, and footwear
                        4. Stylized 3D cel-shaded anime aesthetic cues.
                        Return only the visual description text, no markdown bullets.
                        """.trimIndent()
                    )
                }
            )
        }
        return response.text?.trim()?.take(1800)
            ?: throw IllegalStateException("Gemini Vision returned empty Visual DNA.")
    }

    /**
     * Extends an existing scene by writing the next continuous action beat with Gemini.
     */
    suspend fun generateContinuationScene(
        project: MovieProject,
        previousScene: MovieScene,
        characters: List<AnimeCharacter>
    ): GeneratedSceneDraft {
        val model = Firebase.ai.generativeModel(
            modelName = resolveGeminiStoryModel(project.storyModelId),
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = storyboardSchema
            }
        )
        val charInfo = characters.filter { it.id in previousScene.characterIds }
            .joinToString("\n") { "- ${it.name}: ${it.visualDna}" }
        val lang = DialogueLanguage.fromCode(previousScene.dialogueLanguage)
        val prompt = """
            Extend the following anime scene into 1 immediate continuation shot (seamless action/camera follow-through).
            Previous Scene "${previousScene.title}":
            Visual: ${previousScene.visualPrompt}
            Dialogue (${lang.label}): ${previousScene.dialogueText}
            Camera: ${previousScene.cameraMovement}
            Characters present:
            $charInfo

            Return exactly 1 scene in the JSON "scenes" array continuing the exact lighting, clothing, and story momentum, with dialogue in ${lang.label}.
        """.trimIndent()
        val response = withTimeout(60_000L) { model.generateContent(prompt) }
        val arr = JSONObject(response.text ?: error("Empty continuation response")).getJSONArray("scenes")
        val obj = arr.getJSONObject(0)
        val charsArr = obj.optJSONArray("characterNames") ?: JSONArray()
        return GeneratedSceneDraft(
            actNumber = previousScene.actNumber,
            title = obj.optString("title", "${previousScene.title} (Ext)").take(120),
            visualPrompt = obj.optString("visualPrompt", previousScene.visualPrompt).take(3000),
            dialogueText = obj.optString("dialogueText", "").take(1000),
            dialogueLanguage = previousScene.dialogueLanguage,
            speakerCharacterName = obj.optString("speakerCharacterName", ""),
            characterNames = (0 until charsArr.length()).map { charsArr.getString(it) },
            emotion = obj.optString("emotion", previousScene.emotion),
            cameraMovement = obj.optString("cameraMovement", CameraMovement.ORBIT_3D.id),
            soundEffect = obj.optString("soundEffect", previousScene.soundEffect),
            bgmMood = obj.optString("bgmMood", previousScene.bgmMood),
            durationSeconds = obj.optLong("durationSeconds", 6L).coerceIn(4L, 12L)
        )
    }

    /**
     * Generates a consistent 3D anime character portrait/sheet or scene keyframe image.
     * Supports free open-source Pollinations Flux Anime AND Firebase AI Logic Nano Banana models.
     */
    suspend fun generateImageAsset(
        prompt: String,
        imageModelId: String,
        aspectRatio: String,
        referenceBitmaps: List<Bitmap> = emptyList(),
        outputFileName: String
    ): File {
        val bitmap = when (imageModelId) {
            AiModelCatalog.GEMINI_NANO_BANANA_2_1.id,
            AiModelCatalog.GEMINI_3_PRO_IMAGE.id -> {
                generateWithFirebaseNanoBanana(
                    modelName = imageModelId,
                    prompt = prompt,
                    aspectRatio = aspectRatio,
                    referenceBitmaps = referenceBitmaps
                )
            }
            else -> {
                generateWithPollinationsFlux(prompt, aspectRatio)
            }
        }

        val outDir = File(context.filesDir, "animeflow/images").apply { mkdirs() }
        val outFile = File(outDir, outputFileName)
        withContext(Dispatchers.IO) {
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos)
            }
        }
        return outFile
    }

    private suspend fun generateWithFirebaseNanoBanana(
        modelName: String,
        prompt: String,
        aspectRatio: String,
        referenceBitmaps: List<Bitmap>
    ): Bitmap {
        val mappedAspect = when (aspectRatio) {
            "9:16" -> AspectRatio.PORTRAIT_9x16
            "1:1" -> AspectRatio.SQUARE_1x1
            else -> AspectRatio.LANDSCAPE_16x9
        }
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(mappedAspect)
                .build()
        }
        val model = Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = config
        )
        val response = withTimeout(60_000L) {
            if (referenceBitmaps.isNotEmpty()) {
                model.generateContent(
                    content {
                        referenceBitmaps.take(3).forEach { bmp ->
                            image(scaleBitmapDown(bmp, 768))
                        }
                        text("Using the attached character reference(s) strictly preserving face, hair, and outfit consistency: $prompt")
                    }
                )
            } else {
                model.generateContent(prompt)
            }
        }
        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()
        return imagePart?.image
            ?: throw IllegalStateException("$modelName returned no image. Note: Nano Banana image models require Firebase Blaze billing.")
    }

    private suspend fun generateWithPollinationsFlux(
        prompt: String,
        aspectRatio: String
    ): Bitmap = withContext(Dispatchers.IO) {
        val (w, h) = when (aspectRatio) {
            "9:16" -> 720 to 1280
            "1:1" -> 1024 to 1024
            "21:9" -> 1344 to 576
            else -> 1280 to 720
        }
        val encoded = URLEncoder.encode(prompt.take(900), "UTF-8")
        val seed = abs(prompt.hashCode()) % 999_999
        val url = "https://image.pollinations.ai/prompt/$encoded?model=flux-anime&width=$w&height=$h&nologo=true&seed=$seed"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AnimeFlow-Android/1.0")
            .build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException(
                    "Pollinations Flux Anime returned HTTP ${resp.code}. Open-access endpoints can throttle under heavy load — retry or switch image model."
                )
            }
            val bytes = resp.body?.bytes()
                ?: throw IllegalStateException("Empty image body from Pollinations Flux.")
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IllegalStateException("Could not decode generated image bytes.")
        }
    }

    /**
     * Calls Hugging Face Serverless Inference Router for real open-source Text-to-Video (Wan 2.1 / CogVideoX).
     */
    suspend fun generateVideoViaHuggingFace(
        modelId: String,
        prompt: String,
        hfToken: String,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        if (hfToken.isBlank()) {
            throw IllegalStateException(
                "Hugging Face free token required for $modelId. Add a free HF token in Models & Costs, or switch to the free AnimeFlow 3D Camera MP4 Engine."
            )
        }
        val payload = JSONObject().apply {
            put("inputs", prompt.take(800))
        }.toString()

        val request = Request.Builder()
            .url("https://router.huggingface.co/hf-inference/models/$modelId")
            .header("Authorization", "Bearer $hfToken")
            .header("Accept", "video/mp4")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body?.string().orEmpty().take(240)
                val hint = when (resp.code) {
                    401, 403 -> "Invalid or unauthorized Hugging Face token."
                    402 -> "Hugging Face free monthly inference credits exhausted."
                    503 -> "Model $modelId is cold-starting on Hugging Face. Try again in 30-60s."
                    else -> "Hugging Face API error HTTP ${resp.code}: $errBody"
                }
                throw IllegalStateException(hint)
            }
            val contentType = resp.header("Content-Type").orEmpty()
            val bytes = resp.body?.bytes() ?: throw IllegalStateException("Empty video response from Hugging Face.")
            if (!contentType.contains("video") && bytes.size < 4096) {
                throw IllegalStateException("Unexpected response from $modelId ($contentType): ${String(bytes).take(180)}")
            }
            val outDir = File(context.filesDir, "animeflow/videos").apply { mkdirs() }
            val outFile = File(outDir, outputFileName)
            FileOutputStream(outFile).use { it.write(bytes) }
            outFile
        }
    }

    fun buildConsistentScenePrompt(
        project: MovieProject,
        scene: MovieScene,
        characters: List<AnimeCharacter>
    ): String {
        val style = VisualStyle.fromId(project.visualStyle)
        val emotion = CharacterEmotion.fromLabel(scene.emotion)
        val participating = characters.filter { it.id in scene.characterIds }
        val charDnaBlock = if (participating.isNotEmpty()) {
            participating.joinToString("; ") { "${it.name}: ${it.visualDna}" }
        } else {
            ""
        }
        return listOf(
            style.promptDirective,
            scene.visualPrompt,
            if (charDnaBlock.isNotBlank()) "Consistent characters: $charDnaBlock" else "",
            "Character expression: ${emotion.promptCue}",
            "Camera framing: ${CameraMovement.fromId(scene.cameraMovement).label}"
        ).filter { it.isNotBlank() }.joinToString(". ")
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val newW = (w * ratio).toInt().coerceAtLeast(1)
        val newH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }
}
