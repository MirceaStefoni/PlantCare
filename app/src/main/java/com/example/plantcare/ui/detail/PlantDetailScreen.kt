package com.example.plantcare.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PlantDetailScreen(plantId: String) {
    var care by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(plantId) {
        care = null // placeholder; load from Room or fetch via API later
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Plant $plantId", style = MaterialTheme.typography.headlineMedium)
        ElevatedCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Care Guide", style = MaterialTheme.typography.titleMedium)
                if (care == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxSize())
                } else {
                    Text(care!!)
                }
                OutlinedButton(onClick = { /* refresh */ }) { Text("Refresh") }
            }
        }
    }
}


