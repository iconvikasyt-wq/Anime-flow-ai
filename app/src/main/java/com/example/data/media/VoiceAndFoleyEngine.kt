package com.example.data.media

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.domain.model.BgmMood
import com.example.domain.model.CharacterEmotion
import com.example.domain.model.DialogueLanguage
import com.example.domain.model.SoundEffectType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val TAG = "VoiceAndFoleyEngine"
private const val SAMPLE_RATE = 44100

data class SynthesizedAudioResult(
    val wavFile: File,
    val lipSyncEnvelope: List<Float>, // 30 values per second (0f..1f)
    val durationMs: Long,
    val ttsNotice: String? = null
)

class VoiceAndFoleyEngine(private val context: Context) {

    /**
     * Synthesizes spoken dialogue (English, Japanese, or Hindi) via Android TextToSpeech,
     * mixes in procedural SFX (Fighting, Walking, Rain, Explosion) and BGM,
     * writes a standard 16-bit 44.1kHz PCM WAV file, and returns the 30fps lip-sync envelope.
     */
    suspend fun synthesizeSceneAudio(
        sceneId: String,
        dialogueText: String,
        languageCode: String,
        emotionLabel: String,
        basePitch: Float,
        baseSpeed: Float,
        soundEffectId: String,
        bgmMoodId: String,
        targetDurationSec: Int
    ): SynthesizedAudioResult = withContext(Dispatchers.IO) {
        val outDir = File(context.filesDir, "animeflow/audio").apply { mkdirs() }
        val rawTtsFile = File(outDir, "${sceneId}_tts_raw.wav")
        val finalWavFile = File(outDir, "${sceneId}_master.wav")

        val lang = DialogueLanguage.fromCode(languageCode)
        val emotion = CharacterEmotion.fromLabel(emotionLabel)
        val effectivePitch = (basePitch + emotion.pitchOffset).coerceIn(0.55f, 1.85f)
        val effectiveSpeed = (baseSpeed * emotion.speedMultiplier).coerceIn(0.6f, 1.75f)

        var ttsNotice: String? = null
        var speechPcm = ShortArray(0)

        if (dialogueText.isNotBlank()) {
            val ttsOk = synthesizeTtsToWav(
                text = dialogueText,
                locale = Locale.forLanguageTag(lang.localeTag),
                pitch = effectivePitch,
                speed = effectiveSpeed,
                outputFile = rawTtsFile
            )
            if (ttsOk && rawTtsFile.exists() && rawTtsFile.length() > 44) {
                speechPcm = readWavAs44100Mono(rawTtsFile)
            } else {
                ttsNotice = "${lang.label} TTS voice pack unavailable on this device; rendered SFX & BGM track."
            }
            rawTtsFile.delete()
        }

        val minSamples = targetDurationSec.coerceIn(3, 60) * SAMPLE_RATE
        val totalSamples = max(minSamples, speechPcm.size + SAMPLE_RATE / 2)
        val mixBuffer = FloatArray(totalSamples)

        // 1. Place dialogue starting at 0.35s
        val speechOffset = (0.35f * SAMPLE_RATE).toInt().coerceAtMost(max(0, totalSamples - speechPcm.size))
        for (i in speechPcm.indices) {
            val idx = speechOffset + i
            if (idx < totalSamples) {
                mixBuffer[idx] += (speechPcm[i] / 32768f) * 0.92f
            }
        }

        // 2. Compute 30fps lip-sync envelope strictly from spoken dialogue waveform
        val lipSyncEnvelope = computeLipSyncEnvelope(
            speechPcm = speechPcm,
            speechOffsetSamples = speechOffset,
            totalSamples = totalSamples,
            fps = 30
        )

        // 3. Layer BGM (ducked during dialogue)
        val bgmMood = BgmMood.fromId(bgmMoodId)
        if (bgmMood != BgmMood.NONE) {
            mixBgm(mixBuffer, bgmMood, speechOffset, speechPcm.size)
        }

        // 4. Layer Procedural Sound Effects (Fighting, Walking, Rain, Explosion)
        val sfxType = SoundEffectType.fromId(soundEffectId)
        if (sfxType != SoundEffectType.NONE) {
            mixSoundEffect(mixBuffer, sfxType)
        }

        // 5. Soft-clip & write 16-bit PCM WAV
        val outShorts = ShortArray(totalSamples) { i ->
            val v = mixBuffer[i].coerceIn(-0.98f, 0.98f)
            (v * 32767f).toInt().toShort()
        }
        writeWavFile(finalWavFile, outShorts, SAMPLE_RATE)

        SynthesizedAudioResult(
            wavFile = finalWavFile,
            lipSyncEnvelope = lipSyncEnvelope,
            durationMs = (totalSamples * 1000L) / SAMPLE_RATE,
            ttsNotice = ttsNotice
        )
    }

