package com.example.plantcare.ui.light

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.plantcare.data.sensor.LightSensorSampler
import com.example.plantcare.domain.model.LightMeasurement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightMonitorScreen(
    onBack: () -> Unit,
    viewModel: LightMonitorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Light Monitor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::startMeasurement,
                        enabled = state.measurementPhase != MeasurementPhase.Sampling && state.sensorAvailable
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Measure again")
                    }
                }
            )
        }
    ) { padding ->
        when {
            !state.sensorAvailable -> EmptyState(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                title = "Sensor unavailable",
                message = "This device does not expose an ambient light sensor, so light measurements cannot be taken."
            )
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        MeasurementHeaderCard(
                            plantName = state.plantName,
                            plantScientific = state.plantScientificName,
                            plantPhoto = state.referencePhotoUrl,
                            measurementPhase = state.measurementPhase,
                            currentLux = state.currentLux,
                            remainingSeconds = (state.remainingMillis / 1000.0).coerceAtLeast(0.0),
                            elapsedMillis = state.elapsedMillis
                        )
                    }
                    item {
                        Crossfade(targetState = state.measurementPhase, label = "phase") { phase ->
                            when (phase) {
                                MeasurementPhase.Sampling -> SamplingContent(state)
                                MeasurementPhase.Evaluating -> EvaluatingContent(state)
                                MeasurementPhase.Ready -> ResultContent(
                                    measurement = state.latestMeasurement,
                                    history = state.history,
                                    onMeasureAgain = viewModel::startMeasurement
                                )
                                MeasurementPhase.Error -> ErrorContent(
                                    message = state.errorMessage ?: "Something went wrong.",
                                    onRetry = viewModel::startMeasurement
                                )
                                MeasurementPhase.Idle -> IdleContent(onMeasure = viewModel::startMeasurement)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeasurementHeaderCard(
    plantName: String?,
    plantScientific: String?,
    plantPhoto: String?,
    measurementPhase: MeasurementPhase,
    currentLux: Double?,
    remainingSeconds: Double,
    elapsedMillis: Long
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E0))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LightMode,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plantName ?: "Plant",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1B1C1E)
                    )
                    Text(
                        text = plantScientific ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF616161)
                    )
                }
                if (plantPhoto != null) {
                    AsyncImage(
                        model = plantPhoto,
                        contentDescription = plantName,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            val measuring = measurementPhase == MeasurementPhase.Sampling
            Text(
                text = currentLux?.let { "${it.toInt()} lux" } ?: "-- lux",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = Color(0xFFFB5607)
            )
            Text(
                text = when (measurementPhase) {
                    MeasurementPhase.Sampling -> "Measuring… ${(remainingSeconds).toInt()}s remaining"
                    MeasurementPhase.Evaluating -> "Generating recommendations…"
                    MeasurementPhase.Ready -> "Tap refresh to measure again"
                    MeasurementPhase.Idle -> "Tap refresh to start measuring"
                    MeasurementPhase.Error -> "Unable to measure"
                },
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF5B5F62)
            )
            if (measuring) {
                val progressValue =
                    (elapsedMillis / LightSensorSampler.DEFAULT_DURATION_MS.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = progressValue,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFF9800)
                )
            }
        }
    }
}

@Composable
private fun SamplingContent(state: LightMonitorUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GaugeCard(
            label = "Real-time lux",
            value = state.currentLux?.toInt() ?: 0,
            progress = (state.elapsedMillis / LightSensorSampler.DEFAULT_DURATION_MS.toFloat()).coerceIn(0f, 1f)
        )
        MeasurementTipsCard()
    }
}

@Composable
private fun ResultContent(
    measurement: LightMeasurement?,
    history: List<LightMeasurement>,
    onMeasureAgain: () -> Unit
) {
    if (measurement == null) {
        IdleContent(onMeasureAgain)
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SummaryCard(measurement)
        IdealRangeCard(measurement)
        AdequacyCard(measurement)
        RecommendationsCard(measurement)
        UnderstandingLevelsCard()
        RecentMeasurementsCard(history)
        Button(
            onClick = onMeasureAgain,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Measure again")
        }
    }
}

