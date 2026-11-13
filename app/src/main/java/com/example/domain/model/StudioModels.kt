package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class MovieProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val synopsis: String = "",
    val visualStyle: String = VisualStyle.CEL_ANIME_3D.id,
    val targetDurationMinutes: Long = 30L,
    val aspectRatio: String = "16:9",
    val defaultLanguage: String = DialogueLanguage.EN.code,
    val videoModelId: String = AiModelCatalog.MOTION_3D_RENDERER.id,
    val imageModelId: String = AiModelCatalog.POLLINATIONS_FLUX_ANIME.id,
    val storyModelId: String = AiModelCatalog.GEMINI_3_8_FLASH.id,
    val status: String = "draft",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AnimeCharacter(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val role: String = "Protagonist",
    val visualDna: String = "",
    val voiceLanguage: String = DialogueLanguage.EN.code,
    val voicePitch: Double = 1.0,
    val voiceSpeed: Double = 1.0,
    val defaultEmotion: String = CharacterEmotion.DETERMINED.label,
    val referenceImagePath: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MovieScene(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val orderIndex: Long = 0L,
    val actNumber: Long = 1L,
    val title: String = "",
    val visualPrompt: String = "",
    val dialogueText: String = "",
    val dialogueLanguage: String = DialogueLanguage.EN.code,
    val speakerCharacterId: String = "",
    val characterIds: List<String> = emptyList(),
    val emotion: String = CharacterEmotion.DETERMINED.label,
    val cameraMovement: String = CameraMovement.DOLLY_FORWARD.id,
    val soundEffect: String = SoundEffectType.NONE.id,
    val bgmMood: String = BgmMood.EPIC_ORCHESTRAL.id,
    val durationSeconds: Long = 6L,
    val keyframePath: String = "",
    val videoClipPath: String = "",
    val audioPath: String = "",
    val renderStatus: String = "pending",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class VisualStyle(val id: String, val label: String, val promptDirective: String) {
    CEL_ANIME_3D(
        id = "3D_CEL_ANIME",
        label = "3D Cel-Shaded Anime",
        promptDirective = "3D cel-shaded anime cinema frame, Unreal Engine 5 anime shader, crisp ink outlines, dynamic volumetric rim lighting, Makoto Shinkai sky depth"
    ),
    CYBERPUNK_OVA(
        id = "CYBERPUNK_OVA",
        label = "Cyberpunk 3D OVA",
        promptDirective = "3D futuristic cyberpunk anime film frame, neon rain reflections, anamorphic lens flare, moody obsidian shadows, high-detail mecha and street fashion"
    ),
    GHIBLI_PAINTERLY(
        id = "GHIBLI_PAINTERLY",
        label = "Painterly Fantasy 3D",
        promptDirective = "lush 3D painterly anime cinema, hand-painted gouache clouds, warm golden hour sunlight, expressive character animation"
    ),
    SHONEN_ACTION_3D(
        id = "SHONEN_ACTION_3D",
        label = "Shonen Sakuga 3D",
        promptDirective = "high-octane 3D shonen anime sakuga frame, dramatic foreshortening, energy particle trails, sharp cel shadows, Ufotable-inspired compositing"
    );

    companion object {
        fun fromId(id: String): VisualStyle = entries.firstOrNull { it.id == id } ?: CEL_ANIME_3D
    }
}

enum class DialogueLanguage(val code: String, val label: String, val localeTag: String) {
    EN("EN", "English", "en-US"),
    JA("JA", "Japanese (日本語)", "ja-JP"),
    HI("HI", "Hindi (हिन्दी)", "hi-IN");

    companion object {
        fun fromCode(code: String): DialogueLanguage = entries.firstOrNull { it.code == code } ?: EN
    }
}

enum class CharacterEmotion(val label: String, val pitchOffset: Float, val speedMultiplier: Float, val promptCue: String) {
    DETERMINED("Determined", 0.0f, 1.0f, "focused intense eyes, resolute expression"),
    CALM("Calm", -0.06f, 0.92f, "serene composed posture, gentle gaze"),
    FURIOUS("Furious", 0.12f, 1.15f, "fierce battle glare, dramatic wind-swept hair, clenched jaw"),
    JOYFUL("Joyful", 0.14f, 1.08f, "bright anime smile, sparkling highlights in eyes"),
    MELANCHOLY("Melancholy", -0.12f, 0.85f, "downcast reflective eyes, soft backlit shadows"),
    SHOCKED("Shocked", 0.18f, 1.18f, "wide dilated eyes, dramatic speed-lines framing face");

    companion object {
        fun fromLabel(label: String): CharacterEmotion =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: DETERMINED
    }
}

enum class CameraMovement(val id: String, val label: String, val shortTag: String) {
    STATIC("STATIC", "Locked Tripod", "LOCK"),
    DOLLY_FORWARD("DOLLY_FORWARD", "3D Dolly Push-In", "DOLLY IN"),
    ZOOM_IN("ZOOM_IN", "Dramatic Zoom In", "ZOOM IN"),
    ZOOM_OUT("ZOOM_OUT", "Reveal Pull-Out", "PULL OUT"),
    PAN_LEFT("PAN_LEFT", "Cinema Pan Left", "PAN L"),
    PAN_RIGHT("PAN_RIGHT", "Cinema Pan Right", "PAN R"),
    ORBIT_3D("ORBIT_3D", "3D Parallax Orbit", "ORBIT"),
    CRANE_UP("CRANE_UP", "Skyline Crane Up", "CRANE UP"),
    HANDHELD_ACTION("HANDHELD_ACTION", "Sakuga Action Shake", "ACTION");

    companion object {
        fun fromId(id: String): CameraMovement = entries.firstOrNull { it.id == id } ?: DOLLY_FORWARD
    }
}

enum class SoundEffectType(val id: String, val label: String) {
    NONE("NONE", "No SFX"),
    FIGHTING("FIGHTING", "Sword & Combat SFX"),
    WALKING("WALKING", "Footsteps SFX"),
    RAIN("RAIN", "Heavy Rain & Thunder"),
    EXPLOSION("EXPLOSION", "Cinematic Explosion");

    companion object {
        fun fromId(id: String): SoundEffectType = entries.firstOrNull { it.id == id } ?: NONE
    }
}

enum class BgmMood(val id: String, val label: String) {
    NONE("NONE", "Silence"),
    EPIC_ORCHESTRAL("EPIC_ORCHESTRAL", "Epic Anime Score"),
    LOFI_ANIME("LOFI_ANIME", "Emotional Piano"),
    CYBER_SYNTH("CYBER_SYNTH", "Cyberpunk Synth"),
    SUSPENSE("SUSPENSE", "Dark Tension Drone");

    companion object {
        fun fromId(id: String): BgmMood = entries.firstOrNull { it.id == id } ?: EPIC_ORCHESTRAL
    }
}

enum class ModelCategory { STORY, IMAGE, VIDEO, VOICE }

data class AiModelSpec(
    val id: String,
    val displayName: String,
    val category: ModelCategory,
    val provider: String,
    val isFreeWithoutSubscription: Boolean,
    val requiresBlaze: Boolean,
    val requiresOptionalHfToken: Boolean,
    val rateLimitInfo: String,
    val costSummary: String,
    val description: String
)

object AiModelCatalog {
    // Story & Screenwriting Models (Firebase AI Logic)
    val GEMINI_3_8_FLASH = AiModelSpec(
        id = "gemini-3.8-flash",
        displayName = "Gemini 3.8 Flash",
        category = ModelCategory.STORY,
        provider = "Firebase AI Logic",
        isFreeWithoutSubscription = true,
        requiresBlaze = false,
        requiresOptionalHfToken = false,
        rateLimitInfo = "Free tier included (Spark); rate-limited RPM",
        costSummary = "$0 Free Tier (No Blaze billing required)",
        description = "Default frontier screenwriter for 15-120 min anime multi-act breakdowns, character DNA extraction, and EN/JA/HI dialogue."
    )
    val GEMINI_3_5_FLASH_LITE = AiModelSpec(
        id = "gemini-3.5-flash-lite",
        displayName = "Gemini 3.5 Flash-Lite",
        category = ModelCategory.STORY,
        provider = "Firebase AI Logic",
        isFreeWithoutSubscription = true,
        requiresBlaze = false,
        requiresOptionalHfToken = false,
        rateLimitInfo = "High-throughput free tier",
        costSummary = "$0 Free Tier (Ultra-fast, lowest cost)",
        description = "Ultra-low latency scene expansions, rapid storyboard variations, and instant dialogue translations."
    )
    val GEMINI_4_ARGON = AiModelSpec(
        id = "gemini-4-argon",
        displayName = "Gemini 4 Argon",
        category = ModelCategory.STORY,
        provider = "Firebase AI Logic",
        isFreeWithoutSubscription = false,
        requiresBlaze = true,
        requiresOptionalHfToken = false,
        rateLimitInfo = "Tiered RPM based on Firebase Blaze usage",
        costSummary = "Pay-as-you-go (Requires Firebase Blaze plan)",
        description = "Deep reasoning model for intricate 90-120 minute multi-character continuity and complex plot arcs."
    )

    // Keyframe & Character Sheet Image Models
    val POLLINATIONS_FLUX_ANIME = AiModelSpec(
        id = "pollinations-flux-anime",
        displayName = "Flux Anime Open (Pollinations)",
        category = ModelCategory.IMAGE,
        provider = "Open-Source Flux Endpoint",
        isFreeWithoutSubscription = true,
        requiresBlaze = false,
        requiresOptionalHfToken = false,
        rateLimitInfo = "Free open-access (~1 req / 5s queue limit)",
        costSummary = "$0 Free & Open (No subscription or API key)",
        description = "Free open-source Flux image generator tuned for crisp 3D cel-shaded anime keyframes and character concepts."
    )
    val GEMINI_NANO_BANANA_2_1 = AiModelSpec(
        id = "gemini-nano-banana-2.1",
        displayName = "Gemini Nano Banana 2.1",
        category = ModelCategory.IMAGE,
        provider = "Firebase AI Logic",
        isFreeWithoutSubscription = false,
        requiresBlaze = true,
        requiresOptionalHfToken = false,
        rateLimitInfo = "Pay-as-you-go Blaze tier; supports reference Image+Text",
        costSummary = "Blaze Pay-as-you-go (~$0.03-0.04/frame)",
        description = "Multimodal character-consistent image generation & editing with uploaded reference photos."
    )
    val GEMINI_3_PRO_IMAGE = AiModelSpec(
        id = "gemini-3-pro-image",
        displayName = "Gemini 3 Pro Image (Nano Banana Pro)",
        category = ModelCategory.IMAGE,
        provider = "Firebase AI Logic",
        isFreeWithoutSubscription = false,
        requiresBlaze = true,
        requiresOptionalHfToken = false,
        rateLimitInfo = "Pay-as-you-go Blaze tier; 1K/2K/4K output",
        costSummary = "Blaze Pay-as-you-go (Studio Pro asset tier)",
        description = "Studio-grade 3D anime keyframe synthesis and multi-character scene composition."
    )

    // Video Synthesis Models
    val MOTION_3D_RENDERER = AiModelSpec(
        id = "animeflow-motion-3d",
        displayName = "AnimeFlow 3D Camera + LipSync MP4 Engine",
        category = ModelCategory.VIDEO,
        provider = "On-Device MediaCodec + Media3",
        isFreeWithoutSubscription = true,
        requiresBlaze = false,
        requiresOptionalHfToken = false,
        rateLimitInfo = "Unlimited, 0 external API quota, works offline",
        costSummary = "$0 Free Forever (Hardware-accelerated MP4)",
        description = "Synthesizes real 1080p/4K MP4 clips from AI keyframes with 3D parallax camera motion, weather/action particles, lip-sync & muxed WAV audio."
    )
    val HF_WAN_2_1 = AiModelSpec(
        id = "Wan-AI/Wan2.1-T2V-1.3B",
        displayName = "Wan 2.1 T2V 1.3B (Open-Source)",
        category = ModelCategory.VIDEO,
        provider = "Hugging Face Serverless",
        isFreeWithoutSubscription = true,
        requiresBlaze = false,
        requiresOptionalHfToken = true,
        rateLimitInfo = "HF free monthly inference credits (cold-start 30-90s)",
        costSummary = "$0 Free HF Token Tier (No paid subscription)",
        description = "Open-source diffusion Text-to-Video model via Hugging Face Inference Router. Add a free HF token in Models & Costs."
    )
    val HF_COGVIDEOX_2B = AiModelSpec(
        id = "THUDM/CogVideoX-2b",
        displayName = "CogVideoX-2B (Open-Source)",
        category = ModelCategory.VIDEO,
        provider = "Hugging Face Serverless",
        isFreeWithoutSubscription = true,
        requiresBlaze = false,
        requiresOptionalHfToken = true,
        rateLimitInfo = "HF free monthly inference credits (queued when busy)",
        costSummary = "$0 Free HF Token Tier (No paid subscription)",
        description = "Open-source transformer video synthesis via Hugging Face Inference Router."
    )

    // Voice & Sound Models
    val ANDROID_NEURAL_TTS_DSP = AiModelSpec(
        id = "android-tts-dsp-mixer",
        displayName = "Multilingual Voice + Foley DSP Mixer",
        category = ModelCategory.VOICE,
        provider = "Android TTS + Native PCM Mixer",
        isFreeWithoutSubscription = true,
        requiresBlaze = false,
        requiresOptionalHfToken = false,
        rateLimitInfo = "Unlimited; supports EN, JA, HI with pitch/emotion",
        costSummary = "$0 Free Forever (Synthesizes real WAV + LipSync)",
        description = "Synthesizes spoken English, Japanese & Hindi voice acting mixed with procedural fighting, walking, rain, explosion SFX & BGM."
    )

    val allModels = listOf(
        GEMINI_3_8_FLASH,
        GEMINI_3_5_FLASH_LITE,
        GEMINI_4_ARGON,
        POLLINATIONS_FLUX_ANIME,
        GEMINI_NANO_BANANA_2_1,
        GEMINI_3_PRO_IMAGE,
        MOTION_3D_RENDERER,
        HF_WAN_2_1,
        HF_COGVIDEOX_2B,
        ANDROID_NEURAL_TTS_DSP
    )

    fun storyModels() = allModels.filter { it.category == ModelCategory.STORY }
    fun imageModels() = allModels.filter { it.category == ModelCategory.IMAGE }
    fun videoModels() = allModels.filter { it.category == ModelCategory.VIDEO }
    fun voiceModels() = allModels.filter { it.category == ModelCategory.VOICE }
    fun findById(id: String): AiModelSpec? = allModels.firstOrNull { it.id == id }
}
