package com.example.data.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.domain.model.CameraMovement
import com.example.domain.model.SoundEffectType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val TAG = "AnimeVideoRenderEngine"

data class ExportCapabilities(
    val supports1080p: Boolean,
    val supports4k: Boolean,
    val maxWidth: Int,
    val maxHeight: Int,
    val encoderName: String
)

enum class ExportResolution(val id: String, val label: String, val baseWidth: Int, val baseHeight: Int) {
    HD_720("720p", "720p Fast Preview", 1280, 720),
    FHD_1080("1080p", "1080p Full HD", 1920, 1080),
    UHD_4K("4K", "4K Cinema UHD", 3840, 2160);

    companion object {
        fun fromId(id: String): ExportResolution = entries.firstOrNull { it.id == id } ?: FHD_1080
    }
}

class AnimeVideoRenderEngine(private val context: Context) {

    fun detectExportCapabilities(): ExportCapabilities {
        var supports1080 = false
        var supports4k = false
        var maxW = 1280
        var maxH = 720
        var bestEncoder = "Software AVC"
        try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in codecList.codecInfos) {
                if (!info.isEncoder) continue
                val types = info.supportedTypes
                val avcType = types.firstOrNull {
                    it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)
                } ?: continue
                val caps = info.getCapabilitiesForType(avcType).videoCapabilities ?: continue
                val can1080 = caps.isSizeSupported(1920, 1080)
                val can4k = caps.isSizeSupported(3840, 2160)
                if (can1080) supports1080 = true
                if (can4k) supports4k = true
                val upperW = caps.supportedWidths.upper
                val upperH = caps.supportedHeights.upper
                if (upperW * upperH > maxW * maxH) {
                    maxW = upperW
                    maxH = upperH
                    bestEncoder = info.name
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect MediaCodec capabilities: ${e.message}")
        }
        return ExportCapabilities(
            supports1080p = supports1080,
            supports4k = supports4k,
            maxWidth = maxW,
            maxHeight = maxH,
            encoderName = bestEncoder
        )
    }

    suspend fun renderSceneClipMp4(
        sceneId: String,
        keyframeFile: File,
        wavAudioFile: File?,
        aspectRatio: String,
        resolution: ExportResolution,
        cameraMovementId: String,
        soundEffectId: String,
        dialogueText: String,
        speakerName: String,
        lipSyncEnvelope: List<Float>,
        durationSec: Int,
        fps: Int = 24
    ): File = withContext(Dispatchers.IO) {
        val (targetW, targetH) = computeAlignedDimensions(aspectRatio, resolution)
        val outDir = File(context.filesDir, "animeflow/videos").apply { mkdirs() }
        val videoOnlyFile = File(outDir, "${sceneId}_vtmp.mp4")
        val audioOnlyFile = File(outDir, "${sceneId}_atmp.m4a")
        val finalMp4 = File(outDir, "${sceneId}_clip.mp4")

        val sourceBmp = BitmapFactory.decodeFile(keyframeFile.absolutePath)
            ?: throw IllegalStateException("Keyframe image missing or unreadable: ${keyframeFile.name}")

        try {
            encodeVideoFramesToMp4(
                sourceBmp = sourceBmp,
                outFile = videoOnlyFile,
                width = targetW,
                height = targetH,
                durationSec = durationSec.coerceIn(3, 60),
                fps = fps,
                cameraMovement = CameraMovement.fromId(cameraMovementId),
                soundEffect = SoundEffectType.fromId(soundEffectId),
                dialogueText = dialogueText,
                speakerName = speakerName,
                lipSyncEnvelope = lipSyncEnvelope
            )
        } finally {
            sourceBmp.recycle()
        }

        if (wavAudioFile != null && wavAudioFile.exists() && wavAudioFile.length() > 44) {
            val aacReady = runCatching {
                encodeWavToAacM4a(wavAudioFile, audioOnlyFile)
                audioOnlyFile.exists() && audioOnlyFile.length() > 128
            }.getOrDefault(false)

            if (aacReady) {
                runCatching {
                    muxVideoAndAudio(videoOnlyFile, audioOnlyFile, finalMp4)
                }.onSuccess {
                    videoOnlyFile.delete()
                    audioOnlyFile.delete()
                    return@withContext finalMp4
                }.onFailure { err ->
                    Log.w(TAG, "Audio+Video mux failed, falling back to video track: ${err.message}")
                }
            }
        }

        videoOnlyFile.copyTo(finalMp4, overwrite = true)
        videoOnlyFile.delete()
        audioOnlyFile.delete()
        finalMp4
    }

    /**
     * Merges multiple rendered MP4 scene clips sequentially into a single continuous movie MP4.
     */
    suspend fun mergeClipsToMovieMp4(
        clipFiles: List<File>,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        require(clipFiles.isNotEmpty()) { "No rendered scene clips available to merge." }
        val outDir = File(context.filesDir, "animeflow/exports").apply { mkdirs() }
        val mergedFile = File(outDir, outputFileName)
        if (clipFiles.size == 1) {
            clipFiles.first().copyTo(mergedFile, overwrite = true)
            return@withContext mergedFile
        }

        val muxer = MediaMuxer(mergedFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var outVideoTrack = -1
        var outAudioTrack = -1
        var videoFormatRef: MediaFormat? = null
        var audioFormatRef: MediaFormat? = null

        // Inspect first clip for track setup
        val firstExtractor = MediaExtractor()
        firstExtractor.setDataSource(clipFiles.first().absolutePath)
        for (i in 0 until firstExtractor.trackCount) {
            val fmt = firstExtractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("video/") && outVideoTrack == -1) {
                videoFormatRef = fmt
                outVideoTrack = muxer.addTrack(fmt)
            } else if (mime.startsWith("audio/") && outAudioTrack == -1) {
                audioFormatRef = fmt
                outAudioTrack = muxer.addTrack(fmt)
            }
        }
        firstExtractor.release()

        if (outVideoTrack == -1) {
            muxer.release()
            throw IllegalStateException("First clip contains no valid video track.")
        }

        muxer.start()
        val buffer = ByteBuffer.allocateDirect(1024 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()
        var cumulativePtsOffsetUs = 0L

        try {
            for (clip in clipFiles) {
                val extractor = MediaExtractor()
                extractor.setDataSource(clip.absolutePath)
                var srcVideoIdx = -1
                var srcAudioIdx = -1
                var clipMaxDurationUs = 0L

                for (t in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(t)
                    val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("video/") && srcVideoIdx == -1) {
                        srcVideoIdx = t
                        if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                            clipMaxDurationUs = max(clipMaxDurationUs, fmt.getLong(MediaFormat.KEY_DURATION))
                        }
                    } else if (mime.startsWith("audio/") && srcAudioIdx == -1 && outAudioTrack != -1) {
                        srcAudioIdx = t
                        if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                            clipMaxDurationUs = max(clipMaxDurationUs, fmt.getLong(MediaFormat.KEY_DURATION))
                        }
                    }
                }

                var observedMaxPts = 0L
                if (srcVideoIdx != -1) {
                    extractor.selectTrack(srcVideoIdx)
                    while (true) {
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        val pts = extractor.sampleTime
                        observedMaxPts = max(observedMaxPts, pts)
                        bufferInfo.offset = 0
                        bufferInfo.size = size
                        bufferInfo.presentationTimeUs = cumulativePtsOffsetUs + pts
                        bufferInfo.flags = extractor.sampleFlags
                        muxer.writeSampleData(outVideoTrack, buffer, bufferInfo)
                        extractor.advance()
                    }
                    extractor.unselectTrack(srcVideoIdx)
                }

                if (srcAudioIdx != -1 && outAudioTrack != -1 && audioFormatRef != null) {
                    extractor.selectTrack(srcAudioIdx)
                    while (true) {
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        val pts = extractor.sampleTime
                        observedMaxPts = max(observedMaxPts, pts)
                        bufferInfo.offset = 0
                        bufferInfo.size = size
                        bufferInfo.presentationTimeUs = cumulativePtsOffsetUs + pts
                        bufferInfo.flags = extractor.sampleFlags
                        muxer.writeSampleData(outAudioTrack, buffer, bufferInfo)
                        extractor.advance()
                    }
                    extractor.unselectTrack(srcAudioIdx)
                }

                extractor.release()
                cumulativePtsOffsetUs += max(clipMaxDurationUs, observedMaxPts + 41_667L)
            }
            muxer.stop()
        } finally {
            muxer.release()
        }
        mergedFile
    }

    /**
     * Zero-permission export to Android MediaStore Movies/AnimeFlow on Android 10+.
     */
    suspend fun exportToGallery(mp4File: File, displayName: String): Uri = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$displayName.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/AnimeFlow")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create MediaStore video entry.")

        resolver.openOutputStream(uri)?.use { out ->
            FileInputStream(mp4File).use { input ->
                input.copyTo(out)
            }
        } ?: throw IllegalStateException("Failed to open MediaStore output stream.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        uri
    }

    private fun encodeVideoFramesToMp4(
        sourceBmp: Bitmap,
        outFile: File,
        width: Int,
        height: Int,
        durationSec: Int,
        fps: Int,
        cameraMovement: CameraMovement,
        soundEffect: SoundEffectType,
        dialogueText: String,
        speakerName: String,
        lipSyncEnvelope: List<Float>
    ) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, if (width >= 3840) 24_000_000 else if (width >= 1920) 8_000_000 else 4_500_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = codec.createInputSurface()
        codec.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        val totalFrames = durationSec * fps
        val frameDurationUs = 1_000_000L / fps
        var writtenFrames = 0L

        val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = height * 0.036f
            textAlign = Paint.Align.CENTER
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
        }
        val speakerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(34, 211, 238)
            textSize = height * 0.026f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val matrix = Matrix()
        val rng = Random(42)

        try {
            for (frameIdx in 0 until totalFrames) {
                val progress = frameIdx.toFloat() / max(1, totalFrames - 1).toFloat()
                val canvas: Canvas = inputSurface.lockCanvas(null)
                try {
                    drawAnimatedAnimeFrame(
                        canvas = canvas,
                        width = width,
                        height = height,
                        sourceBmp = sourceBmp,
                        progress = progress,
                        frameIdx = frameIdx,
                        cameraMovement = cameraMovement,
                        soundEffect = soundEffect,
                        dialogueText = dialogueText,
                        speakerName = speakerName,
                        lipOpen = lipSyncEnvelope.getOrElse((frameIdx * 30 / fps).coerceAtLeast(0)) { 0f },
                        matrix = matrix,
                        imgPaint = imgPaint,
                        overlayPaint = overlayPaint,
                        textPaint = textPaint,
                        speakerPaint = speakerPaint,
                        rng = rng
                    )
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                // Drain available encoded buffers
                writtenFrames = drainVideoEncoder(
                    codec = codec,
                    muxer = muxer,
                    bufferInfo = bufferInfo,
                    endOfStream = false,
                    trackIndexRef = { trackIndex },
                    setTrackIndex = { trackIndex = it },
                    muxerStartedRef = { muxerStarted },
                    setMuxerStarted = { muxerStarted = it },
                    writtenFrames = writtenFrames,
                    frameDurationUs = frameDurationUs
                )
            }

            codec.signalEndOfInputStream()
            drainVideoEncoder(
                codec = codec,
                muxer = muxer,
                bufferInfo = bufferInfo,
                endOfStream = true,
                trackIndexRef = { trackIndex },
                setTrackIndex = { trackIndex = it },
                muxerStartedRef = { muxerStarted },
                setMuxerStarted = { muxerStarted = it },
                writtenFrames = writtenFrames,
                frameDurationUs = frameDurationUs
            )
        } finally {
            try {
                codec.stop()
            } catch (_: Exception) {
            }
            codec.release()
            inputSurface.release()
            if (muxerStarted) {
                try {
                    muxer.stop()
                } catch (_: Exception) {
                }
            }
            muxer.release()
        }
    }

    private fun drainVideoEncoder(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        trackIndexRef: () -> Int,
        setTrackIndex: (Int) -> Unit,
        muxerStartedRef: () -> Boolean,
        setMuxerStarted: (Boolean) -> Unit,
        writtenFrames: Long,
        frameDurationUs: Long
    ): Long {
        var frameCounter = writtenFrames
        val timeoutUs = if (endOfStream) 10_000L else 0L
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStartedRef()) {
                        val newTrack = muxer.addTrack(codec.outputFormat)
                        setTrackIndex(newTrack)
                        muxer.start()
                        setMuxerStarted(true)
                    }
                }
                outputIndex >= 0 -> {
                    val encodedData = codec.getOutputBuffer(outputIndex)
                    if (encodedData != null) {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStartedRef()) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            bufferInfo.presentationTimeUs = frameCounter * frameDurationUs
                            muxer.writeSampleData(trackIndexRef(), encodedData, bufferInfo)
                            frameCounter++
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
        return frameCounter
    }

    private fun drawAnimatedAnimeFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        sourceBmp: Bitmap,
        progress: Float,
        frameIdx: Int,
        cameraMovement: CameraMovement,
        soundEffect: SoundEffectType,
        dialogueText: String,
        speakerName: String,
        lipOpen: Float,
        matrix: Matrix,
        imgPaint: Paint,
        overlayPaint: Paint,
        textPaint: Paint,
        speakerPaint: Paint,
        rng: Random
    ) {
        canvas.drawColor(Color.rgb(7, 9, 14))

        val baseScale = max(
            width.toFloat() / sourceBmp.width.toFloat(),
            height.toFloat() / sourceBmp.height.toFloat()
        )
        var zoom = 1.08f
        var tx = 0f
        var ty = 0f
        var rot = 0f

        when (cameraMovement) {
            CameraMovement.STATIC -> zoom = 1.0f
            CameraMovement.DOLLY_FORWARD -> zoom = 1.0f + 0.18f * progress
            CameraMovement.ZOOM_IN -> zoom = 1.02f + 0.25f * progress
            CameraMovement.ZOOM_OUT -> zoom = 1.24f - 0.22f * progress
            CameraMovement.PAN_LEFT -> {
                zoom = 1.16f
                tx = width * (0.06f - 0.12f * progress)
            }
            CameraMovement.PAN_RIGHT -> {
                zoom = 1.16f
                tx = width * (-0.06f + 0.12f * progress)
            }
            CameraMovement.ORBIT_3D -> {
                zoom = 1.14f + 0.04f * sin(progress * PI.toFloat())
                tx = width * 0.045f * sin(progress * 2f * PI.toFloat())
                ty = height * 0.025f * cos(progress * 2f * PI.toFloat())
                rot = 1.2f * sin(progress * 2f * PI.toFloat())
            }
            CameraMovement.CRANE_UP -> {
                zoom = 1.16f
                ty = height * (0.06f - 0.12f * progress)
            }
            CameraMovement.HANDHELD_ACTION -> {
                zoom = 1.15f
                tx = width * 0.015f * sin(frameIdx * 1.9f)
                ty = height * 0.015f * cos(frameIdx * 2.3f)
                rot = 0.9f * sin(frameIdx * 1.4f)
            }
        }

        val scale = baseScale * zoom
        matrix.reset()
        matrix.postTranslate(-sourceBmp.width / 2f, -sourceBmp.height / 2f)
        matrix.postScale(scale, scale)
        matrix.postRotate(rot)
        matrix.postTranslate(width / 2f + tx, height / 2f + ty)
        canvas.drawBitmap(sourceBmp, matrix, imgPaint)

        // SFX Visual Atmosphere Overlay
        when (soundEffect) {
            SoundEffectType.RAIN -> {
                overlayPaint.color = Color.argb(115, 165, 243, 252)
                overlayPaint.strokeWidth = max(2f, width * 0.002f)
                for (r in 0 until 42) {
                    val rx = ((r * 97 + frameIdx * 18) % width).toFloat()
                    val ry = ((r * 131 + frameIdx * 42) % height).toFloat()
                    canvas.drawLine(rx, ry, rx - width * 0.015f, ry + height * 0.07f, overlayPaint)
                }
            }
            SoundEffectType.FIGHTING -> {
                if (frameIdx % 18 < 5) {
                    overlayPaint.color = Color.argb(170, 244, 63, 142)
                    overlayPaint.strokeWidth = max(4f, width * 0.004f)
                    val sx = width * (0.25f + 0.5f * ((frameIdx * 7 % 10) / 10f))
                    val sy = height * 0.3f
                    canvas.drawLine(sx - width * 0.15f, sy, sx + width * 0.18f, sy + height * 0.25f, overlayPaint)
                }
            }
            SoundEffectType.EXPLOSION -> {
                if (progress in 0.1f..0.45f) {
                    val p = (progress - 0.1f) / 0.35f
                    overlayPaint.color = Color.argb(((1f - p) * 140).toInt(), 251, 146, 60)
                    overlayPaint.style = Paint.Style.STROKE
                    overlayPaint.strokeWidth = width * 0.012f * (1f - p * 0.6f)
                    canvas.drawCircle(width * 0.5f, height * 0.5f, width * 0.55f * p, overlayPaint)
                    overlayPaint.style = Paint.Style.FILL
                }
            }
            else -> {
                // Subtle anime sakura / light dust particles
                overlayPaint.color = Color.argb(90, 253, 164, 175)
                for (p in 0 until 12) {
                    val px = ((p * 173 + frameIdx * 5) % width).toFloat()
                    val py = ((p * 89 + frameIdx * 3) % height).toFloat()
                    canvas.drawCircle(px, py, max(2f, width * 0.003f), overlayPaint)
                }
            }
        }

        // Bottom cinematic gradient + Lip-Sync & Subtitle bar
        if (dialogueText.isNotBlank()) {
            overlayPaint.shader = LinearGradient(
                0f, height * 0.72f, 0f, height.toFloat(),
                Color.TRANSPARENT, Color.argb(215, 7, 9, 14),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, height * 0.72f, width.toFloat(), height.toFloat(), overlayPaint)
            overlayPaint.shader = null

            // Lip-sync equalizer / mouth waveform indicator above subtitle
            if (speakerName.isNotBlank()) {
                canvas.drawText(speakerName.uppercase(), width / 2f, height * 0.84f, speakerPaint)
            }
            val barCount = 9
            val barWidth = width * 0.006f
            val gap = width * 0.005f
            val totalBarW = barCount * barWidth + (barCount - 1) * gap
            val startX = (width - totalBarW) / 2f
            overlayPaint.color = Color.rgb(34, 211, 238)
            for (b in 0 until barCount) {
                val factor = 0.25f + 0.75f * sin((b + 1) * PI.toFloat() / (barCount + 1))
                val bh = max(height * 0.004f, height * 0.028f * lipOpen * factor)
                val bx = startX + b * (barWidth + gap)
                val by = height * 0.875f - bh / 2f
                canvas.drawRoundRect(
                    RectF(bx, by, bx + barWidth, by + bh),
                    barWidth / 2f, barWidth / 2f, overlayPaint
                )
            }

            canvas.drawText(dialogueText.take(90), width / 2f, height * 0.935f, textPaint)
        }
    }

    private fun encodeWavToAacM4a(wavFile: File, outM4a: File) {
        val wavBytes = FileInputStream(wavFile).use { it.readBytes() }
        if (wavBytes.size <= 44) return
        val bb = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        val sampleRate = bb.getInt(24).coerceAtLeast(8000)
        val channels = bb.getShort(22).toInt().coerceAtLeast(1)
        val pcmOffset = 44
        val pcmLength = wavBytes.size - pcmOffset

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(outM4a.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIdx = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        var readPos = 0
        var totalSamplesFed = 0L
        var inputDone = false
        var outputDone = false
        val bytesPerFrame = channels * 2

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(5000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        val remaining = pcmLength - readPos
                        if (remaining <= 0) {
                            codec.queueInputBuffer(
                                inIdx, 0, 0,
                                (totalSamplesFed * 1_000_000L) / sampleRate,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val chunk = min(inBuf.capacity(), remaining)
                            inBuf.put(wavBytes, pcmOffset + readPos, chunk)
                            val pts = (totalSamplesFed * 1_000_000L) / sampleRate
                            readPos += chunk
                            totalSamplesFed += chunk / bytesPerFrame
                            codec.queueInputBuffer(inIdx, 0, chunk, pts, 0)
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 5000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            trackIdx = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null) {
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size > 0 && muxerStarted) {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(trackIdx, outBuf, bufferInfo)
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } finally {
            try {
                codec.stop()
            } catch (_: Exception) {
            }
            codec.release()
            if (muxerStarted) {
                try {
                    muxer.stop()
                } catch (_: Exception) {
                }
            }
            muxer.release()
        }
    }

    private fun muxVideoAndAudio(videoFile: File, audioFile: File, outFile: File) {
        val vExt = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
        val aExt = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        try {
            vExt.selectTrack(0)
            aExt.selectTrack(0)
            val vTrack = muxer.addTrack(vExt.getTrackFormat(0))
            val aTrack = muxer.addTrack(aExt.getTrackFormat(0))
            muxer.start()

            val buf = ByteBuffer.allocateDirect(512 * 1024)
            val info = MediaCodec.BufferInfo()

            while (true) {
                val sz = vExt.readSampleData(buf, 0)
                if (sz < 0) break
                info.set(0, sz, vExt.sampleTime, vExt.sampleFlags)
                muxer.writeSampleData(vTrack, buf, info)
                vExt.advance()
            }
            while (true) {
                val sz = aExt.readSampleData(buf, 0)
                if (sz < 0) break
                info.set(0, sz, aExt.sampleTime, aExt.sampleFlags)
                muxer.writeSampleData(aTrack, buf, info)
                aExt.advance()
            }
            muxer.stop()
        } finally {
            vExt.release()
            aExt.release()
            muxer.release()
        }
    }

    private fun computeAlignedDimensions(aspectRatio: String, resolution: ExportResolution): Pair<Int, Int> {
        val longEdge = resolution.baseWidth
        val raw = when (aspectRatio) {
            "9:16" -> (longEdge * 9 / 16) to longEdge
            "21:9" -> longEdge to (longEdge * 9 / 21)
            else -> longEdge to (longEdge * 9 / 16)
        }
        // Align to multiples of 16 for hardware MediaCodec compatibility
        val w = ((raw.first + 8) / 16) * 16
        val h = ((raw.second + 8) / 16) * 16
        return w.coerceAtLeast(512) to h.coerceAtLeast(512)
    }
}
