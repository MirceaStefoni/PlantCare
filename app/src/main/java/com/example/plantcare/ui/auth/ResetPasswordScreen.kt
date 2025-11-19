package com.example.plantcare.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ResetPasswordScreen(
    onBackToSignIn: () -> Unit,
    vm: AuthViewModel = hiltViewModel()
) {
    val loading = vm.loading
    val error = vm.error

    var email by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.clearError() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Reset password")
        if (error.value != null) Text(error.value!!)
        if (confirmation != null) Text(confirmation!!)
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
        Button(onClick = {
            vm.sendReset(email)
            confirmation = "If an account exists, an email has been sent."
        }, enabled = !loading.value) { Text("Send reset email") }
        Button(onClick = onBackToSignIn, enabled = !loading.value) { Text("Back to sign in") }
        if (loading.value) { Spacer(Modifier.height(8.dp)); CircularProgressIndicator() }
    }
}