@Composable
private fun EvaluatingContent(state: LightMonitorUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(color = Color(0xFFFF9800))
        Text("Analyzing ${state.currentLux?.toInt() ?: 0} lux for recommendations…")
    }
}

@Composable
private fun IdleContent(onMeasure: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MeasurementTipsCard()
        Button(
            onClick = onMeasure,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start measurement")
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Button(onClick = onRetry, shape = RoundedCornerShape(12.dp)) {
            Text("Try again")
        }
    }
}

@Composable
private fun GaugeCard(label: String, value: Int, progress: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    // Background arc
                    drawArc(
                        color = Color(0xFFEAEAEA),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = 24f, cap = StrokeCap.Round)
                    )
                    // Progress arc
                    drawArc(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFFFF4E4E), Color(0xFFFF9800), Color(0xFF4CAF50))
                        ),
                        startAngle = 135f,
                        sweepAngle = 270f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        style = Stroke(width = 24f, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$value",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("lux", color = Color.Gray)
                }
            }
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun MeasurementTipsCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6ED))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Measurement Tips", fontWeight = FontWeight.Bold)
            Text("• Place phone near plant location")
            Text("• Remove shadows or obstructions")
            Text("• Hold steady during measurement")
        }
    }
}

@Composable
private fun SummaryCard(measurement: LightMeasurement) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE9FCEB))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("${measurement.luxValue.toInt()} lux", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                measurement.assessmentLabel,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF1B5E20)
            )
            Text(
                formatTimestamp(measurement.measuredAt),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF546E7A)
            )
        }
    }
}

@Composable
private fun IdealRangeCard(measurement: LightMeasurement) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Ideal Light for this plant", fontWeight = FontWeight.Bold)
            Text(
                "${measurement.idealMinLux?.toInt() ?: 0} – ${measurement.idealMaxLux?.toInt() ?: 0} lux",
                style = MaterialTheme.typography.titleMedium
            )
            Text(measurement.idealDescription ?: "", color = Color(0xFF6D6D6D))
        }
    }
}

@Composable
private fun AdequacyCard(measurement: LightMeasurement) {
    val percent = (measurement.adequacyPercent ?: 0).coerceIn(0, 100)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Optimal percentage", fontWeight = FontWeight.Bold)
                Text(
                    "$percent% of ideal light",
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(72.dp)
            ) {
                CircularProgressIndicator(
                    progress = (percent / 100f).coerceIn(0f, 1f),
                    strokeWidth = 8.dp,
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF4CAF50)
                )
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
            }
        }
    }
}

@Composable
private fun RecommendationsCard(measurement: LightMeasurement) {
    val recommendations = measurement.recommendations?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
    if (recommendations.isEmpty()) return
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recommendations", fontWeight = FontWeight.Bold)
            recommendations.forEach { recommendation ->
                Text(recommendation.trimStart('-').trim(), color = Color(0xFF37474F))
            }
        }
    }
}

@Composable
private fun UnderstandingLevelsCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Understanding Light Levels", fontWeight = FontWeight.Bold)
            LevelRow(
                label = "Low Light (0–5,000 lux)",
                description = "Deep room interiors, far from windows",
                color = Color(0xFFFFCDD2)
            )
            LevelRow(
                label = "Medium Light (5,000–15,000 lux)",
                description = "Near bright windows with indirect light",
                color = Color(0xFFFFF59D)
            )
            LevelRow(
                label = "Bright Light (15,000+ lux)",
                description = "South-facing windows, filtered sun",
                color = Color(0xFFC8E6C9)
            )
        }
    }
}

@Composable
private fun LevelRow(label: String, description: String, color: Color) {
    Surface(
        color = color,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
            Text(description, color = Color(0xFF424242))
        }
    }
}

@Composable
private fun RecentMeasurementsCard(history: List<LightMeasurement>) {
    if (history.isEmpty()) return
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Recent Measurements", fontWeight = FontWeight.Bold)
            history.take(3).forEach { measurement ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("${measurement.luxValue.toInt()} lux", fontWeight = FontWeight.SemiBold)
                        Text(
                            formatTimestamp(measurement.measuredAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF6D6D6D)
                        )
                    }
                    Text(measurement.assessmentLabel, color = Color(0xFF00897B))
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, title: String, message: String) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text(message, textAlign = TextAlign.Center, color = Color.Gray)
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

