package com.example.plantcare.ui.outdoor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.plantcare.domain.model.CityLocation
import com.example.plantcare.domain.model.OutdoorCheck
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ForestGreen = Color(0xFF2D6A4F)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun OutdoorCheckScreen(
    plantId: String,
    onBack: () -> Unit,
    viewModel: OutdoorCheckViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val locationPermission = rememberPermissionState(android.Manifest.permission.ACCESS_COARSE_LOCATION)

    LaunchedEffect(plantId) { viewModel.load(plantId) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Outdoor Suitability", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.plant == null) {
                HeaderSkeletonCard()
            } else {
                HeaderCard(
                    plantName = state.plant?.commonName ?: "Plant",
                    latest = state.latest
                )
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Run a new check", fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = state.cityQuery,
                        onValueChange = viewModel::setCityQuery,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search city") },
                        placeholder = { Text("e.g., Deva") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.cityQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.setCityQuery("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedIndicatorColor = ForestGreen,
                            focusedLeadingIconColor = ForestGreen,
                            focusedLabelColor = ForestGreen
                        ),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )

                    if (state.isSearchingCities) {
                        Text("Searching cities…", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }

                    if (state.citySuggestions.isNotEmpty()) {
                        CitySuggestionsCard(
                            suggestions = state.citySuggestions,
                            onPick = viewModel::onCitySelected
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { viewModel.runFromCity(plantId) },
                            enabled = !state.isLoading,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = ForestGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Check by City")
                        }

                        OutlinedButton(
                            onClick = {
                                if (locationPermission.status.isGranted) {
                                    viewModel.runFromCurrentLocation(plantId)
                                } else {
                                    locationPermission.launchPermissionRequest()
                                }
                            },
                            enabled = !state.isLoading,
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = ForestGreen),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ForestGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Use GPS")
                        }
                    }

                    if (state.isLoading) {
                        Text("Checking weather and analyzing…", color = Color.Gray)
                    }
                    if (state.errorMessage != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFD32F2F))
                            Spacer(Modifier.width(8.dp))
                            Text(state.errorMessage!!, color = Color(0xFFD32F2F))
                        }
                    }

                    if (!locationPermission.status.isGranted) {
                        Text(
                            "If GPS is unavailable/denied, city mode still works.",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            when {
                state.latest != null -> ResultCard(check = state.latest!!, isRefreshing = state.isLoading)
                state.isLoading -> ResultSkeletonCard()
            }

            when {
                state.history.isNotEmpty() -> HistoryCard(history = state.history, isRefreshing = state.isLoading)
                state.isLoading -> HistorySkeletonCard()
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeaderCard(plantName: String, latest: OutdoorCheck?) {
    val bg = Brush.verticalGradient(listOf(Color(0xFFE8F5E9), Color.Transparent))
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(bg)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(plantName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ForestGreen)
            Text(
                latest?.cityName?.let { "Last checked for $it" } ?: "No checks yet",
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun ResultCard(check: OutdoorCheck, isRefreshing: Boolean) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Result", fontWeight = FontWeight.Bold)
                    VerdictChip(check.verdict, check.verdictColor)
                }

                if (isRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = ForestGreen
                    )
                    Text(
                        "Updating… showing last result until the new one is ready.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text("Conditions", fontWeight = FontWeight.Bold)
                KeyValueRow("Location", check.cityName ?: String.format(Locale.US, "%.4f, %.4f", check.latitude, check.longitude))
                KeyValueRow("Temp", String.format(Locale.US, "%.1f°C", check.tempC))
                KeyValueRow("Feels like", String.format(Locale.US, "%.1f°C", check.feelsLikeC))
                KeyValueRow("Humidity", "${check.humidityPercent}%")
                KeyValueRow("Wind", String.format(Locale.US, "%.1f km/h", check.windKmh))
                KeyValueRow("Forecast low (24h)", check.minTempNext24hC?.let { String.format(Locale.US, "%.1f°C", it) } ?: "Unknown")
                if (!check.weatherDescription.isNullOrBlank()) {
                    KeyValueRow("Weather", check.weatherDescription!!)
                }

                if (check.analysis.isNotBlank()) {
                    Text("Summary", fontWeight = FontWeight.Bold)
                    Text(check.analysis, color = Color(0xFF37474F))
                }

                if (check.warnings.isNotEmpty()) {
                    SectionList(title = "Warnings", items = check.warnings)
                }
                if (check.recommendations.isNotEmpty()) {
                    SectionList(title = "Recommendations", items = check.recommendations)
                }

                Text(
                    "Checked at ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(check.checkedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            // A subtle overlay skeleton so it's obvious a refresh is in progress.
            if (isRefreshing) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.White.copy(alpha = 0.55f))
                )
                Column(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Spacer(Modifier.height(34.dp))
                    SkeletonBox(width = 120.dp, height = 14.dp)
                    repeat(4) { SkeletonBox(width = 280.dp, height = 12.dp) }
                    Spacer(Modifier.height(8.dp))
                    SkeletonBox(width = 160.dp, height = 14.dp)
                    SkeletonBox(width = 260.dp, height = 14.dp)
                }
            }
        }
    }
}

