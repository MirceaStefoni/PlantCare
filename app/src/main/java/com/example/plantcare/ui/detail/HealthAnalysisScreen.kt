package com.example.plantcare.ui.detail

import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.plantcare.domain.model.HealthIssue
import com.example.plantcare.domain.model.HealthRecommendation
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Forest green color used throughout the app
private val ForestGreen = Color(0xFF2D6A4F)
private val LightGreen = Color(0xFF95D5B2)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HealthAnalysisScreen(
    plantId: String,
    onBack: () -> Unit,
    viewModel: HealthAnalysisViewModel = hiltViewModel()
) {
    val plant by viewModel.plant.collectAsState()
    val affectedAreaPhotoUri by viewModel.affectedAreaPhotoUri.collectAsState()
    val analysisStarted by viewModel.analysisStarted.collectAsState()
    
    // Loading states
    val isLoadingScore by viewModel.isLoadingScore.collectAsState()
    val isLoadingIssues by viewModel.isLoadingIssues.collectAsState()
    val isLoadingRecommendations by viewModel.isLoadingRecommendations.collectAsState()
    
    // Results
    val scoreResult by viewModel.scoreResult.collectAsState()
    val issuesResult by viewModel.issuesResult.collectAsState()
    val recommendationsResult by viewModel.recommendationsResult.collectAsState()
    
    // Errors
    val scoreError by viewModel.scoreError.collectAsState()
    val issuesError by viewModel.issuesError.collectAsState()
    val recommendationsError by viewModel.recommendationsError.collectAsState()
    
    val analyzedAt by viewModel.analyzedAt.collectAsState()
    
    val context = LocalContext.current
    var showPhotoOptions by remember { mutableStateOf(false) }
    
    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.setAffectedAreaPhoto(uri)
        }
    }
    
    // Camera launcher
    val photoUri = remember { createTempImageUri(context) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoUri != null) {
            viewModel.setAffectedAreaPhoto(photoUri)
        }
    }
    
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(plantId) {
        viewModel.loadPlant(plantId)
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
                    "Add Photo",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    "Choose how to add the affected area photo:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                PhotoOptionCard(
                    icon = Icons.Filled.PhotoLibrary,
                    title = "Choose from Gallery",
                    description = "Select a photo from your device",
                    onClick = {
                        galleryLauncher.launch("image/*")
                        showPhotoOptions = false
                    }
                )
                
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Health Analysis",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (!analysisStarted) {
            // Photo Selection UI
            PhotoSelectionContent(
                plant = plant,
                affectedAreaPhotoUri = affectedAreaPhotoUri,
                onAddPhoto = { showPhotoOptions = true },
                onRemovePhoto = { viewModel.clearAffectedAreaPhoto() },
                onStartAnalysis = { viewModel.analyzeHealth(plantId) },
                modifier = Modifier.padding(padding)
            )
        } else {
            // Results UI with skeleton loading
            AnalysisResultsContent(
                scoreResult = scoreResult,
                issuesResult = issuesResult,
                recommendationsResult = recommendationsResult,
                isLoadingScore = isLoadingScore,
                isLoadingIssues = isLoadingIssues,
                isLoadingRecommendations = isLoadingRecommendations,
                scoreError = scoreError,
                issuesError = issuesError,
                recommendationsError = recommendationsError,
                analyzedAt = analyzedAt,
                onRetryScore = { viewModel.retryScore() },
                onRetryIssues = { viewModel.retryIssues() },
                onRetryRecommendations = { viewModel.retryRecommendations() },
                onNewAnalysis = { viewModel.resetAnalysis() },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun PhotoSelectionContent(
    plant: com.example.plantcare.domain.model.Plant?,
    affectedAreaPhotoUri: Uri?,
    onAddPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onStartAnalysis: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        // Title
        Text(
            "Plant Health Check",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = ForestGreen
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Compare your plant's current state",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Images Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Plant Photo (Left)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(plant?.userPhotoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Plant Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "Your Plant",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // Affected Area Photo (Right)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .fillMaxWidth()
                        .clickable { onAddPhoto() },
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (affectedAreaPhotoUri != null) 
                            Color.Transparent 
                        else 
                            LightGreen.copy(alpha = 0.3f)
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (affectedAreaPhotoUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(affectedAreaPhotoUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Affected Area",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            IconButton(
                                onClick = onRemovePhoto,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(32.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(ForestGreen, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Add Photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    "Add Photo",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ForestGreen,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "Affected Area",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Instruction Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    "Take a photo of the affected area of your plant to start the analysis. This helps identify issues and provide accurate recommendations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    lineHeight = 20.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Analyze Button
        Button(
            onClick = onStartAnalysis,
            enabled = affectedAreaPhotoUri != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ForestGreen,
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Analysis", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun AnalysisResultsContent(
    scoreResult: com.example.plantcare.domain.model.HealthScoreResult?,
    issuesResult: List<HealthIssue>?,
    recommendationsResult: com.example.plantcare.domain.model.HealthRecommendationsResult?,
    isLoadingScore: Boolean,
    isLoadingIssues: Boolean,
    isLoadingRecommendations: Boolean,
    scoreError: String?,
    issuesError: String?,
    recommendationsError: String?,
    analyzedAt: Long?,
    onRetryScore: () -> Unit,
    onRetryIssues: () -> Unit,
    onRetryRecommendations: () -> Unit,
    onNewAnalysis: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Health Score Section
        HealthScoreSection(
            result = scoreResult,
            isLoading = isLoadingScore,
            error = scoreError,
            analyzedAt = analyzedAt,
            onRetry = onRetryScore
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Detected Issues Section
        IssuesSection(
            issues = issuesResult,
            isLoading = isLoadingIssues,
            error = issuesError,
            onRetry = onRetryIssues
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Recommendations Section
        RecommendationsSection(
            result = recommendationsResult,
            isLoading = isLoadingRecommendations,
            error = recommendationsError,
            onRetry = onRetryRecommendations
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // New Analysis Button
        OutlinedButton(
            onClick = onNewAnalysis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ForestGreen
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.horizontalGradient(listOf(ForestGreen, ForestGreen))
            )
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Analysis", fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun HealthScoreSection(
    result: com.example.plantcare.domain.model.HealthScoreResult?,
    isLoading: Boolean,
    error: String?,
    analyzedAt: Long?,
    onRetry: () -> Unit
) {
    val scoreColor = when {
        result == null -> MaterialTheme.colorScheme.surfaceVariant
        result.healthScore >= 70 -> Color(0xFF43A047)
        result.healthScore >= 40 -> Color(0xFFFF9800)
        else -> Color(0xFFD32F2F)
    }
    
    val bgGradient = when {
        result == null -> listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.background)
        result.healthScore >= 70 -> listOf(Color(0xFFE8F5E9), MaterialTheme.colorScheme.background)
        result.healthScore >= 40 -> listOf(Color(0xFFFFF8E1), MaterialTheme.colorScheme.background)
        else -> listOf(Color(0xFFFFEBEE), MaterialTheme.colorScheme.background)
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(bgGradient))
            .padding(vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                // Skeleton Loading
                SkeletonCircle(size = 100.dp)
                Spacer(modifier = Modifier.height(16.dp))
                SkeletonBox(width = 180.dp, height = 24.dp)
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonBox(width = 240.dp, height = 16.dp)
            } else if (error != null) {
                // Error state
                ErrorCard(error = error, onRetry = onRetry)
            } else if (result != null) {
                // Score Circle
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(scoreColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${result.healthScore}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "/100",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "Health Score: ${result.healthStatus}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
                
                Text(
                    result.statusDescription,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
                    textAlign = TextAlign.Center
                )
                
                if (analyzedAt != null) {
                    Text(
                        "Analyzed on ${SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault()).format(Date(analyzedAt))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun IssuesSection(
    issues: List<HealthIssue>?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            "Detected Issues",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        if (isLoading) {
            repeat(2) {
                SkeletonCard()
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else if (error != null) {
            ErrorCard(error = error, onRetry = onRetry)
        } else if (issues != null) {
            if (issues.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE8F5E9)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ForestGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "No issues detected! Your plant looks healthy.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ForestGreen
                        )
                    }
                }
            } else {
                issues.forEach { issue ->
                    IssueCard(issue)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun RecommendationsSection(
    result: com.example.plantcare.domain.model.HealthRecommendationsResult?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            "Care Recommendations",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        if (isLoading) {
            repeat(2) {
                SkeletonCard()
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            SkeletonCard()
        } else if (error != null) {
            ErrorCard(error = error, onRetry = onRetry)
        } else if (result != null) {
            result.recommendations.forEach { recommendation ->
                RecommendationCard(recommendation)
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Prevention Tips
            if (result.preventionTips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Prevention Tips",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        result.preventionTips.forEach { tip ->
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = ForestGreen,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    tip,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Skeleton Loading Components
@Composable
private fun SkeletonCircle(size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                shimmerBrush()
            )
    )
}

@Composable
private fun SkeletonBox(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(shimmerBrush())
    )
}

@Composable
private fun SkeletonCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(shimmerBrush())
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush())
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush())
                )
            }
        }
    }
}

@Composable
private fun shimmerBrush(): Brush {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
    return Brush.horizontalGradient(shimmerColors)
}

@Composable
private fun ErrorCard(error: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Text("Retry", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun IssueCard(issue: HealthIssue) {
    var expanded by remember { mutableStateOf(true) }
    
    val severityColor = when (issue.severity.lowercase()) {
        "high" -> Color(0xFFD32F2F)
        "medium" -> Color(0xFFFF9800)
        else -> Color(0xFF4CAF50)
    }
    
    val severityBgColor = when (issue.severity.lowercase()) {
        "high" -> Color(0xFFFFEBEE)
        "medium" -> Color(0xFFFFF3E0)
        else -> Color(0xFFE8F5E9)
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(severityBgColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Eco,
                        contentDescription = null,
                        tint = severityColor
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        issue.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Surface(
                        color = severityColor,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            issue.severity,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        issue.description ?: "No details available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendationCard(recommendation: HealthRecommendation) {
    val (icon, bgColor, iconColor) = when (recommendation.type.lowercase()) {
        "watering" -> Triple(Icons.Filled.WaterDrop, Color(0xFFE3F2FD), Color(0xFF2196F3))
        "light" -> Triple(Icons.Filled.LightMode, Color(0xFFFFF8E1), Color(0xFFFFC107))
        "treatment" -> Triple(Icons.Filled.LocalHospital, Color(0xFFFFEBEE), Color(0xFFD32F2F))
        else -> Triple(Icons.Filled.CheckCircle, Color(0xFFE8F5E9), ForestGreen)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(bgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    recommendation.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    recommendation.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
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
        elevation = CardDefaults.cardElevation(0.dp)
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
                color = ForestGreen,
                modifier = Modifier.size(56.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Color.White,
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
    val file = File(dir, "health_analysis_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
}
