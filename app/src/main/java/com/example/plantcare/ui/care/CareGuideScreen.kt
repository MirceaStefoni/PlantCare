package com.example.plantcare.ui.care

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.plantcare.domain.model.CareInstructions
import com.example.plantcare.ui.theme.ForestGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareGuideScreen(
    onBack: () -> Unit,
    viewModel: CareGuideViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val shareBody = remember(state.instructions, state.plantName) {
        state.instructions?.toShareText(state.plantName)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Care Guide") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (!shareBody.isNullOrBlank()) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareBody)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share care guide"))
                            }
                        },
                        enabled = !shareBody.isNullOrBlank()
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
            )
        }
    ) { padding ->
        when {
            state.isLoading && state.instructions == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.errorMessage != null && state.instructions == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.errorMessage ?: "Something went wrong",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = { viewModel.refresh() }) {
                            Text("Try again")
                        }
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (state.isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    Text(
                        text = state.plantName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    state.scientificName?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }

                    GuideSummaryCard(
                        plantName = state.plantName,
                        instructions = state.instructions
                    )

                    state.errorMessage?.let { message ->
                        Text(
                            text = message,
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    val sections = buildSections(state.instructions)
                    sections.forEach { section ->
                        CareSectionCard(section = section)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideSummaryCard(
    plantName: String,
    instructions: CareInstructions?
) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = ForestGreen, contentColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Complete Care Guide", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Everything you need to keep $plantName thriving",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
            Divider(color = Color.White.copy(alpha = 0.2f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.EnergySavingsLeaf, contentDescription = null, tint = Color.White)
                }
                Text(
                    instructions?.updatedDescription() ?: "Generating recommendations…",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private data class CareSectionUiModel(
    val title: String,
    val description: String?,
    val icon: ImageVector,
    val chipColor: Color
)

private fun buildSections(instructions: CareInstructions?): List<CareSectionUiModel> {
    if (instructions == null) return emptyList()
    return listOf(
        CareSectionUiModel("Watering", instructions.wateringInfo, Icons.Filled.WaterDrop, Color(0xFF2196F3)),
        CareSectionUiModel("Light Requirements", instructions.lightInfo, Icons.Filled.WbSunny, Color(0xFFFFC107)),
        CareSectionUiModel("Temperature", instructions.temperatureInfo, Icons.Filled.Thermostat, Color(0xFFFF7043)),
        CareSectionUiModel("Humidity", instructions.humidityInfo, Icons.Filled.Cloud, Color(0xFF26C6DA)),
        CareSectionUiModel("Soil & Potting", instructions.soilInfo, Icons.Filled.TipsAndUpdates, Color(0xFF8D6E63)),
        CareSectionUiModel("Fertilization", instructions.fertilizationInfo, Icons.Filled.EnergySavingsLeaf, Color(0xFF7CB342)),
        CareSectionUiModel("Pruning", instructions.pruningInfo, Icons.Filled.TipsAndUpdates, Color(0xFF5C6BC0)),
        CareSectionUiModel("Common Problems", instructions.commonIssues, Icons.Filled.BugReport, Color(0xFFD32F2F)),
        CareSectionUiModel("Seasonal Tips", instructions.seasonalTips, Icons.Filled.TipsAndUpdates, Color(0xFFFFB74D))
    ).filter { !it.description.isNullOrBlank() }
}

@Composable
private fun CareSectionCard(section: CareSectionUiModel) {
    var expanded by rememberSaveable(section.title) { mutableStateOf(true) }
    val chevronRotation by animateFloatAsState(targetValue = if (expanded) 0f else 180f, label = "chevron")

    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color.Transparent)
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(section.chipColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(section.icon, contentDescription = null, tint = section.chipColor)
                }
                Spacer(modifier = Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier
                            .graphicsLayer { rotationZ = chevronRotation }
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    section.description?.let { CareBodyText(it) }
                }
            }
        }
    }
}

@Composable
private fun CareBodyText(text: String) {
    val lines = remember(text) {
        text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { line ->
            if (line.startsWith("-")) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("•", color = ForestGreen, fontWeight = FontWeight.Bold)
                    Text(line.removePrefix("-").trim(), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(line, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private fun CareInstructions.updatedDescription(): String {
    val relative = DateUtils.getRelativeTimeSpanString(
        fetchedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    )
    return "Updated $relative"
}

private fun CareInstructions.toShareText(plantName: String): String =
    buildString {
        append("Care guide for ").append(plantName).appendLine()
        appendLine()
        fun appendSection(title: String, body: String?) {
            if (body.isNullOrBlank()) return
            append(title).appendLine(":")
            append(body.trim()).appendLine().appendLine()
        }
        appendSection("Watering", wateringInfo)
        appendSection("Light", lightInfo)
        appendSection("Temperature", temperatureInfo)
        appendSection("Humidity", humidityInfo)
        appendSection("Soil & Potting", soilInfo)
        appendSection("Fertilization", fertilizationInfo)
        appendSection("Pruning", pruningInfo)
        appendSection("Common Problems", commonIssues)
        appendSection("Seasonal Tips", seasonalTips)
    }.trim()


