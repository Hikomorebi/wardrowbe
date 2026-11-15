package site.hikomorebi.wardrobe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LoginScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val configured by vm.auth.server.collectAsStateWithLifecycle()
    var server by rememberSaveable { mutableStateOf(configured.trimEnd('/')) }
    var email by rememberSaveable { mutableStateOf("") }
    // Password intentionally stays out of saved-instance state.
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(76.dp).align(Alignment.CenterHorizontally),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Checkroom, null, Modifier.size(40.dp))
            }
        }
        Text("我的衣橱", style = MaterialTheme.typography.headlineLarge)
        Text("衣物与搭配，随手整理。登录后与电脑端同步。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text("服务器地址") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("邮箱") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !busy,
            visualTransformation =
                if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        if (visible) "隐藏密码" else "显示密码",
                    )
                }
            },
        )
        BusyButton("登录", busy, email.isNotBlank() && password.isNotEmpty()) {
            vm.submit(onSuccess = { password = "" }) {
                vm.auth.setServer(server)
                vm.auth.login(email, password)
            }
        }
        Text(
            "使用与电脑端相同的账号登录。查看与保存衣物需要网络连接。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