    private suspend fun synthesizeTtsToWav(
        text: String,
        locale: Locale,
        pitch: Float,
        speed: Float,
        outputFile: File
    ): Boolean {
        return withTimeoutOrNull(18_000L) {
            suspendCancellableCoroutine { cont ->
                var ttsRef: TextToSpeech? = null
                val listener = TextToSpeech.OnInitListener { status ->
                    val tts = ttsRef
                    if (status != TextToSpeech.SUCCESS || tts == null) {
                        tts?.shutdown()
                        if (cont.isActive) cont.resume(false)
                        return@OnInitListener
                    }
                    val langResult = tts.setLanguage(locale)
                    if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                        langResult == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        Log.w(TAG, "TTS locale $locale missing/unsupported (code=$langResult)")
                    }
                    tts.setPitch(pitch)
                    tts.setSpeechRate(speed)
                    val utteranceId = UUID.randomUUID().toString()
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(uid: String?) {}
                        override fun onDone(uid: String?) {
                            tts.shutdown()
                            if (cont.isActive) cont.resume(true)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(uid: String?) {
                            tts.shutdown()
                            if (cont.isActive) cont.resume(false)
                        }

                        override fun onError(uid: String?, errorCode: Int) {
                            tts.shutdown()
                            if (cont.isActive) cont.resume(false)
                        }
                    })
                    val res = tts.synthesizeToFile(text, Bundle(), outputFile, utteranceId)
                    if (res != TextToSpeech.SUCCESS) {
                        tts.shutdown()
                        if (cont.isActive) cont.resume(false)
                    }
                }
                ttsRef = TextToSpeech(context.applicationContext, listener)
                cont.invokeOnCancellation {
                    try {
                        ttsRef?.stop()
                        ttsRef?.shutdown()
                    } catch (_: Exception) {
                    }
                }
            }
        } ?: false
    }

    private fun computeLipSyncEnvelope(
        speechPcm: ShortArray,
        speechOffsetSamples: Int,
        totalSamples: Int,
        fps: Int
    ): List<Float> {
        val samplesPerFrame = SAMPLE_RATE / fps
        val frameCount = max(1, totalSamples / samplesPerFrame)
        val env = ArrayList<Float>(frameCount)
        for (f in 0 until frameCount) {
            val start = f * samplesPerFrame
            val end = min(totalSamples, start + samplesPerFrame)
            var sumSq = 0.0
            var count = 0
            for (s in start until end) {
                val speechIdx = s - speechOffsetSamples
                if (speechIdx in speechPcm.indices) {
                    val norm = speechPcm[speechIdx] / 32768.0
                    sumSq += norm * norm
                    count++
                }
            }
            val rms = if (count > 0) sqrt(sumSq / count).toFloat() else 0f
            env.add((rms * 4.2f).coerceIn(0f, 1f))
        }
        return env
    }

    private fun mixBgm(
        buffer: FloatArray,
        mood: BgmMood,
        speechOffset: Int,
        speechLen: Int
    ) {
        val chords: List<FloatArray> = when (mood) {
            BgmMood.EPIC_ORCHESTRAL -> listOf(
                floatArrayOf(146.83f, 220.00f, 293.66f, 349.23f), // Dm7
                floatArrayOf(116.54f, 174.61f, 233.08f, 293.66f), // Bbmaj7
                floatArrayOf(130.81f, 196.00f, 261.63f, 329.63f), // C6
                floatArrayOf(146.83f, 220.00f, 277.18f, 329.63f)  // A/D tension
            )
            BgmMood.LOFI_ANIME -> listOf(
                floatArrayOf(261.63f, 329.63f, 392.00f, 493.88f), // Cmaj7
                floatArrayOf(220.00f, 261.63f, 329.63f, 392.00f), // Am7
                floatArrayOf(174.61f, 220.00f, 261.63f, 329.63f), // Fmaj7
                floatArrayOf(196.00f, 246.94f, 293.66f, 349.23f)  // G7
            )
            BgmMood.CYBER_SYNTH -> listOf(
                floatArrayOf(110.00f, 164.81f, 220.00f, 261.63f),
                floatArrayOf(98.00f, 146.83f, 196.00f, 246.94f),
                floatArrayOf(87.31f, 130.81f, 174.61f, 220.00f),
                floatArrayOf(82.41f, 123.47f, 164.81f, 207.65f)
            )
            BgmMood.SUSPENSE -> listOf(
                floatArrayOf(73.42f, 110.00f, 155.56f),
                floatArrayOf(69.30f, 103.83f, 146.83f)
            )
            BgmMood.NONE -> return
        }
        val chordDurationSamples = SAMPLE_RATE * 2
        for (i in buffer.indices) {
            val t = i.toFloat() / SAMPLE_RATE
            val chordIdx = (i / chordDurationSamples) % chords.size
            val notes = chords[chordIdx]
            var sample = 0f
            for (n in notes.indices) {
                val freq = notes[n]
                val harmonic = if (mood == BgmMood.CYBER_SYNTH) {
                    0.6f * sin(2f * PI.toFloat() * freq * t) + 0.25f * sin(4f * PI.toFloat() * freq * t)
                } else {
                    sin(2f * PI.toFloat() * freq * t)
                }
                sample += harmonic / notes.size
            }
            val posInChord = (i % chordDurationSamples).toFloat() / chordDurationSamples
            val env = sin(PI.toFloat() * posInChord).coerceIn(0.15f, 1f)
            val isSpeaking = speechLen > 0 && i in speechOffset until (speechOffset + speechLen)
            val gain = if (isSpeaking) 0.08f else 0.19f
            buffer[i] += sample * env * gain
        }
    }

    private fun mixSoundEffect(buffer: FloatArray, sfx: SoundEffectType) {
        val rng = Random(sfx.id.hashCode())
        when (sfx) {
            SoundEffectType.RAIN -> {
                var lp = 0f
                for (i in buffer.indices) {
                    val white = rng.nextFloat() * 2f - 1f
                    lp = lp * 0.86f + white * 0.14f
                    val t = i.toFloat() / SAMPLE_RATE
                    val thunder = if (i > SAMPLE_RATE && i < SAMPLE_RATE * 3) {
                        val dt = (i - SAMPLE_RATE).toFloat() / SAMPLE_RATE
                        sin(2f * PI.toFloat() * 48f * t) * exp(-dt * 1.4f) * 0.24f
                    } else 0f
                    buffer[i] += lp * 0.22f + thunder
                }
            }
            SoundEffectType.FIGHTING -> {
                // Rhythmic sword clashes & whooshes every ~0.85s
                val hitInterval = (SAMPLE_RATE * 0.85f).toInt()
                for (hitStart in (SAMPLE_RATE / 4) until buffer.size step hitInterval) {
                    val len = min(SAMPLE_RATE / 2, buffer.size - hitStart)
                    for (j in 0 until len) {
                        val dt = j.toFloat() / SAMPLE_RATE
                        val env = exp(-dt * 14f)
                        val clang = sin(2f * PI.toFloat() * 1480f * dt + 2.5f * sin(2f * PI.toFloat() * 410f * dt))
                        val impact = (rng.nextFloat() * 2f - 1f) * exp(-dt * 28f)
                        buffer[hitStart + j] += (clang * 0.28f + impact * 0.35f) * env
                    }
                }
            }
            SoundEffectType.WALKING -> {
                val stepInterval = (SAMPLE_RATE * 0.52f).toInt()
                for (stepStart in (SAMPLE_RATE / 5) until buffer.size step stepInterval) {
                    val len = min(SAMPLE_RATE / 6, buffer.size - stepStart)
                    for (j in 0 until len) {
                        val dt = j.toFloat() / SAMPLE_RATE
                        val env = exp(-dt * 32f)
                        val thud = sin(2f * PI.toFloat() * (115f - dt * 180f).coerceAtLeast(45f) * dt)
                        val grit = (rng.nextFloat() * 2f - 1f) * 0.3f
                        buffer[stepStart + j] += (thud * 0.42f + grit) * env
                    }
                }
            }
            SoundEffectType.EXPLOSION -> {
                val boomStart = (SAMPLE_RATE * 0.6f).toInt().coerceAtMost(buffer.size / 2)
                var lp = 0f
                for (j in 0 until (buffer.size - boomStart)) {
                    val dt = j.toFloat() / SAMPLE_RATE
                    val env = exp(-dt * 2.1f)
                    val white = rng.nextFloat() * 2f - 1f
                    lp = lp * 0.92f + white * 0.08f
                    val sub = sin(2f * PI.toFloat() * (65f * exp(-dt * 1.2f) + 28f) * dt)
                    buffer[boomStart + j] += (sub * 0.45f + lp * 0.55f) * env
                }
            }
            SoundEffectType.NONE -> Unit
        }
    }

    private fun readWavAs44100Mono(file: File): ShortArray {
        val bytes = FileInputStream(file).use { it.readBytes() }
        if (bytes.size <= 44) return ShortArray(0)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val channels = bb.getShort(22).toInt().coerceAtLeast(1)
        val srcRate = bb.getInt(24).coerceAtLeast(8000)
        val bitsPerSample = bb.getShort(34).toInt().coerceAtLeast(16)

        // Find 'data' chunk robustly
        var offset = 12
        var dataOffset = 44
        var dataSize = bytes.size - 44
        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = bb.getInt(offset + 4)
            if (chunkId == "data") {
                dataOffset = offset + 8
                dataSize = min(chunkSize, bytes.size - dataOffset)
                break
            }
            offset += 8 + max(0, chunkSize)
        }

        if (bitsPerSample != 16 || dataSize <= 2) return ShortArray(0)
        val frameSize = channels * 2
        val srcFrames = dataSize / frameSize
        val mono = ShortArray(srcFrames)
        for (i in 0 until srcFrames) {
            var acc = 0
            for (ch in 0 until channels) {
                acc += bb.getShort(dataOffset + i * frameSize + ch * 2).toInt()
            }
            mono[i] = (acc / channels).toShort()
        }

        if (srcRate == SAMPLE_RATE) return mono
        val ratio = srcRate.toDouble() / SAMPLE_RATE.toDouble()
        val targetLen = max(1, (srcFrames / ratio).toInt())
        return ShortArray(targetLen) { i ->
            val srcPos = (i * ratio).toInt().coerceIn(0, srcFrames - 1)
            mono[srcPos]
        }
    }

    private fun writeWavFile(file: File, samples: ShortArray, sampleRate: Int) {
        val byteData = ByteArray(samples.size * 2)
        val bb = ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { bb.putShort(it) }

        val totalDataLen = byteData.size + 36
        val byteRate = sampleRate * 2
        val header = ByteArrayOutputStream(44).apply {
            write("RIFF".toByteArray())
            write(intToLittleEndian(totalDataLen))
            write("WAVE".toByteArray())
            write("fmt ".toByteArray())
            write(intToLittleEndian(16)) // PCM
            write(shortToLittleEndian(1)) // Linear PCM
            write(shortToLittleEndian(1)) // Mono
            write(intToLittleEndian(sampleRate))
            write(intToLittleEndian(byteRate))
            write(shortToLittleEndian(2)) // Block align
            write(shortToLittleEndian(16)) // Bits per sample
            write("data".toByteArray())
            write(intToLittleEndian(byteData.size))
        }.toByteArray()

        FileOutputStream(file).use { fos ->
            fos.write(header)
            fos.write(byteData)
        }
    }

    private fun intToLittleEndian(v: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    private fun shortToLittleEndian(v: Short): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v).array()
}
