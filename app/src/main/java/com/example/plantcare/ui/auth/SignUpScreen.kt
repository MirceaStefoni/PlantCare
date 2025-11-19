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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.plantcare.R
import com.example.plantcare.ui.theme.ForestGreen
import com.example.plantcare.ui.theme.TextSecondary
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

@Composable
fun SignUpScreen(
    onNavigateHome: () -> Unit,
    onOpenSignIn: () -> Unit,
    onBack: () -> Unit,
    vm: AuthViewModel = hiltViewModel()
) {
    val session by vm.session.collectAsState(initial = null)
    val loading by vm.loading.collectAsState(initial = false)
    val error by vm.error.collectAsState(initial = null)

    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    val nameValid = displayName.length in 2..50
    val emailValid = email.contains('@') && email.contains('.') && email.length in 5..254
    val passLen = password.length >= 8
    val passUpper = password.any { it.isUpperCase() }
    val passDigit = password.any { it.isDigit() }
    val passValid = passLen && passUpper && passDigit
    val confirmValid = confirm == password && confirm.isNotEmpty()

    val canSubmit = nameValid && emailValid && passValid && confirmValid && !loading

    val strength = listOf(passLen, passUpper, passDigit).count { it } / 3f
    val strengthLabel = when {
        strength <= 0.34f -> "Weak"
        strength <= 0.67f -> "Medium"
        else -> "Strong"
    }

    LaunchedEffect(session) {
        if (session != null) onNavigateHome()
    }
    LaunchedEffect(Unit) { vm.clearError() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) }
            Spacer(Modifier.size(8.dp))
            Text("Create Account", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        }

        Spacer(Modifier.height(16.dp))
        Text("Join PlantCare", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
        Text("Start your plant care journey today", color = TextSecondary)

        Spacer(Modifier.height(16.dp))
        if (error != null) { Text(error!!, color = Color.Red); Spacer(Modifier.height(8.dp)) }

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it; vm.clearError() },
            label = { Text("Display name") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary) },
            supportingText = { Text("2-50 characters", color = TextSecondary) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; vm.clearError() },
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = TextSecondary) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; vm.clearError() },
            label = { Text("Create password") },
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(progress = { strength }, modifier = Modifier.weight(1f))
            Text(strengthLabel, color = TextSecondary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
            RuleRow("At least 8 characters", passLen)
            RuleRow("One uppercase letter", passUpper)
            RuleRow("One number", passDigit)
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it; vm.clearError() },
            label = { Text("Confirm password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondary) },
            trailingIcon = {
                Icon(
                    imageVector = if (showConfirm) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.clickable { showConfirm = !showConfirm },
                    tint = TextSecondary
                )
            },
            visualTransformation = if (showConfirm) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.signUp(email, password, displayName) },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = Color.White)
        ) { Text("Create Account") }

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
                if (idToken != null) vm.googleSignIn(idToken, rememberMe = true)
            } catch (_: Exception) {}
        }
        OutlinedButton(onClick = { googleLauncher.launch(googleClient.signInIntent) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(painter = painterResource(id = R.drawable.google_logo), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Sign up with Google")
        }

        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("Already have an account? ")
            Text("Sign In", color = ForestGreen, textDecoration = TextDecoration.Underline, modifier = Modifier.clickable { onOpenSignIn() })
        }

        if (loading) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun RuleRow(text: String, ok: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        val color = if (ok) ForestGreen else Color(0xFFD32F2F)
        Box(modifier = Modifier.size(6.dp).background(color = color, shape = CircleShape))
        Text(text, color = if (ok) Color.Unspecified else Color(0xFFD32F2F))
    }
}
