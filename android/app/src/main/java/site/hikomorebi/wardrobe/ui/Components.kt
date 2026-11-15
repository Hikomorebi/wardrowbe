package site.hikomorebi.wardrobe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import site.hikomorebi.wardrobe.data.*

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF5265A8),
        secondary = Color(0xFF58617C),
        background = Color(0xFFF7F8FC),
        surface = Color(0xFFFDFBFF),
    )
private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFB8C4FF),
        secondary = Color(0xFFC1C6DE),
        background = Color(0xFF111318),
        surface = Color(0xFF1B1D24),
    )

@Composable
fun WardrobeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Page(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floating: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null)
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                },
                actions = actions,
            )
        },
        floatingActionButton = floating,
        content = content,
    )
}

@Composable
fun Notice(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (action != null) OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun EmptyState(title: String, text: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.Checkroom,
            null,
            Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        onRetry?.let { OutlinedButton(onClick = it) { Text("重试") } }
    }
}

@Composable
fun <T> Loaded(
    identity: Any?,
    revision: Int = 0,
    loader: suspend () -> T,
    content: @Composable (T, () -> Unit) -> Unit,
) {
    var retry by remember(identity) { mutableIntStateOf(0) }
    var result by remember(identity, revision, retry) { mutableStateOf<Result<T>?>(null) }
    LaunchedEffect(identity, revision, retry) {
        try {
            result = Result.success(loader())
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            result = Result.failure(error)
        }
    }
    val loaded = result
    when {
        loaded == null ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        loaded.isFailure -> EmptyState("暂时无法加载", errorText(loaded.exceptionOrNull()!!)) { retry++ }
        else -> content(loaded.getOrThrow()) { retry++ }
    }
}

@Composable
fun RemoteImage(
    vm: AppViewModel,
    path: String?,
    description: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    scale: ContentScale = ContentScale.Crop,
) {
    val session by vm.auth.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val url = vm.imageUrl(path)
    var retry by remember(url) { mutableIntStateOf(0) }
    val request =
        remember(url, session?.accessToken, retry) {
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .memoryCacheKey(url?.substringBefore('?'))
                .diskCacheKey(url?.substringBefore('?'))
                .apply {
                    session
                        ?.takeIf { url?.startsWith(it.server) == true }
                        ?.accessToken
                        ?.let { addHeader("Authorization", "Bearer $it") }
                }
                .build()
        }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = description,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = scale,
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(26.dp))
            }
        },
        error = {
            Column(
                Modifier.fillMaxSize()
                    .clickable {
                        if (onRetry != null) onRetry()
                        else {
                            retry++
                            vm.refresh()
                        }
                    }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.BrokenImage, null)
                Text("图片未加载，点击重试", style = MaterialTheme.typography.labelSmall)
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeasonBadges(values: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        orderedSeasons(values).forEach { key ->
            val color =
                when (key) {
                    "spring" -> if (isSystemInDarkTheme()) Color(0xFF9CDEA5) else Color(0xFF28733A)
                    "summer" -> if (isSystemInDarkTheme()) Color(0xFFFFDB91) else Color(0xFF8C6000)
                    "fall" -> if (isSystemInDarkTheme()) Color(0xFFFFBE91) else Color(0xFFA34B15)
                    else -> if (isSystemInDarkTheme()) Color(0xFF9AD9FF) else Color(0xFF116A9C)
                }
            val icon =
                when (key) {
                    "spring" -> Icons.Default.LocalFlorist
                    "summer" -> Icons.Default.WbSunny
                    "fall" -> Icons.Default.Eco
                    else -> Icons.Default.AcUnit
                }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = .10f),
                contentColor = color,
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(icon, null, Modifier.size(15.dp))
                    Text(seasons.getValue(key), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (values.isEmpty())
            Text(
                "季节未设置",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

@Composable
fun ChoiceField(
    label: String,
    value: String?,
    options: List<Pair<String, String>>,
    emptyLabel: String = "未设置",
    enabled: Boolean = true,
    onChange: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = { open = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                options.firstOrNull { it.first == value }?.second ?: emptyLabel,
                Modifier.weight(1f),
                maxLines = 2,
            )
            Icon(Icons.Default.ExpandMore, null)
        }
    }
    if (open)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(
                        onClick = {
                            onChange(null)
                            open = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(emptyLabel)
                    }
                    options.forEach { (id, name) ->
                        TextButton(
                            onClick = {
                                onChange(id)
                                open = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(name, Modifier.weight(1f))
                            if (value == id) Icon(Icons.Default.Check, null)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("关闭") } },
        )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    busy: Boolean,
    confirm: String = "删除",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !busy,
                colors =
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(confirm)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } },
    )
}

@Composable
fun BusyButton(text: String, busy: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}
