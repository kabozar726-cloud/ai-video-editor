package com.ai.videoeditor

// Understands the user's AI command (Urdu + English keywords) and turns
// it into edit settings. This is a smart command parser, not a cloud LLM:
// it needs no API key and works fully offline.
data class AppliedPlan(
    val lines: List<String>,
    val speed: Float?,
    val filter: String?,
    val fade: Boolean,
    val silenceCut: Boolean,
    val musicNeeded: Boolean,
    val titleHint: Boolean,
    val captionHint: Boolean,
    val trimStart: Int?,
    val trimLastSec: Int?
)

fun parseCommand(raw: String): AppliedPlan {
    val text = raw.lowercase()
    val lines = mutableListOf<String>()
    var speed: Float? = null
    var filter: String? = null
    var fade = false
    var silenceCut = false
    var musicNeeded = false
    var titleHint = false
    var captionHint = false
    var trimStart: Int? = null
    var trimLastSec: Int? = null

    if (text.contains("thora tez") || text.contains("thoda tez")) {
        speed = 1.5f
        lines.add("Speed: 1.5x tez")
    } else if (text.contains("slow") || text.contains("ahista") || text.contains("sust")) {
        speed = 0.5f
        lines.add("Speed: 0.5x slow")
    } else if (text.contains("fast") || text.contains("tez")) {
        speed = 2f
        lines.add("Speed: 2x fast")
    }
    if (text.contains("bright") || text.contains("roshan")) {
        filter = "Bright"
        lines.add("Filter: Bright")
    } else if (text.contains("warm") || text.contains("garam")) {
        filter = "Warm"
        lines.add("Filter: Warm")
    } else if (text.contains("cool") || text.contains("thanda")) {
        filter = "Cool"
        lines.add("Filter: Cool")
    } else if (text.contains("black") || text.contains("kala") || text.contains("sufaid") || text.contains("bw")) {
        filter = "BW"
        lines.add("Filter: Black & White")
    }
    if (text.contains("fade")) {
        fade = true
        lines.add("Fade in/out lagayega")
    }
    if (text.contains("silent") || text.contains("khamosh") || text.contains("chup") || text.contains("boring") || text.contains("khali")) {
        silenceCut = true
        lines.add("Khamosh (silent) parts kaat dega")
    }
    if (text.contains("music") || text.contains("gana") || text.contains("song") || text.contains("geet")) {
        musicNeeded = true
        lines.add("Music: neeche music file chunein")
    }
    if (text.contains("title")) {
        titleHint = true
        lines.add("Title: neeche title likhein")
    }
    if (text.contains("caption") || text.contains("subtitle") || text.contains("likhai")) {
        captionHint = true
        lines.add("Captions: neeche captions add karein")
    }
    val firstMatch = Regex("pehl[ey] (\\d+)").find(text)
    if (firstMatch != null) {
        trimStart = firstMatch.groupValues[1].toIntOrNull()
        if (trimStart != null) lines.add("Shuru se $trimStart second kaat dega")
    }
    val lastMatch = Regex("(aakhri|last) (\\d+)").find(text)
    if (lastMatch != null) {
        trimLastSec = lastMatch.groupValues[2].toIntOrNull()
        if (trimLastSec != null) lines.add("Aakhir se $trimLastSec second kaat dega")
    }
    if (lines.isEmpty()) {
        lines.add("Command samajh nahi aayi. Misal: 'khamosh parts kaato, captions lagao, music lagao' ya 'video ko bright karo aur 2x tez karo'.")
    }
    return AppliedPlan(lines, speed, filter, fade, silenceCut, musicNeeded, titleHint, captionHint, trimStart, trimLastSec)
}
