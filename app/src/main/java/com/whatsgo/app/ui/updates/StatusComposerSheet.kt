package com.whatsgo.app.ui.updates

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream

private val StatusColors = listOf(
    0xFF000000L, 0xFF3949ABL, 0xFF00897BL, 0xFF7CB342L,
    0xFFF9A825L, 0xFFF4511EL, 0xFF6D4C41L, 0xFFD81B60L, 0xFF8E24AAL
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusComposerSheet(
    onDismiss: () -> Unit,
    onPostText: (String, Long) -> Unit,
    onPostImage: (String, String) -> Unit,
    onPostVideo: (String, String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var text by remember { mutableStateOf("") }
    var selectedColor by remember { mutableLongStateOf(StatusColors.first()) }
    var pickedMediaPath by remember { mutableStateOf<String?>(null) }
    var pickedMediaType by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = copyToCache(context, uri, "img")
            if (path != null) {
                pickedMediaPath = path
                pickedMediaType = "image"
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = copyToCache(context, uri, "vid")
            if (path != null) {
                pickedMediaPath = path
                pickedMediaType = "video"
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "New status",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    if (pickedMediaPath != null && pickedMediaType != null) {
                        if (pickedMediaType == "image") onPostImage(pickedMediaPath!!, text)
                        else onPostVideo(pickedMediaPath!!, text)
                    } else if (text.isNotBlank()) {
                        onPostText(text, selectedColor)
                    }
                    onDismiss()
                }) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Post",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (pickedMediaPath == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(Color(selectedColor), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = {
                            Text(
                                "Type a status…",
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusColors.forEach { color ->
                        val isSel = color == selectedColor
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(color), CircleShape)
                                .border(
                                    width = if (isSel) 3.dp else 1.dp,
                                    color = if (isSel) MaterialTheme.colorScheme.primary
                                            else Color.White.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = color }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = { imagePicker.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Image")
                    }
                    FilledTonalButton(
                        onClick = { videoPicker.launch("video/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Video")
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Selected ${pickedMediaType ?: "media"}",
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            pickedMediaPath = null
                            pickedMediaType = null
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Remove")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Caption") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun copyToCache(context: android.content.Context, uri: Uri, prefix: String): String? {
    return runCatching {
        val dir = File(context.cacheDir, "status").apply { mkdirs() }
        val out = File(dir, "${prefix}_${System.currentTimeMillis()}")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        }
        out.absolutePath
    }.getOrNull()
}
