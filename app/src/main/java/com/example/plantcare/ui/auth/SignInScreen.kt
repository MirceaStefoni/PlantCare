package com.example.plantcare.ui.auth

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.plantcare.R
import com.example.plantcare.ui.theme.ForestGreen
import com.example.plantcare.ui.theme.LightSage
import com.example.plantcare.ui.theme.TextSecondary
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

@Composable
fun SignInScreen(
    onNavigateHome: () -> Unit,
    onOpenSignUp: () -> Unit,
    onOpenReset: () -> Unit,
    vm: AuthViewModel = hiltViewModel()
) {
    val session by vm.session.collectAsState(initial = null)
    val loading by vm.loading.collectAsState(initial = false)
    val error by vm.error.collectAsState(initial = null)

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }

    LaunchedEffect(session) {
        if (session != null) onNavigateHome()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp)) {
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(ForestGreen, shape = CircleShape)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Eco, contentDescription = null, tint = Color.White) }

        Spacer(Modifier.height(12.dp))
        Text("Welcome Back", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("Sign in to continue caring for your plants", color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally))

        Spacer(Modifier.height(24.dp))
        if (error != null) {
            Text(error!!, color = Color.Red)
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = TextSecondary) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondary) },
            trailingIcon = {
                Icon(
                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.clickable { showPassword = !showPassword },
                    tint = TextSecondary
                )
            },
            visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it })
            Text("Remember me")
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.signIn(email, password, rememberMe) },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = Color.White)
        ) { Text("Sign In") }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Divider(modifier = Modifier.weight(1f))
            Text("  OR  ", color = TextSecondary)
            Divider(modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))
        val context = LocalContext.current
        val gso = remember {
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(com.example.plantcare.R.string.default_web_client_id))
                .requestEmail()
                .build()
        }
        val googleClient = remember { GoogleSignIn.getClient(context, gso) }
        val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) vm.googleSignIn(idToken, rememberMe)
            } catch (_: Exception) {}
        }
        OutlinedButton(
            onClick = { googleLauncher.launch(googleClient.signInIntent) },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            val googleId = R.drawable.google_logo
            Icon(painter = painterResource(id = googleId), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Continue with Google")
        }

        Spacer(Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("Don't have an account? ")
            Text(
                "Sign Up",
                color = ForestGreen,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onOpenSignUp() }
            )
        }

        if (loading) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
