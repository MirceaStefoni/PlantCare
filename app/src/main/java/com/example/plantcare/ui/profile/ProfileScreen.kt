package com.example.plantcare.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.text.DateFormat

@Composable
fun ProfileScreen(onLogout: () -> Unit, onAccountDeleted: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val user = vm.user
    val loading = vm.loading
    val error = vm.error
    val createdAt = vm.createdAt

    var name by remember { mutableStateOf("") }
    var avatarPath by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(user.value) { name = user.value?.displayName ?: "" }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Profile")
        if (error.value != null) Text(error.value!!)
        Text("Email: ${user.value?.email ?: "-"}")
        Text("Created: ${createdAt.value?.let { DateFormat.getDateTimeInstance().format(it) } ?: "-"}")
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display name") })
        Button(onClick = { vm.updateDisplayName(name) }, enabled = !loading.value) { Text("Save") }
        OutlinedTextField(value = avatarPath, onValueChange = { avatarPath = it }, label = { Text("Avatar local path (demo)") })
        OutlinedButton(onClick = { vm.updateAvatar(avatarPath) }, enabled = !loading.value) { Text("Upload avatar") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.logout(); onLogout() }, enabled = !loading.value) { Text("Logout") }
        if (!confirmDelete) {
            OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete account") }
        } else {
            Button(onClick = { scope.launch { vm.deleteAccount(); onAccountDeleted() } }) { Text("Confirm delete") }
        }
        if (loading.value) { Spacer(Modifier.height(8.dp)); CircularProgressIndicator() }
    }
}


