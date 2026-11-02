package com.ai.videoeditor

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

data class Caption(val text: String, val startSec: Int, val endSec: Int)

data class SilenceRange(val start: Double, val end: Double)

data class RenderRequest(
    val video: File,
    val music: File?,
    val startSec: Int,
    val endSec: Int,
    val speed: Float,
    val filter: String,
    val fade: Boolean,
    val silenceCut: Boolean,
    val captions: List<Caption>,
    val title: String,
    val titleColor: String,
    val fontFile: File,
    val videoHasAudio: Boolean
)

object FfmpegHelper {

    // Gallery URIs are not file paths: copy the picked file where ffmpeg can read it.
    // For a 1-hour video this copy takes a while — the UI says so honestly.
    fun copyUriToFile(context: Context, uri: Uri, name: String): File? {
        return try {
            val out = File(context.filesDir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            if (out.exists() && out.length() > 0) out else null
        } catch (e: Exception) {
            null
        }
    }

    fun fontFile(context: Context): File {
        val f = File(context.filesDir, "DejaVuSans-Bold.ttf")
        if (!f.exists()) {
            try {
                context.assets.open("DejaVuSans-Bold.ttf").use { input ->
                    f.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
            }
        }
        return f
    }

    fun getDurationMs(file: File): Long {
        return try {
            val info = FFprobeKit.getMediaInformation(file.absolutePath)
            val dur = info.mediaInformation?.duration?.toDoubleOrNull() ?: 0.0
            (dur * 1000).toLong()
        } catch (e: Exception) {
            0L
        }
    }

    fun hasAudio(file: File): Boolean {
        return try {
            val info = FFprobeKit.getMediaInformation(file.absolutePath)
            info.mediaInformation?.streams?.any { it.type == "audio" } ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun detectSilences(file: File): List<SilenceRange> {
        val ranges = mutableListOf<SilenceRange>()
        try {
            val session = FFmpegKit.execute(
                "-i \"${file.absolutePath}\" -af silencedetect=noise=-32dB:d=0.6 -f null -"
            )
            val logs = session.allLogsAsString
            val startRe = Regex("silence_start: ([0-9.]+)")
            val endRe = Regex("silence_end: ([0-9.]+)")
            var pendingStart: Double? = null
            for (line in logs.lines()) {
                val s = startRe.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
                if (s != null) pendingStart = s
                val e = endRe.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
                if (e != null && pendingStart != null) {
                    ranges.add(SilenceRange(pendingStart, e))
                    pendingStart = null
                }
            }
        } catch (e: Exception) {
        }
        return ranges
    }

    private fun escapeDrawtext(text: String): String {
        return text.replace("\\", "\\\\").replace("'", "\\'").replace(":", "\\:").replace("%", "\\%")
    }

    private fun drawtext(font: File, text: String, size: Int, color: String, y: String, enable: String): String {
        return "drawtext=fontfile='${font.absolutePath}':text='${escapeDrawtext(text)}'" +
            ":fontsize=$size:fontcolor=$color:borderw=2:bordercolor=black" +
            ":x=(w-text_w)/2:y=$y:enable='$enable'"
    }

    // Builds the full ffmpeg command. Returns the command, the output
    // duration in ms (for progress), and a note (e.g. silence-cut fallback).
    fun buildCommand(req: RenderRequest, outFile: File, durationMs: Long): Triple<String, Long, String> {
        val start = req.startSec.toDouble()
        val rawEnd = if (req.endSec > 0) req.endSec.toDouble() else durationMs / 1000.0
        val end = rawEnd.coerceAtMost(durationMs / 1000.0).coerceAtLeast(start + 1)
        var note = ""
        var keepRanges: List<Pair<Double, Double>>? = null
        if (req.silenceCut) {
            val silences = detectSilences(req.video)
            val keeps = mutableListOf<Pair<Double, Double>>()
            var cur = start
            for (s in silences) {
                val ss = s.start.coerceIn(start, end)
                val ee = s.end.coerceIn(start, end)
                if (ss > cur && ss - cur >= 0.5) keeps.add(cur to ss)
                if (ee > cur) cur = ee
            }
            if (cur < end && end - cur >= 0.5) keeps.add(cur to end)
            if (keeps.isNotEmpty() && keeps.size <= 12) {
                keepRanges = keeps
            } else {
                note = "Silent parts zyada the, is liye poori video rakhi gayi."
            }
        }

        val outDurationSec = if (keepRanges != null) {
            keepRanges.sumOf { it.second - it.first }
        } else {
            end - start
        } / req.speed.toDouble()
        val totalMs = (outDurationSec * 1000).toLong()

        val videoParts = mutableListOf<String>()
        if (req.speed != 1f) videoParts.add("setpts=${1f / req.speed}*PTS")
        when (req.filter) {
            "Bright" -> videoParts.add("eq=brightness=0.06:contrast=1.08:saturation=1.15")
            "Warm" -> videoParts.add("colorbalance=rs=0.15:gs=0.05:bs=-0.15")
            "Cool" -> videoParts.add("colorbalance=rs=-0.15:gs=0.0:bs=0.15")
            "BW" -> videoParts.add("hue=s=0")
        }
        if (req.fade && outDurationSec > 1) {
            videoParts.add("fade=t=in:st=0:d=0.5")
            videoParts.add("fade=t=out:st=${outDurationSec - 0.5}:d=0.5")
        }
        for (cap in req.captions) {
            if (cap.text.isNotBlank() && cap.endSec > cap.startSec) {
                videoParts.add(
                    drawtext(
                        req.fontFile, cap.text, 44, "white", "h-140",
                        "between(t,${cap.startSec},${cap.endSec})"
                    )
                )
            }
        }
        if (req.title.isNotBlank()) {
            videoParts.add(
                drawtext(
                    req.fontFile, req.title, 72, req.titleColor, "(h-text_h)/2",
                    "between(t,0,5)"
                )
            )
        }

        val cmd = StringBuilder()
        cmd.append("ffmpeg -y ")
        val musicIndex: Int
        if (keepRanges != null) {
            for ((s, e) in keepRanges) {
                cmd.append("-ss $s -to $e -i \"${req.video.absolutePath}\" ")
            }
            musicIndex = keepRanges.size
        } else {
            cmd.append("-i \"${req.video.absolutePath}\" ")
            musicIndex = 1
        }
        if (req.music != null) cmd.append("-i \"${req.music.absolutePath}\" ")

        val filters = mutableListOf<String>()
        val videoSource: String
        if (keepRanges != null) {
            val k = keepRanges.size
            val concatInputs = StringBuilder()
            for (i in 0 until k) {
                concatInputs.append("[$i:v]")
                if (req.videoHasAudio) concatInputs.append("[$i:a]")
            }
            val aCount = if (req.videoHasAudio) 1 else 0
            if (req.videoHasAudio) {
                filters.add("${concatInputs}concat=n=$k:v=1:a=$aCount[vcat][acat]")
            } else {
                filters.add("${concatInputs}concat=n=$k:v=1:a=$aCount[vcat]")
            }
            videoSource = "[vcat]"
        } else {
            videoSource = "[0:v]"
        }
        if (videoParts.isNotEmpty()) {
            filters.add("$videoSource${videoParts.joinToString(",")}[vout]")
        }
        val videoMap = if (videoParts.isNotEmpty()) "[vout]" else {
            if (keepRanges != null) "[vcat]" else "0:v"
        }

        // Audio chain.
        var audioMap: String? = null
        val audioBase: String? = if (keepRanges != null) {
            if (req.videoHasAudio) "[acat]" else null
        } else {
            if (req.videoHasAudio) "[0:a]" else null
        }
        if (req.music != null) {
            if (audioBase != null) {
                var base = audioBase
                if (req.speed != 1f) {
                    filters.add("$base" + "atempo=${req.speed}[a0]")
                    base = "[a0]"
                }
                filters.add("[$musicIndex:a]volume=0.3[mus]")
                filters.add("$base[mus]amix=inputs=2:duration=first:normalize=0[aout]")
                audioMap = "[aout]"
            } else {
                filters.add("[$musicIndex:a]volume=0.8[aout]")
                audioMap = "[aout]"
            }
        } else if (audioBase != null) {
            if (req.speed != 1f) {
                filters.add("$audioBase" + "atempo=${req.speed}[aout]")
                audioMap = "[aout]"
            } else if (keepRanges != null) {
                audioMap = "[acat]"
            } else {
                audioMap = "0:a"
            }
        }

        if (filters.isNotEmpty()) {
            cmd.append("-filter_complex \"${filters.joinToString(";")}\" ")
        }
        if (keepRanges == null) {
            cmd.append("-ss $start -to $end ")
        }
        cmd.append("-map $videoMap ")
        if (audioMap != null) cmd.append("-map $audioMap ")
        cmd.append("-c:v libx264 -preset veryfast -crf 23 -c:a aac -movflags +faststart \"${outFile.absolutePath}\"")
        return Triple(cmd.toString(), totalMs, note)
    }

    fun render(
        req: RenderRequest,
        outFile: File,
        durationMs: Long,
        onProgress: (Int) -> Unit,
        onDone: (Boolean, String) -> Unit
    ) {
        try {
            val (cmd, totalMs, note) = buildCommand(req, outFile, durationMs)
            FFmpegKit.executeAsync(
                cmd,
                { session ->
                    val ok = ReturnCode.isSuccess(session.returnCode)
                    onDone(ok, if (ok) note else "Render fail ho gaya. Dobara koshish karein.")
                },
                { },
                { stats ->
                    if (totalMs > 0) {
                        val p = ((stats.time * 100) / totalMs).toInt().coerceIn(0, 100)
                        onProgress(p)
                    }
                }
            )
        } catch (e: Exception) {
            onDone(false, "Render start nahi hua: ${e.message}")
        }
    }
}
