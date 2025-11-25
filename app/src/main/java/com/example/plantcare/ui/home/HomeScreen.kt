package com.example.plantcare.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.plantcare.ui.components.getPlantIconById
import com.example.plantcare.ui.theme.ForestGreen
import com.example.plantcare.ui.theme.LightSage
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import java.util.concurrent.TimeUnit
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted

import com.example.plantcare.ui.profile.ProfileScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenPlant: (String) -> Unit, 
    onOpenProfile: () -> Unit, // Kept for signature compatibility but unused for navigation now
    onLogout: () -> Unit,
    onAccountDeleted: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val plants by viewModel.plants.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Navigation State
    var selectedTab by remember { mutableStateOf(0) } // 0: Home, 1: History, 2: Profile

    BackHandler {
        (context as? android.app.Activity)?.moveTaskToBack(true)
    }

    Scaffold(
        topBar = {
            if (selectedTab == 0) { // Only show TopBar on Home Tab
                TopAppBar(
                    title = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(LightSage.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getPlantIconById(0),
                                    contentDescription = "PlantCare logo",
                                    tint = ForestGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "PlantCare", 
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showAdd = true }, 
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        Icons.Default.Add, 
                        contentDescription = "Add Plant",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp
                ) {
                    val navColors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { 
                            Icon(
                                Icons.Outlined.Home, 
                                contentDescription = null,
                                modifier = Modifier.size(26.dp)
                            ) 
                        },
                        label = { 
                            Text(
                                "Home",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            ) 
                        },
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { 
                            Icon(
                                Icons.Outlined.History, 
                                contentDescription = null,
                                modifier = Modifier.size(26.dp)
                            ) 
                        },
                        label = { 
                            Text(
                                "History",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            ) 
                        },
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { 
                            Icon(
                                Icons.Outlined.Person, 
                                contentDescription = null,
                                modifier = Modifier.size(26.dp)
                            ) 
                        },
                        label = { 
                            Text(
                                "Profile",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            ) 
                        },
                        colors = navColors
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> {
                    if (plants.isEmpty()) {
                        EmptyState(modifier = Modifier.fillMaxSize())
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(plants, key = { it.id }) { p ->
                                PlantCard(
                                    name = if (!p.nickname.isNullOrBlank()) p.nickname else p.commonName,
                                    subtitle = if (!p.nickname.isNullOrBlank()) p.commonName else p.scientificName ?: "",
                                    photoUrl = if (p.userPhotoUrl.isNotBlank()) p.userPhotoUrl else p.referencePhotoUrl ?: "",
                                    updatedAt = p.updatedAt,
                                    onClick = { onOpenPlant(p.id) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("History Feature Coming Soon")
                    }
                }
                2 -> {
                    // Embedded Profile Screen
                    // We need to pass 'onBack' as {} or handle it if we want it to go back to Home tab
                    // But logically 'Profile' is a main tab, so 'onBack' might not be relevant or could switch tab to Home.
                    ProfileScreen(
                        onLogout = onLogout,
                        onAccountDeleted = onAccountDeleted,
                        onBack = { selectedTab = 0 } // Back from profile goes to Home
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
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(LightSage),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getPlantIconById(0),
                contentDescription = "Plant icon",
                tint = ForestGreen,
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "No Plants Yet",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Add your first plant to get started",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "Tap the + button below",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PlantCard(
    name: String,
    subtitle: String,
    photoUrl: String,
    updatedAt: Long,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick, 
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 8.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            // Plant Image
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Timestamp chip - top left
            if (updatedAt > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = formatRelativeTime(updatedAt),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            
            // Plant name gradient overlay - bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent, 
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        name, 
                        color = Color.White, 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, 
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle, 
                            color = Color.White.copy(alpha = 0.85f),
                            fontStyle = FontStyle.Italic,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
private fun AddPlantDialog(
    onDismiss: () -> Unit,
    onPickFromGallery: (android.net.Uri) -> Unit,
    onCapturePhoto: (android.net.Uri) -> Unit,
    onAddByName: (String) -> Unit
) {
    val context = LocalContext.current
    var showNameInput by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onPickFromGallery(uri)
    }
    val photoUri = remember { createTempImageUri(context) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoUri != null) onCapturePhoto(photoUri)
    }
    
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                "Add New Plant",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            if (!showNameInput) {
                // Photo options
                Text(
                    "Choose a method to add your plant:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                    // Gallery option
                AddPlantOptionCard(
                    icon = getPlantIconById(0),
                    title = "Choose from Gallery",
                    description = "Select a photo from your device",
                    onClick = {
                        galleryLauncher.launch("image/*")
                    }
                )
                
                // Camera option
                AddPlantOptionCard(
                    icon = Icons.Filled.CameraAlt,
                    title = "Take a Photo",
                    description = "Capture a new image with your camera",
                    onClick = { 
                        if (cameraPermissionState.status.isGranted) {
                            photoUri?.let { cameraLauncher.launch(it) }
                        } else {
                            cameraPermissionState.launchPermissionRequest()
                        }
                    }
                )
                
                // Name option
                AddPlantOptionCard(
                    icon = Icons.Filled.TextFields,
                    title = "Enter Plant Name",
                    description = "Type the name if you already know it",
                    onClick = { showNameInput = true }
                )
            } else {
                // Name input
                Text(
                    "Enter the plant name:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Plant Name") },
                    placeholder = { Text("e.g., Monstera Deliciosa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    OutlinedButton(
                        onClick = { 
                            showNameInput = false
                            name = ""
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Back", fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = { 
                            if (name.isNotBlank()) {
                                onAddByName(name)
                            }
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Add Plant", fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AddPlantOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
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

