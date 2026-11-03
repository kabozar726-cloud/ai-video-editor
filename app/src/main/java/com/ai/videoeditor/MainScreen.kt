package com.ai.videoeditor

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MainScreen(vm: EditorViewModel) {
    val context = LocalContext.current
    var showPlan by remember { mutableStateOf(false) }
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) vm.pickVideo(context, uri)
    }
    val musicPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) vm.pickMusic(context, uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("AI Video Editor", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Video dein, command dein, app khud edit karegi.", fontSize = 14.sp)
        Text(vm.status, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

        Button(onClick = { videoPicker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) {
            Text("1. Video chunein (gallery se)")
        }
        if (vm.videoPicked) {
            Text("Video: ${vm.videoSeconds} second")
        }

        OutlinedTextField(
            value = vm.command,
            onValueChange = { vm.command = it },
            label = { Text("2. AI command likhein (Urdu ya English)") },
            placeholder = { Text("Misal: khamosh parts kaato, captions lagao, music lagao") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Button(onClick = { vm.applyCommand(); showPlan = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Plan banayein (Preview)")
        }
        if (showPlan && vm.planLines.isNotEmpty()) {
            Text("Plan:", fontWeight = FontWeight.Bold)
            vm.planLines.forEach { line -> Text("- $line", fontSize = 14.sp) }
        }

        Text("3. Options (zaroori nahi, sirf jo chahiye):", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { musicPicker.launch("audio/*") }) { Text("Music chunein") }
        }
        OutlinedTextField(
            value = vm.title,
            onValueChange = { vm.title = it },
            label = { Text("Title (pehle 5 second par dikhega)") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.titleColor = "yellow" }) { Text("Title: Peela") }
            Button(onClick = { vm.titleColor = "white" }) { Text("Title: Sufaid") }
            Button(onClick = { vm.titleColor = "cyan" }) { Text("Title: Neela") }
        }
        OutlinedTextField(
            value = vm.captionText,
            onValueChange = { vm.captionText = it },
            label = { Text("Caption text") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = vm.captionStart,
                onValueChange = { vm.captionStart = it },
                label = { Text("Caption shuru (sec)") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = vm.captionEnd,
                onValueChange = { vm.captionEnd = it },
                label = { Text("Caption khatam (sec)") },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = vm.trimFrom,
                onValueChange = { vm.trimFrom = it },
                label = { Text("Shuru se kaat (sec)") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = vm.trimTo,
                onValueChange = { vm.trimTo = it },
                label = { Text("Aakhir tak (sec)") },
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = { vm.render(context) },
            enabled = vm.videoPicked && !vm.isRendering,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (vm.isRendering) "Render ho raha hai..." else "4. Render karein")
        }
        if (vm.isRendering) {
            LinearProgressIndicator(progress = vm.progress / 100f, modifier = Modifier.fillMaxWidth())
            Text("Progress: ${vm.progress}% — App ko khula rakhein. 1 ghante ki video ko waqt lag sakta hai.")
        }
        if (vm.outputFile != null) {
            Text("Tayyar video mil gayi!", fontWeight = FontWeight.Bold)
            if (vm.note.isNotBlank()) Text(vm.note, fontSize = 14.sp)
            Button(
                onClick = {
                    val uri = vm.outputShareUri(context)
                    if (uri != null) {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "Video share karein"))
                    } else { vm.onShareFailed() }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("5. Video Share karein")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Note: Google sign-in, auto-captions (awaz se likhai) aur keyframe animation agli version mein aayenge. " +
                "Is version mein command-based editing, captions, title, music, speed, filters aur fade poori tarah kaam karte hain.",
            fontSize = 12.sp
        )
    }
}
