package com.example.plantcare.ui.detail

import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun EditPlantScreen(
    plantId: String,
    onBack: () -> Unit,
    onPlantDeleted: () -> Unit,
    viewModel: EditPlantViewModel = hiltViewModel()
) {
    val plant by viewModel.plant.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveError by viewModel.saveError.collectAsState()
    val pendingPhotoPreview by viewModel.pendingPhotoPreview.collectAsState()
    val context = LocalContext.current
    
    // Photo picker state
    var showPhotoOptions by remember { mutableStateOf(false) }
    
    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.selectPhoto(uri)
        }
    }
    
    // Camera launcher
    val photoUri = remember { createTempImageUri(context) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoUri != null) {
            viewModel.selectPhoto(photoUri)
        }
    }
    
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    
    LaunchedEffect(plantId) {
        viewModel.loadPlant(plantId)
    }

    if (plant == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val p = plant!!
    
    // State for form fields
    var commonName by remember { mutableStateOf(p.commonName) }
    var scientificName by remember { mutableStateOf(p.scientificName ?: "") }
    var nickname by remember { mutableStateOf(p.nickname ?: "") }
    var location by remember { mutableStateOf(p.location ?: "") }
    var acquiredDate by remember { mutableStateOf(p.acquiredDate) }
    var notes by remember { mutableStateOf(p.notes ?: "") }

    // Date Picker State
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = acquiredDate)
    
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    acquiredDate = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Photo Options Bottom Sheet
    if (showPhotoOptions) {
        ModalBottomSheet(
            onDismissRequest = { showPhotoOptions = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Change Photo",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    "Choose a method to update your plant photo:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                // Gallery option
                PhotoOptionCard(
                    icon = Icons.Filled.PhotoLibrary,
                    title = "Choose from Gallery",
                    description = "Select a photo from your device",
                    onClick = {
                        galleryLauncher.launch("image/*")
                        showPhotoOptions = false
                    }
                )
                
                // Camera option
                PhotoOptionCard(
                    icon = Icons.Filled.CameraAlt,
                    title = "Take a Photo",
                    description = "Capture a new image with your camera",
                    onClick = {
                        if (cameraPermissionState.status.isGranted) {
                            photoUri?.let { cameraLauncher.launch(it) }
                        } else {
                            cameraPermissionState.launchPermissionRequest()
                        }
                        showPhotoOptions = false
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Determine which photo to display: pending (if changed) or original
    val displayPhotoUri = pendingPhotoPreview?.toString() ?: p.userPhotoUrl

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Edit Plant", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.savePlant(
                                plantId = plantId,
                                commonName = commonName,
                                scientificName = scientificName,
                                nickname = nickname,
                                location = location,
                                acquiredDate = acquiredDate,
                                notes = notes,
                                onSuccess = onBack
                            )
                        },
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Photo Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .shadow(8.dp, RoundedCornerShape(24.dp))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(displayPhotoUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Plant Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledTonalButton(
                            onClick = { showPhotoOptions = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Change Photo")
                        }
                    }
                }
            }
            
            // Pending photo indicator
            if (pendingPhotoPreview != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE8F5E9)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "New photo selected. Press Save to apply changes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }
            
            // Error message
            if (saveError != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFEBEE)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            saveError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC62828)
                        )
                    }
                }
            }

            // Form Fields
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Nickname (Primary Name)
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Nickname") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Give your plant a name") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )

                // Common Name (Read Only)
                OutlinedTextField(
                    value = commonName,
                    onValueChange = { }, // Read-only
                    label = { Text("Common Name") },
                    leadingIcon = { Icon(Icons.Filled.Eco, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = false,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                )
                
                // Scientific Name hidden as requested
            }

            // Location Dropdown (Simulated with TextField for now)
            var locationExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                    trailingIcon = { 
                        IconButton(onClick = { locationExpanded = true }) {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                DropdownMenu(
                    expanded = locationExpanded,
                    onDismissRequest = { locationExpanded = false }
                ) {
                    listOf("Living Room", "Kitchen", "Bedroom", "Balcony", "Office").forEach { loc ->
                        DropdownMenuItem(
                            text = { Text(loc) },
                            onClick = { 
                                location = loc
                                locationExpanded = false 
                            }
                        )
                    }
                }
            }

            // Date Picker Field
            OutlinedTextField(
                value = if (acquiredDate != null) SimpleDateFormat("MM/dd/yyyy", Locale.US).format(Date(acquiredDate!!)) else "",
                onValueChange = {},
                label = { Text("Acquired Date") },
                leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                minLines = 3
            )

            // Delete Section
            OutlinedCard(
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Plant", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Permanently remove this plant",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                    Button(
                        onClick = { viewModel.deletePlant(plantId) { onPlantDeleted() } },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PhotoOptionCard(
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

private fun createTempImageUri(context: android.content.Context): Uri? {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
    val file = File(dir, "plant_edit_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
}
