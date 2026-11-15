package site.hikomorebi.wardrobe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import site.hikomorebi.wardrobe.data.*

private data class OutfitDraft(val outfit: Outfit?, val selected: List<ClothingItem>)

@Composable
fun OutfitEditorScreen(vm: AppViewModel, id: String?, back: () -> Unit, saved: () -> Unit) {
    Page(if (id == null) "创建搭配" else "编辑搭配", back) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Loaded(
                id,
                loader = {
                    val outfit = id?.let { vm.outfit(it) }
                    // Fetch full items: outfit summaries intentionally omit archive/status fields.
                    val selected =
                        if (outfit == null) emptyList()
                        else
                            coroutineScope {
                                outfit.items.map { async { vm.item(it.id) } }.awaitAll()
                            }
                    OutfitDraft(outfit, selected)
                },
            ) { draft, _ ->
                OutfitForm(vm, draft, saved)
            }
        }
    }
}

@Composable
private fun OutfitForm(vm: AppViewModel, draft: OutfitDraft, saved: () -> Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var name by rememberSaveable(draft.outfit?.id) { mutableStateOf(draft.outfit?.name.orEmpty()) }
    var selectedJson by
        rememberSaveable(draft.outfit?.id) { mutableStateOf(apiJson.toJson(draft.selected)) }
    val selected =
        remember(selectedJson) {
            apiJson.fromJson(selectedJson, Array<ClothingItem>::class.java).toList()
        }
    var search by rememberSaveable { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    var retry by remember { mutableIntStateOf(0) }
    var rows by remember { mutableStateOf<List<ClothingItem>>(emptyList()) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(search) { page = 1 }
    LaunchedEffect(search, page, retry) {
        loading = true
        error = null
        try {
            delay(300)
            val result = vm.pickerItems(search, page)
            rows = if (page == 1) result.items else (rows + result.items).distinctBy { it.id }
            hasMore = result.hasMore
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            error = errorText(exception)
        } finally {
            loading = false
        }
    }
    fun toggle(item: ClothingItem) {
        if (busy) return
        val already = selected.any { it.id == item.id }
        if (!already && selected.size >= 20) {
            vm.message("每个搭配最多选择 20 件衣物")
            return
        }
        selectedJson =
            apiJson.toJson(
                if (already) selected.filterNot { it.id == item.id } else selected + item,
            )
    }
    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                name,
                { name = it.take(100) },
                label = { Text("搭配名称") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { Text("已选 ${selected.size} 件", style = MaterialTheme.typography.titleMedium) }
        if (selected.isEmpty())
            item { Text("从下方衣物列表中选择组合。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(selected, key = { "selected-${it.id}" }) { item ->
            OutlinedCard {
                Row(
                    Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RemoteImage(
                        vm,
                        item.thumbnailUrl ?: item.imageUrl,
                        item.title,
                        Modifier.size(54.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (item.isArchived || item.status != "ready")
                            Text(
                                "已归档或暂不可用，请恢复后再保存，或从组合移除",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                    }
                    IconButton(onClick = { toggle(item) }, enabled = !busy) {
                        Icon(Icons.Default.Close, "移除${item.title}")
                    }
                }
            }
        }
        item {
            BusyButton(
                "保存搭配",
                busy,
                name.isNotBlank() &&
                    selected.isNotEmpty() &&
                    selected.none { it.isArchived || it.status != "ready" },
            ) {
                vm.submit(
                    onSuccess = {
                        saved()
                        vm.message("搭配已保存")
                    },
                ) {
                    for (chosen in selected) {
                        val current = vm.item(chosen.id)
                        require(!current.isArchived && current.status == "ready") {
                            "“${current.title}”已归档或暂不可用，请先恢复或移出组合"
                        }
                    }
                    vm.saveOutfit(draft.outfit?.id, name, selected.map { it.id })
                }
            }
        }
        item {
            HorizontalDivider()
            Text("选择衣物", style = MaterialTheme.typography.titleMedium)
        }
        item {
            OutlinedTextField(
                search,
                { search = it },
                label = { Text("搜索可用衣物") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let { message -> item { Notice(message, "重试") { retry++ } } }
        items(rows, key = { "picker-${it.id}" }) { item ->
            val checked = selected.any { it.id == item.id }
            Row(
                Modifier.fillMaxWidth()
                    .clickable(enabled = !busy && !loading) { toggle(item) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RemoteImage(
                    vm,
                    item.thumbnailUrl ?: item.imageUrl,
                    item.title,
                    Modifier.size(58.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.categoryName ?: "类别未设置", style = MaterialTheme.typography.bodySmall)
                }
                Checkbox(checked, onCheckedChange = { toggle(item) }, enabled = !busy && !loading)
            }
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (hasMore)
            item {
                OutlinedButton(
                    onClick = { page++ },
                    enabled = !loading && !busy && error == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("加载更多衣物")
                }
            }
        if (rows.isEmpty() && !loading && error == null) item { Text("没有找到可用衣物，请先添加衣物或调整搜索条件。") }
    }
}
