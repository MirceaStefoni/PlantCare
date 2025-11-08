package com.example.plantcare.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenPlant: (String) -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val plants by viewModel.plants.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PlantCare", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) },
                actions = {
                    // Profile avatar placeholder + status dot
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        Surface(
                            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(50)),
                            color = MaterialTheme.colorScheme.secondary
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Person, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onSecondary)
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.align(Alignment.TopEnd).size(8.dp)
                        ) {}
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val navColors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                NavigationBarItem(
                    selected = true, onClick = { },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                    label = { Text("Home") },
                    colors = navColors
                )
                NavigationBarItem(
                    selected = false, onClick = { },
                    icon = { Icon(Icons.Outlined.History, contentDescription = null) },
                    label = { Text("History") },
                    colors = navColors
                )
                NavigationBarItem(
                    selected = false, onClick = { },
                    icon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    label = { Text("Profile") },
                    colors = navColors
                )
            }
        }
    ) { padding ->
        if (plants.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Add your first plant")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(plants, key = { it.id }) { p ->
                    PlantCard(
                        name = p.commonName,
                        scientific = p.scientificName ?: "",
                        photoUrl = if (p.userPhotoUrl.isNotBlank()) p.userPhotoUrl else p.referencePhotoUrl ?: "",
                        updatedAt = p.updatedAt,
                        onClick = { onOpenPlant(p.id) },
                        onDelete = { viewModel.deletePlant(p.id) }
                    )
                }
            }
        }

        if (showAdd) {
            AddPlantDialog(
                onDismiss = { showAdd = false },
                onPickFromGallery = { uri ->
                    val persisted = importImageToAppStorage(context, uri) ?: uri
                    viewModel.addPlantFromUri(persisted.toString(), name = "New Plant", scientific = null)
                    showAdd = false
                },
                onCapturePhoto = { uri ->
                    viewModel.addPlantFromUri(uri.toString(), name = "New Plant", scientific = null)
                    showAdd = false
                },
                onAddByName = { name ->
                    viewModel.addPlantByName(name)
                    showAdd = false
                }
            )
        }
    }
}

@Composable
private fun PlantCard(
    name: String,
    scientific: String,
    photoUrl: String,
    updatedAt: Long,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Timestamp chip
            if (updatedAt > 0) {
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = formatRelativeTime(updatedAt),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0x99000000))
                        )
                    )
                    .padding(8.dp)
            ) {
                Column {
                    Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (scientific.isNotBlank()) {
                        Text(scientific, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            var showConfirm by remember { mutableStateOf(false) }
            if (showConfirm) {
                Dialog(onDismissRequest = { showConfirm = false }) {
                    Surface {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Delete plant?")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { showConfirm = false }, colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )) { Text("Cancel") }
                                Button(onClick = { showConfirm = false; onDelete() }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = Color.White
                                )) { Text("Delete") }
                            }
                        }
                    }
                }
            }
            IconButton(onClick = { showConfirm = true }, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
            }
        }
    }
}

@Composable
private fun AddPlantDialog(
    onDismiss: () -> Unit,
    onPickFromGallery: (android.net.Uri) -> Unit,
    onCapturePhoto: (android.net.Uri) -> Unit,
    onAddByName: (String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPickFromGallery(uri)
    }
    val photoUri = remember { createTempImageUri(context) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoUri != null) onCapturePhoto(photoUri)
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Add Plant")
                Button(onClick = {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )) { Text("Add by Photo (Gallery)") }
                OutlinedButton(onClick = { photoUri?.let { cameraLauncher.launch(it) } }, colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )) {
                    Text("Add by Photo (Camera)")
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Add by Name") },
                    singleLine = true
                )
                Button(onClick = { if (name.isNotBlank()) onAddByName(name) }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )) {
                    Text("Add")
                }
            }
        }
    }
}

private fun createTempImageUri(context: android.content.Context): android.net.Uri? {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
    val file = File(dir, "plant_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
}

private fun importImageToAppStorage(context: android.content.Context, source: Uri): Uri? {
    try {
        context.contentResolver.takePersistableUriPermission(
            source,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: SecurityException) {
        // Provider didn't grant persistable permissions; we'll copy the bytes
    } catch (_: IllegalArgumentException) {
        // Uri not eligible for persistable permission
    }

    val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
    val dest = File(dir, "plant_${System.currentTimeMillis()}.jpg")
    return try {
        context.contentResolver.openInputStream(source)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", dest)
    } catch (_: Exception) {
        null
    }
}

private fun formatRelativeTime(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = (now - timestampMs).coerceAtLeast(0)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    if (days >= 1) return "${days}d ago"
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    if (hours >= 1) return "${hours}h ago"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    if (minutes >= 1) return "${minutes}m ago"
    return "Just now"
}

