package site.hikomorebi.wardrobe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import site.hikomorebi.wardrobe.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeScreen(
    vm: AppViewModel,
    onAdd: () -> Unit,
    onItem: (String) -> Unit,
    onMatching: (String) -> Unit,
) {
    val state by vm.wardrobe.collectAsStateWithLifecycle()
    val revision by vm.revision.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    var filterOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ClothingItem?>(null) }
    LaunchedEffect(revision) { vm.loadItems() }
    Page(
        if (state.filters.archived) "已归档衣物" else "我的衣橱",
        actions = {
            IconButton(onClick = { filterOpen = true }, enabled = !busy) {
                Icon(Icons.Default.Tune, "筛选衣物")
            }
            IconButton(onClick = { vm.refresh() }, enabled = !busy) {
                Icon(Icons.Default.Refresh, "刷新衣橱")
            }
        },
        floating = {
            if (!state.filters.archived)
                FloatingActionButton(onClick = { if (!busy) onAdd() }) {
                    Icon(Icons.Default.Add, "新增衣物")
                }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.filters.search,
                onValueChange = { vm.setFilters(state.filters.copy(search = it), debounce = true) },
                singleLine = true,
                placeholder = { Text("搜索衣物名称、备注") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                enabled = !busy,
                trailingIcon = {
                    if (state.filters.search.isNotEmpty())
                        IconButton(onClick = { vm.setFilters(state.filters.copy(search = "")) }) {
                            Icon(Icons.Default.Close, "清除搜索")
                        }
                },
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "共 ${state.total} 件衣物",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.filters != ItemFilters())
                    TextButton(onClick = { vm.setFilters(ItemFilters()) }, enabled = !busy) {
                        Text("重置筛选")
                    }
            }
            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = { vm.refresh() },
                modifier = Modifier.weight(1f),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(168.dp),
                    contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.error?.let { error ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Notice(error, "重试") { vm.loadItems() }
                        }
                    }
                    if (state.items.isEmpty() && !state.loading && state.error == null)
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(
                                "还没有衣物",
                                if (state.filters == ItemFilters()) "点击右下角加号，拍照或选图添加第一件衣物。"
                                else "试试调整筛选条件。",
                            )
                        }
                    items(state.items, key = { it.id }) { item ->
                        ClothingCard(
                            vm,
                            item,
                            locations.firstOrNull { it.id == item.storageLocationId }?.name,
                            busy,
                            { onItem(item.id) },
                            { onMatching(item.id) },
                            {
                                vm.submit(
                                    onSuccess = {
                                        vm.message(if (item.isArchived) "衣物已恢复" else "衣物已归档")
                                    },
                                ) {
                                    vm.archive(item.id, item.isArchived)
                                }
                            },
                            { deleting = item },
                        )
                    }
                    if (state.hasMore)
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            OutlinedButton(
                                onClick = { vm.loadItems(reset = false) },
                                enabled = !state.loading && !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (state.loading) "加载中…" else "加载更多")
                            }
                        }
                }
            }
        }
    }
    if (filterOpen)
        FilterSheet(state.filters, categories, locations, { filterOpen = false }) {
            vm.setFilters(it)
            filterOpen = false
        }
    deleting?.let { item ->
        ConfirmDialog(
            "删除衣物？",
            "“${item.title}”及其图片将被永久删除。只是不再使用时，可以选择归档。",
            busy,
            onDismiss = { deleting = null },
            onConfirm = {
                vm.submit(
                    onSuccess = {
                        deleting = null
                        vm.message("衣物已删除")
                    },
                    onError = {
                        deleting = null
                        vm.message(it)
                    },
                ) {
                    vm.deleteItem(item.id)
                }
            },
        )
    }
}

@Composable
fun ClothingCard(
    vm: AppViewModel,
    item: ClothingItem,
    location: String?,
    busy: Boolean,
    onDetail: () -> Unit,
    onMatching: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().testTag("clothing-${item.id}")) {
        RemoteImage(
            vm,
            item.mediumUrl ?: item.imageUrl,
            item.title,
            Modifier.fillMaxWidth()
                .aspectRatio(4f / 3)
                .clickable(enabled = !busy, onClick = onDetail),
        )
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                item.title,
                Modifier.clickable(enabled = !busy, onClick = onDetail),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                item.categoryName ?: "类别未设置",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            item.condition?.let {
                Text("成色：${conditions[it] ?: "未设置"}", style = MaterialTheme.typography.bodySmall)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Place,
                    null,
                    Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    location ?: "位置未设置",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SeasonBadges(item.season)
            Button(
                onClick = onMatching,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(Icons.Default.Layers, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("推荐搭配")
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onArchive,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Icon(
                        if (item.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                        null,
                        Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (item.isArchived) "恢复" else "归档")
                }
                OutlinedIconButton(onClick = onDelete, enabled = !busy) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        "删除${item.title}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    current: ItemFilters,
    categories: List<NamedEntry>,
    locations: List<NamedEntry>,
    onDismiss: () -> Unit,
    apply: (ItemFilters) -> Unit,
) {
    var draft by remember { mutableStateOf(current) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("筛选衣物", style = MaterialTheme.typography.titleLarge)
            ChoiceField("类别", draft.category, categories.map { it.id to it.name }, "全部类别") {
                draft = draft.copy(category = it)
            }
            ChoiceField("成色", draft.condition, conditions.toList(), "全部成色") {
                draft = draft.copy(condition = it)
            }
            ChoiceField("存放位置", draft.location, locations.map { it.id to it.name }, "全部位置") {
                draft = draft.copy(location = it)
            }
            ChoiceField("适合季节", draft.season, seasons.toList(), "全部季节") {
                draft = draft.copy(season = it)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("查看已归档衣物", Modifier.weight(1f))
                Switch(
                    checked = draft.archived,
                    onCheckedChange = { draft = draft.copy(archived = it) },
                )
            }
            BusyButton("应用筛选", false) { apply(draft) }
            TextButton(
                onClick = { draft = ItemFilters(search = current.search) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("重置条件")
            }
        }
    }
}