@Composable
private fun CitySuggestionsCard(
    suggestions: List<CityLocation>,
    onPick: (CityLocation) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(
                "Select the correct city",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = ForestGreen
            )
            suggestions.take(5).forEachIndexed { index, city ->
                val label = cityLabel(city)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(city) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, color = Color(0xFF263238), fontWeight = FontWeight.SemiBold)
                        Text(
                            String.format(Locale.US, "%.4f, %.4f", city.latitude, city.longitude),
                            color = Color.Gray,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text("Pick", color = ForestGreen, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                if (index != suggestions.take(5).lastIndex) {
                    Spacer(Modifier.height(1.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFEAEAEA))
                    )
                }
            }
        }
    }
}

private fun cityLabel(city: CityLocation): String {
    val parts = buildList {
        add(city.name)
        city.state?.takeIf { it.isNotBlank() }?.let { add(it) }
        city.country?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    return parts.joinToString(", ")
}

@Composable
private fun HistoryCard(history: List<OutdoorCheck>, isRefreshing: Boolean) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("History", fontWeight = FontWeight.Bold)
                if (isRefreshing) {
                    Text("Updating…", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                }
            }
            history.take(10).forEach { item ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(item.checkedAt)),
                            color = Color.DarkGray
                        )
                        VerdictChip(label = item.verdict, color = item.verdictColor)
                    }
                    Text(
                        item.cityName ?: String.format(Locale.US, "%.4f, %.4f", item.latitude, item.longitude),
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (history.size > 10) Text("Showing latest 10", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray)
        Spacer(Modifier.width(10.dp))
        Text(value, color = Color.DarkGray, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun VerdictChip(label: String, color: String) {
    val c = when (color.lowercase()) {
        "green" -> Color(0xFF2E7D32)
        "red" -> Color(0xFFC62828)
        else -> Color(0xFFF57C00)
    }
    Card(
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(containerColor = c.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = c,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun SectionList(title: String, items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        items.forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                Text("•", modifier = Modifier.width(14.dp), color = ForestGreen, fontWeight = FontWeight.Bold)
                Text(line, color = Color(0xFF37474F))
            }
        }
    }
}

@Composable
private fun HeaderSkeletonCard() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SkeletonBox(width = 180.dp, height = 22.dp)
            SkeletonBox(width = 220.dp, height = 14.dp)
        }
    }
}

@Composable
private fun ResultSkeletonCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBox(width = 90.dp, height = 18.dp)
                SkeletonBox(width = 64.dp, height = 28.dp, radius = 999.dp)
            }
            SkeletonBox(width = 120.dp, height = 14.dp)
            repeat(6) { SkeletonBox(width = 280.dp, height = 12.dp) }
            Spacer(Modifier.height(6.dp))
            SkeletonBox(width = 200.dp, height = 14.dp)
            SkeletonBox(width = 260.dp, height = 14.dp)
        }
    }
}

@Composable
private fun HistorySkeletonCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonBox(width = 90.dp, height = 18.dp)
            repeat(3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SkeletonBox(width = 70.dp, height = 12.dp)
                    SkeletonBox(width = 64.dp, height = 28.dp, radius = 999.dp)
                }
                SkeletonBox(width = 160.dp, height = 10.dp)
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun SkeletonBox(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp = 6.dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(brush = skeletonBrush(), shape = RoundedCornerShape(radius))
    )
}

@Composable
private fun skeletonBrush(): Brush {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    )
    return Brush.horizontalGradient(shimmerColors)
}


