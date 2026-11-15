package site.hikomorebi.wardrobe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import site.hikomorebi.wardrobe.data.*

@Composable
fun ItemDetailScreen(
    vm: AppViewModel,
    id: String,
    back: () -> Unit,
    edit: () -> Unit,
    matching: () -> Unit,
) {
    val revision by vm.revision.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    Page(
        "衣物详情",
        back,
        actions = {
            TextButton(onClick = edit, enabled = !busy) {
                Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                Text("编辑")
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Loaded(id, revision, { vm.item(id) }) { item, reload ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RemoteImage(
                        vm,
                        item.imageUrl,
                        item.title,
                        Modifier.fillMaxWidth().aspectRatio(1f).clickable { fullscreen = true },
                        reload,
                        ContentScale.Fit,
                    )
                    Text(item.title, style = MaterialTheme.typography.headlineSmall)
                    if (item.isArchived) Notice("这件衣物已归档。恢复后可以用于创建新的搭配。")
                    OutlinedCard {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            DetailRow("类别", item.categoryName ?: "未设置")
                            DetailRow("成色", conditions[item.condition] ?: "未设置")
                            DetailRow(
                                "存放位置",
                                locations.firstOrNull { it.id == item.storageLocationId }?.name
                                    ?: "未设置",
                            )
                            Text("适合季节", style = MaterialTheme.typography.labelLarge)
                            SeasonBadges(item.season)
                            item.notes
                                ?.takeIf { it.isNotBlank() }
                                ?.let {
                                    Text("备注", style = MaterialTheme.typography.labelLarge)
                                    Text(it)
                                }
                        }
                    }
                    BusyButton("推荐搭配", busy, onClick = matching)
                    OutlinedButton(
                        onClick = {
                            vm.submit(
                                onSuccess = {
                                    vm.message(if (item.isArchived) "衣物已恢复" else "衣物已归档")
                                },
                            ) {
                                vm.archive(id, item.isArchived)
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            if (item.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                            null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (item.isArchived) "恢复衣物" else "归档衣物")
                    }
                    TextButton(
                        onClick = { deleting = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("删除衣物", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (deleting)
                    ConfirmDialog(
                        "永久删除衣物？",
                        "衣物信息与图片无法恢复。如需保留记录，请选择归档。",
                        busy,
                        onDismiss = { deleting = false },
                        onConfirm = {
                            vm.submit(
                                onSuccess = {
                                    deleting = false
                                    back()
                                    vm.message("衣物已删除")
                                },
                                onError = {
                                    deleting = false
                                    vm.message(it)
                                },
                            ) {
                                vm.deleteItem(id)
                            }
                        },
                    )
                if (fullscreen) FullImage(vm, item.imageUrl, item.title) { fullscreen = false }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            Modifier.width(80.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun FullImage(vm: AppViewModel, path: String, name: String, close: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var x by remember { mutableFloatStateOf(0f) }
    var y by remember { mutableFloatStateOf(0f) }
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            RemoteImage(
                vm,
                path,
                name,
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale == 1f) {
                                x = 0f
                                y = 0f
                            } else {
                                x = (x + pan.x).coerceIn(-size.width * 2f, size.width * 2f)
                                y = (y + pan.y).coerceIn(-size.height * 2f, size.height * 2f)
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = x,
                        translationY = y,
                    ),
                scale = ContentScale.Fit,
            )
            IconButton(
                onClick = close,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding(),
            ) {
                Icon(Icons.Default.Close, "关闭大图", tint = Color.White)
            }
        }
    }
}
