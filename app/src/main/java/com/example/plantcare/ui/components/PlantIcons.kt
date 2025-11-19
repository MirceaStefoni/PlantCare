package com.example.plantcare.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Yard
import androidx.compose.ui.graphics.vector.ImageVector

fun getPlantIconById(iconId: Int): ImageVector {
    return when (iconId % 6) {
        0 -> Icons.Filled.Eco
        1 -> Icons.Filled.LocalFlorist
        2 -> Icons.Filled.Yard
        3 -> Icons.Filled.Park
        4 -> Icons.Filled.FilterVintage
        5 -> Icons.Filled.Grass
        else -> Icons.Filled.Eco
    }
}

