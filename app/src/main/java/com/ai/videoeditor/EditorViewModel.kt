package com.ai.videoeditor

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel : ViewModel() {
    var status by mutableStateOf("Video chunein")
        private set
    var videoPicked by mutableStateOf(false)
        private set
    var videoDurationMs by mutableStateOf(0L)
        private set
    var videoSeconds by mutableStateOf(0)
        private set
    var videoHasAudio by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0)
        private set
    var isRendering by mutableStateOf(false)
        private set
    var planLines by mutableStateOf<List<String>>(emptyList())
        private set
    var outputFile by mutableStateOf<File?>(null)
        private set
    var outputUri by mutableStateOf<Uri?>(null)
        private set
    var note by mutableStateOf("")
        private set

    private var videoFile: File? = null
    private var musicFile: File? = null
    private var plan = parseCommand("")

    var command by mutableStateOf("")
    var title by mutableStateOf("")
    var titleColor by mutableStateOf("yellow")
    var captionText by mutableStateOf("")
    var captionStart by mutableStateOf("0")
    var captionEnd by mutableStateOf("10")
    var trimFrom by mutableStateOf("0")
    var trimTo by mutableStateOf("0")

    fun pickVideo(context: Context, uri: Uri) {
        videoFile = null
        videoPicked = false
        videoDurationMs = 0
        outputFile = null
        outputUri = null
        status = "Video copy ho rahi hai... (1 ghante ki video ko waqt lag sakta hai)"
        viewModelScope.launch(Dispatchers.IO) {
            val copied = FfmpegHelper.copyUriToFile(context, uri, "picked_video.mp4")
            if (copied == null) {
                withContext(Dispatchers.Main) {
                    status = "Video copy nahi hui. Dobara chunein."
                }
                return@launch
            }
            val dur = FfmpegHelper.getDurationMs(copied)
            val audio = FfmpegHelper.hasAudio(copied)
            withContext(Dispatchers.Main) {
                videoFile = copied
                videoDurationMs = dur
                videoSeconds = (dur / 1000).toInt()
                videoHasAudio = audio
                videoPicked = true
                trimTo = videoSeconds.toString()
                status = "Video tayyar hai (${videoSeconds}s). Ab command likhein."
            }
        }
    }

    fun pickMusic(context: Context, uri: Uri) {
        status = "Music copy ho rahi hai..."
        viewModelScope.launch(Dispatchers.IO) {
            val copied = FfmpegHelper.copyUriToFile(context, uri, "picked_music.mp3")
            withContext(Dispatchers.Main) {
                if (copied == null) {
                    status = "Music copy nahi hui."
                    return@withContext
                }
                musicFile = copied
                status = "Music lag gayi. Ab Render dabayein."
            }
        }
    }

    fun applyCommand() {
        plan = parseCommand(command)
        planLines = plan.lines
        status = "Plan tayyar. Preview upar dekh lein, phir Render dabayein."
    }

    fun render(context: Context) {
        val video = videoFile
        if (video == null) {
            status = "Pehle video chunein."
            return
        }
        if (isRendering) return
        isRendering = true
        progress = 0
        status = "Render ho raha hai... App ko khula rakhein."
        viewModelScope.launch(Dispatchers.IO) {
            val caps = if (captionText.isNotBlank()) {
                val s = captionStart.toIntOrNull() ?: 0
                val e = captionEnd.toIntOrNull() ?: 10
                listOf(Caption(captionText, s, e))
            } else {
                emptyList()
            }
            val speed = plan.speed ?: 1f
            val filter = plan.filter ?: ""
            val fade = plan.fade
            val silenceCut = plan.silenceCut
            val startSec = trimFrom.toIntOrNull() ?: 0
            val endSec = trimTo.toIntOrNull() ?: 0
            val out = File(context.filesDir, "edited_output.mp4")
            val req = RenderRequest(
                video = video,
                music = if (plan.musicNeeded) musicFile else null,
                startSec = startSec,
                endSec = endSec,
                speed = speed,
                filter = filter,
                fade = fade,
                silenceCut = silenceCut,
                captions = caps,
                title = title,
                titleColor = titleColor,
                fontFile = FfmpegHelper.fontFile(context),
                videoHasAudio = videoHasAudio
            )
            FfmpegHelper.render(
                req, out, videoDurationMs,
                onProgress = { progress = it },
                onDone = { ok, msg ->
                    isRendering = false
                    if (ok && out.exists()) {
                        outputFile = out
                        status = "Ho gaya! Share dabake video bhejein."
                        note = msg
                    } else {
                        status = msg
                    }
                }
            )
        }
    }

    fun outputShareUri(context: Context): Uri? {
        val f = outputFile ?: return null
        return try {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                f
            )
        } catch (e: Exception) {
            null
        }
    }
}
