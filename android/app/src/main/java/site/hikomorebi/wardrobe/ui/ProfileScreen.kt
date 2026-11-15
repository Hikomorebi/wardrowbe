package site.hikomorebi.wardrobe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.OffsetDateTime
import java.time.ZoneId
import site.hikomorebi.wardrobe.BuildConfig
import site.hikomorebi.wardrobe.data.*

@Composable
fun ProfileScreen(vm: AppViewModel, go: (String) -> Unit) {
    val session by vm.auth.session.collectAsStateWithLifecycle()
    val server by vm.auth.server.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val profile by vm.profileInfo.collectAsStateWithLifecycle()
    var loggingOut by remember { mutableStateOf(false) }
    var changingServer by remember { mutableStateOf(false) }
    var serverInput by rememberSaveable { mutableStateOf(server.trimEnd('/')) }
    var serverError by remember { mutableStateOf<String?>(null) }
    Page("我的") { padding ->
        Column(
            Modifier.padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedCard {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        (profile ?: session?.user)?.displayName.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        (profile ?: session?.user)?.email.orEmpty(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val until =
                        session?.sessionExpiresAt?.let {
                            runCatching {
                                OffsetDateTime.parse(it)
                                    .atZoneSameInstant(ZoneId.systemDefault())
                                    .toLocalDate()
                                    .toString()
                            }
                                .getOrNull()
                        }
                    if (until != null)
                        Text("本次登录有效至 $until", style = MaterialTheme.typography.bodySmall)
                }
            }
            ProfileLink("类别管理", "新增、修改或删除衣物类别", Icons.Default.Category, !busy) { go("categories") }
            ProfileLink("位置管理", "整理衣柜、抽屉等存放位置", Icons.Default.Place, !busy) { go("locations") }
            ProfileLink("修改密码", "修改后需要在各设备重新登录", Icons.Default.Lock, !busy) { go("password") }
            OutlinedCard {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("服务器", style = MaterialTheme.typography.labelLarge)
                    Text(server.trimEnd('/'), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { changingServer = true }, enabled = !busy) {
                        Text("修改服务器地址")
                    }
                }
            }
            OutlinedButton(
                onClick = { loggingOut = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("退出登录")
            }
            Text(
                "我的衣橱 ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (loggingOut)
        ConfirmDialog(
            "退出登录？",
            "会结束当前手机的登录状态，服务器上的衣物和搭配会保留。",
            busy,
            "退出登录",
            { loggingOut = false },
        ) {
            vm.submit(
                onError = {
                    loggingOut = false
                    vm.message(it)
                },
            ) {
                vm.auth.logout()
            }
        }
    if (changingServer)
        AlertDialog(
            onDismissRequest = { if (!busy) changingServer = false },
            title = { Text("修改服务器地址") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("更换地址后会退出当前账号，需要在新服务器重新登录。")
                    OutlinedTextField(
                        serverInput,
                        {
                            serverInput = it
                            serverError = null
                        },
                        label = { Text("HTTPS 服务器地址") },
                        enabled = !busy,
                        singleLine = true,
                    )
                    serverError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.submit(onError = { serverError = it }) {
                            val normalized = normalizedServer(serverInput, BuildConfig.DEBUG)
                            if (normalized != server) {
                                vm.auth.logout()
                                vm.auth.setServer(normalized)
                            }
                            changingServer = false
                        }
                    },
                    enabled = !busy,
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { changingServer = false }, enabled = !busy) { Text("取消") }
            },
        )
}

@Composable
private fun ProfileLink(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    click: () -> Unit,
) {
    OutlinedCard(onClick = click, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
        }
    }
}

@Composable
fun EntriesScreen(vm: AppViewModel, category: Boolean, back: () -> Unit) {
    val revision by vm.revision.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val label = if (category) "类别" else "位置"
    var editing by remember { mutableStateOf<NamedEntry?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<NamedEntry?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    Page(
        "${label}管理",
        back,
        floating = {
            FloatingActionButton(
                onClick = {
                    if (!busy) {
                        editing = null
                        name = ""
                        formError = null
                        showForm = true
                    }
                },
            ) {
                Icon(Icons.Default.Add, "新增$label")
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Loaded(category, revision, { vm.entries(category) }) { rows, _ ->
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (rows.isEmpty()) item { EmptyState("还没有$label", "点击右下角加号新增。") }
                    items(rows, key = { it.id }) { entry ->
                        OutlinedCard {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    entry.name,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                IconButton(
                                    onClick = {
                                        editing = entry
                                        name = entry.name
                                        formError = null
                                        showForm = true
                                    },
                                    enabled = !busy,
                                ) {
                                    Icon(Icons.Default.Edit, "修改${entry.name}")
                                }
                                IconButton(onClick = { deleting = entry }, enabled = !busy) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        "删除${entry.name}",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showForm)
        AlertDialog(
            onDismissRequest = { if (!busy) showForm = false },
            title = { Text(if (editing == null) "新增$label" else "修改$label") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        name,
                        {
                            name = it.take(100)
                            formError = null
                        },
                        label = { Text("${label}名称") },
                        singleLine = true,
                        enabled = !busy,
                    )
                    formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.submit(
                            onSuccess = {
                                showForm = false
                                vm.message("已保存")
                            },
                            onError = { formError = it },
                        ) {
                            vm.saveEntry(category, editing?.id, name)
                        }
                    },
                    enabled = !busy && name.isNotBlank(),
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForm = false }, enabled = !busy) { Text("取消") }
            },
        )
    deleting?.let { entry ->
        ConfirmDialog(
            "删除$label？",
            "将删除“${entry.name}”。如果仍有衣物使用此$label，需要先调整这些衣物。",
            busy,
            onDismiss = { deleting = null },
            onConfirm = {
                vm.submit(
                    onSuccess = {
                        deleting = null
                        vm.message("已删除")
                    },
                    onError = {
                        deleting = null
                        vm.message(it)
                    },
                ) {
                    vm.deleteEntry(category, entry.id)
                }
            },
        )
    }
}

@Composable
fun PasswordScreen(vm: AppViewModel, back: () -> Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var current by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    Page("修改密码", back) { padding ->
        Column(
            Modifier.padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Notice("密码需为 12～128 个字符。修改成功后，手机和网页都需要重新登录。")
            listOf(
                    Triple("当前密码", current, { value: String -> current = value }),
                    Triple("新密码", password, { value: String -> password = value }),
                    Triple("确认新密码", confirmation, { value: String -> confirmation = value }),
                )
                .forEach { (label, value, change) ->
                    OutlinedTextField(
                        value,
                        change,
                        label = { Text(label) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            if (confirmation.isNotEmpty() && confirmation != password)
                Text("两次输入的新密码不一致", color = MaterialTheme.colorScheme.error)
            BusyButton(
                "保存新密码",
                busy,
                current.isNotEmpty() && password.length in 12..128 && password == confirmation,
            ) {
                vm.submit(
                    onSuccess = {
                        current = ""
                        password = ""
                        confirmation = ""
                        vm.message("密码已修改，请重新登录")
                    },
                ) {
                    vm.changePassword(current, password)
                }
            }
        }
    }
}
