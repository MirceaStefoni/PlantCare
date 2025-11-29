package com.example.plantcare.ui.care

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.plantcare.domain.model.CareGuideFields
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

    val shareBody = state.instructions?.toShareText(state.plantName)
    val completedGroups = state.groupStates.values.count { it.status == CareGroupStatus.READY }
    val progressFraction = if (CareGuideGroups.isNotEmpty()) {
        completedGroups / CareGuideGroups.size.toFloat()
    } else 1f

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
            state.isBootstrapping -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
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
                    if (state.isSequentialLoading) {
                        LinearProgressIndicator(
                            progress = progressFraction.coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth()
                        )
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
                        isLoading = state.isSequentialLoading,
                        lastUpdated = state.lastUpdated
                    )

                    state.errorMessage?.let { message ->
                        Text(
                            text = message,
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    CareGuideGroups.forEach { definition ->
                        val groupState = state.groupStates[definition.id] ?: CareGroupState()
                        CareGuideGroupCard(
                            definition = definition,
                            values = definition.keys.associateWith { key -> state.fieldValues[key] },
                            state = groupState
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideSummaryCard(
    plantName: String,
    isLoading: Boolean,
    lastUpdated: Long?
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.EnergySavingsLeaf, contentDescription = null, tint = Color.White)
                val summary = when {
                    isLoading -> "Generating personalized tips…"
                    lastUpdated != null -> formatRelativeUpdate(lastUpdated)
                    else -> "Care tips will appear once generated"
                }
                Text(summary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CareGuideGroupCard(
    definition: CareGuideGroupDefinition,
    values: Map<String, String?>,
    state: CareGroupState
) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(definition.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(definition.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)

            definition.keys.forEach { key ->
                CareGuideItemRow(
                    label = CareGuideFieldLabels[key].orEmpty(),
                    value = values[key],
                    visual = fieldVisuals[key],
                    isLoading = state.status == CareGroupStatus.LOADING && values[key].isNullOrBlank()
                )
            }

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Red
                )
            }
        }
    }
}

@Composable
private fun CareGuideItemRow(
    label: String,
    value: String?,
    visual: FieldVisual?,
    isLoading: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            visual?.let { Icon(it.icon, contentDescription = null, tint = it.color) }
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        when {
            isLoading -> SkeletonParagraph()
            value.isNullOrBlank() -> Text("Details will appear soon.", color = Color.Gray)
            else -> CareBodyText(value)
        }
    }
}

@Composable
private fun SkeletonParagraph(lines: Int = 2) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(lines) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.LightGray.copy(alpha = 0.4f))
            )
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

private fun formatRelativeUpdate(timestamp: Long): String {
    val relative = DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    )
    return "Updated $relative"
}

private data class FieldVisual(val icon: ImageVector, val color: Color)

private val fieldVisuals: Map<String, FieldVisual> = mapOf(
    CareGuideFields.WATERING to FieldVisual(Icons.Filled.WaterDrop, Color(0xFF2196F3)),
    CareGuideFields.LIGHT to FieldVisual(Icons.Filled.WbSunny, Color(0xFFFFC107)),
    CareGuideFields.TEMPERATURE to FieldVisual(Icons.Filled.Thermostat, Color(0xFFFF7043)),
    CareGuideFields.HUMIDITY to FieldVisual(Icons.Filled.Cloud, Color(0xFF26C6DA)),
    CareGuideFields.SOIL to FieldVisual(Icons.Filled.TipsAndUpdates, Color(0xFF8D6E63)),
    CareGuideFields.FERTILIZATION to FieldVisual(Icons.Filled.EnergySavingsLeaf, Color(0xFF7CB342)),
    CareGuideFields.PRUNING to FieldVisual(Icons.Filled.TipsAndUpdates, Color(0xFF5C6BC0)),
    CareGuideFields.ISSUES to FieldVisual(Icons.Filled.EnergySavingsLeaf, Color(0xFFD32F2F)),
    CareGuideFields.SEASONAL to FieldVisual(Icons.Filled.TipsAndUpdates, Color(0xFFFFB74D))
)
