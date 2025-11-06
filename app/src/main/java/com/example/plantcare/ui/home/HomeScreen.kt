package com.example.plantcare.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class PlantUi(val id: String, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenPlant: (String) -> Unit, onOpenProfile: () -> Unit) {
    var plants by remember { mutableStateOf<List<PlantUi>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        plants = emptyList() // placeholder; load from repository later
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("PlantCare") }, actions = { androidx.compose.material3.TextButton(onClick = onOpenProfile) { Text("Profile") } }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
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
                items(plants) { p ->
                    Card(onClick = { onOpenPlant(p.id) }) {
                        Text(p.name, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }

        if (showAdd) {
            Dialog(onDismissRequest = { showAdd = false }) {
                Surface {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Add Plant")
                        androidx.compose.material3.Button(onClick = { /* Add by Photo */ showAdd = false }) {
                            Text("Add by Photo")
                        }
                        androidx.compose.material3.OutlinedButton(onClick = { /* Add by Name */ showAdd = false }) {
                            Text("Add by Name")
                        }
                    }
                }
            }
        }
    }
}


