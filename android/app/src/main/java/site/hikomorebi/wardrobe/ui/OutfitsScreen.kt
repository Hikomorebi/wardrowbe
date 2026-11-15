package site.hikomorebi.wardrobe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import site.hikomorebi.wardrobe.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitsScreen(
    vm: AppViewModel,
    sourceId: String?,
    back: (() -> Unit)?,
    item: (String) -> Unit,
    add: () -> Unit,
    edit: (String) -> Unit,
) {
    val revision by vm.revision.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var rows by remember(sourceId) { mutableStateOf<List<Outfit>>(emptyList()) }
    var source by remember(sourceId) { mutableStateOf<ClothingItem?>(null) }
    var hasMore by remember(sourceId) { mutableStateOf(false) }
    var page by remember(sourceId) { mutableIntStateOf(1) }
    var reload by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Outfit?>(null) }
    LaunchedEffect(sourceId, revision, reload) { page = 1 }
    LaunchedEffect(sourceId, revision, reload, page) {
        loading = true
        error = null
        try {
            if (page == 1 && sourceId != null) source = vm.item(sourceId)
            val result = vm.outfits(page, sourceId)
            rows = if (page == 1) result.outfits else (rows + result.outfits).distinctBy { it.id }
            hasMore = result.hasMore
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            error = errorText(exception)
        } finally {
            loading = false
        }
    }
    Page(
        if (sourceId == null) "我的搭配" else "推荐搭配",
        back,
        actions = {
            IconButton(onClick = { reload++ }, enabled = !busy) {
                Icon(Icons.Default.Refresh, "刷新搭配")
            }
        },
        floating = {
            if (sourceId == null)
                FloatingActionButton(onClick = { if (!busy) add() }) {
                    Icon(Icons.Default.Add, "创建搭配")
                }
        },
    ) { padding ->
        PullToRefreshBox(loading, { reload++ }, Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (sourceId != null)
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "从已保存的组合中，查看与这件衣物配套的其他衣物。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            source?.let { current ->
                                OutlinedCard {
                                    Row(
                                        Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        RemoteImage(
                                            vm,
                                            current.thumbnailUrl ?: current.imageUrl,
                                            current.title,
                                            Modifier.size(70.dp),
                                        )
                                        Column {
                                            Text(
                                                "当前衣物",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                current.title,
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                            Text(
                                                current.categoryName ?: "类别未设置",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                error?.let { message -> item { Notice(message, "重试") { reload++ } } }
                if (rows.isEmpty() && !loading && error == null)
                    item {
                        EmptyState(
                            if (sourceId == null) "还没有保存的搭配" else "暂未找到推荐搭配",
                            if (sourceId == null) "点击加号，选择衣物保存组合。" else "先到“我的搭配”创建包含这件衣物的组合。",
                        )
                    }
                items(rows, key = { it.id }) { outfit ->
                    OutlinedCard {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Layers,
                                    null,
                                    Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    outfit.title,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (sourceId == null) {
                                    IconButton(onClick = { edit(outfit.id) }, enabled = !busy) {
                                        Icon(Icons.Default.Edit, "编辑${outfit.title}")
                                    }
                                    IconButton(onClick = { deleting = outfit }, enabled = !busy) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            "删除${outfit.title}",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                            val displayed =
                                if (sourceId == null) outfit.items else outfit.companions(sourceId)
                            if (displayed.isEmpty())
                                Text(
                                    "这个组合只有当前衣物，还没有配套衣物。",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            displayed.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    pair.forEach { clothing ->
                                        OutlinedCard(
                                            onClick = { if (!busy) item(clothing.id) },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            RemoteImage(
                                                vm,
                                                clothing.imageUrl ?: clothing.thumbnailUrl,
                                                clothing.title,
                                                Modifier.fillMaxWidth()
                                                    .aspectRatio(
                                                        if (displayed.size == 1) 1.6f else 1f,
                                                    ),
                                                onRetry = { reload++ },
                                            )
                                            Column(Modifier.padding(10.dp)) {
                                                Text(
                                                    clothing.title,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                clothing.categoryName?.let {
                                                    Text(
                                                        it,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color =
                                                            MaterialTheme.colorScheme
                                                                .onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (pair.size == 1 && displayed.size > 1)
                                        Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                if (hasMore)
                    item {
                        OutlinedButton(
                            onClick = { page++ },
                            enabled = !loading && !busy && error == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (loading) "加载中…" else "加载更多")
                        }
                    }
            }
        }
    }
    deleting?.let { outfit ->
        ConfirmDialog(
            "删除搭配？",
            "将删除“${outfit.title}”这个组合，组合中的衣物会保留。",
            busy,
            onDismiss = { deleting = null },
            onConfirm = {
                vm.submit(
                    onSuccess = {
                        deleting = null
                        vm.message("搭配已删除")
                    },
                    onError = {
                        deleting = null
                        vm.message(it)
                    },
                ) {
                    vm.deleteOutfit(outfit.id)
                }
            },
        )
    }
}
