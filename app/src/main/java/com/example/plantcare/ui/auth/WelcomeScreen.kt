package com.example.plantcare.ui.auth

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.plantcare.R
import com.example.plantcare.ui.theme.ForestGreen
import com.example.plantcare.ui.theme.LightSage
import com.example.plantcare.ui.theme.TextSecondary
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onSignIn: () -> Unit
) {
    val context = LocalContext.current
    BackHandler { (context as? Activity)?.moveTaskToBack(true) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().weight(1f)) {
            Spacer(Modifier.height(56.dp))
            Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                val desiredId = remember { context.resources.getIdentifier("plant_welcome", "drawable", context.packageName) }
                val painter = if (desiredId != 0) painterResource(id = desiredId) else painterResource(id = R.drawable.ic_launcher_foreground)
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.LightMode, contentDescription = null, tint = ForestGreen)
                Text("PlantCare", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
            }
            Spacer(Modifier.height(28.dp))
            Column(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Your AI Plant Care Companion",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Identify, monitor, and nurture your plants with intelligent insights",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FeatureRow(icon = Icons.Default.CameraAlt, label = "Instant Plant ID")
                    FeatureRow(icon = Icons.Default.Favorite, label = "Health Monitoring")
                    FeatureRow(icon = Icons.Default.LightMode, label = "Light Analysis")
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = onGetStarted,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = Color.White)
            ) { Text("Get Started") }
            Spacer(Modifier.height(8.dp))
            Text(
                "Already have an account? Sign In",
                color = ForestGreen,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .padding(8.dp)
                    .clickable(onClick = onSignIn)
            )
        }
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(LightSage, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}


