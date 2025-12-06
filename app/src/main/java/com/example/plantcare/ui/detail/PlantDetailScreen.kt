package com.example.plantcare.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.WaterDrop
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.plantcare.R

@Composable
fun PlantDetailScreen(
    plantId: String,
    onBack: () -> Unit = {},
    onEdit: (String) -> Unit = {},
    onOpenCareGuide: (String) -> Unit = {},
    onOpenLightMonitor: (String) -> Unit = {},
    viewModel: PlantDetailViewModel = hiltViewModel()
) {
    val plant by viewModel.plant.collectAsState()

    LaunchedEffect(plantId) {
        viewModel.loadPlant(plantId)
    }

    if (plant == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val p = plant!!
    val scrollState = rememberScrollState()

    Scaffold(
        floatingActionButton = {
            // No FAB in the design, but keeping if needed for future
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Hero Image Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(p.userPhotoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = p.commonName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Gradient Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                startY = 100f
                            )
                        )
                )

                // Top Bar Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { onEdit(plantId) },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                }

                // Plant Name Overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(24.dp)
                ) {
                    Text(
                        text = if (!p.nickname.isNullOrBlank()) p.nickname else p.commonName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (!p.nickname.isNullOrBlank()) {
                         Text(
                            text = p.commonName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (!p.scientificName.isNullOrBlank()) {
                        Text(
                            text = p.scientificName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.8f),
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Content Section (overlapping the image slightly would be nice, but stick to design)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-20).dp) // Pull up slightly
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                
                // Quick Stats Card
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Added on ${java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(p.createdAt))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                        
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                        
                        StatRow(
                            icon = Icons.Filled.WaterDrop,
                            text = p.wateringFrequency ?: "Analyzing...",
                            tint = Color(0xFF2196F3)
                        )
                        StatRow(
                            icon = Icons.Filled.LightMode,
                            text = p.lightRequirements ?: "Analyzing...",
                            tint = Color(0xFFFFC107)
                        )
                        StatRow(
                            icon = Icons.Filled.Favorite,
                            text = p.healthStatus ?: "Analyzing...",
                            tint = if (p.healthStatus?.contains("Poor", true) == true) Color(0xFFD32F2F) else Color(0xFF4CAF50)
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                Text(
                    text = "Care Features",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )

                // Feature Cards
                FeatureCard(
                    icon = Icons.Filled.Description,
                    title = "Care Guide",
                    description = "Watering, light, soil, and seasonal tips",
                    buttonText = "View Guide",
                    iconBgColor = Color(0xFFE8F5E9),
                    iconTint = Color(0xFF2E7D32),
                    onClick = { onOpenCareGuide(p.id) }
                )

                FeatureCard(
                    icon = Icons.Filled.LocalHospital,
                    title = "Health Check",
                    description = "Analyze plant health and detect issues",
                    buttonText = "Start Analysis",
                    iconBgColor = Color(0xFFE8F5E9), // Light Green bg
                    iconTint = Color(0xFF2E7D32),
                    badgeText = "3 checks",
                    onClick = { /* TODO */ }
                )

                FeatureCard(
                    icon = Icons.Filled.LightMode,
                    title = "Light Monitor",
                    description = "Measure light levels for optimal growth",
                    buttonText = "Measure Light",
                    iconBgColor = Color(0xFFFFF3E0), // Light Orange bg
                    iconTint = Color(0xFFFF9800),
                    isOutlinedButton = true,
                    onClick = { onOpenLightMonitor(p.id) }
                )
                
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun StatRow(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
    }
}

@Composable
fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String,
    iconBgColor: Color,
    iconTint: Color,
    badgeText: String? = null,
    isOutlinedButton: Boolean = false,
    onClick: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(iconBgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF37474F)
                    )
                    if (badgeText != null) {
                        Surface(
                            color = Color(0xFF4CAF50),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = badgeText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                if (isOutlinedButton) {
                    OutlinedButton(
                        onClick = onClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = iconTint),
                        border = androidx.compose.foundation.BorderStroke(1.dp, iconTint),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(buttonText)
                    }
                } else {
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent, // Making it outlined-ish or just text per design? 
                            // Wait, design shows outlined for view guide? No, design shows thick black border for view guide? 
                            // Actually "View Guide" is Black Button with White Text (or outlined thick).
                            // "Start Analysis" is outlined green.
                            // "Measure Light" is outlined orange.
                            // I'll stick to what looked good or interpret the image best.
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.Black),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                         Text(buttonText, color = Color.Black)
                    }
                }
            }
        }
    }
}
