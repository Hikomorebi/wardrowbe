package site.hikomorebi.wardrobe.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File
import site.hikomorebi.wardrobe.data.*

@Composable
fun ItemEditorScreen(vm: AppViewModel, id: String?, back: () -> Unit, saved: (String) -> Unit) {
    Page(if (id == null) "新增衣物" else "编辑衣物", back) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (id == null) ItemForm(vm, null, saved)
            else Loaded(id, loader = { vm.item(id) }) { item, _ -> ItemForm(vm, item, saved) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemForm(vm: AppViewModel, item: ClothingItem?, saved: (String) -> Unit) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    var name by rememberSaveable(item?.id) { mutableStateOf(item?.name.orEmpty()) }
    var category by rememberSaveable(item?.id) { mutableStateOf(item?.categoryId) }
    var condition by rememberSaveable(item?.id) { mutableStateOf(item?.condition) }
    var location by rememberSaveable(item?.id) { mutableStateOf(item?.storageLocationId) }
    var selectedSeasons by
        rememberSaveable(item?.id) { mutableStateOf(orderedSeasons(item?.season.orEmpty())) }
    var notes by rememberSaveable(item?.id) { mutableStateOf(item?.notes.orEmpty()) }
    var photoPath by rememberSaveable(item?.id) { mutableStateOf<String?>(null) }
    var capturePath by rememberSaveable(item?.id) { mutableStateOf<String?>(null) }

    fun prepare(uri: android.net.Uri, originalCapture: String? = null) {
        vm.submit(
            onSuccess = { file ->
                photoPath?.let { File(it).delete() }
                photoPath = file.absolutePath
            },
        ) {
            try {
                PhotoProcessor.prepare(context, uri)
            } finally {
                originalCapture?.let { File(it).delete() }
            }
        }
    }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) prepare(uri)
        }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val path = capturePath
            if (path != null) {
                if (success)
                    prepare(
                        FileProvider.getUriForFile(
                            context,
                            context.packageName + ".files",
                            File(path),
                        ),
                        path,
                    )
                else File(path).delete()
            }
            capturePath = null
        }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val selectedPhoto = photoPath?.let(::File)?.takeIf { it.exists() }
        if (selectedPhoto != null)
            AsyncImage(
                selectedPhoto,
                "待上传图片",
                Modifier.fillMaxWidth().aspectRatio(1.3f),
                contentScale = ContentScale.Fit,
            )
        else if (item != null)
            RemoteImage(
                vm,
                item.imageUrl,
                item.title,
                Modifier.fillMaxWidth().aspectRatio(1.3f),
                scale = ContentScale.Fit,
            )
        else Notice("先拍照或选择衣物图片，再填写衣物信息。")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    val file = PhotoProcessor.captureFile(context)
                    capturePath = file.absolutePath
                    try {
                        camera.launch(
                            FileProvider.getUriForFile(
                                context,
                                context.packageName + ".files",
                                file,
                            ),
                        )
                    } catch (_: Exception) {
                        file.delete()
                        capturePath = null
                        vm.message("无法打开相机，请使用相册选图")
                    }
                },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.PhotoCamera, null)
                Spacer(Modifier.width(6.dp))
                Text("拍照")
            }
            OutlinedButton(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.PhotoLibrary, null)
                Spacer(Modifier.width(6.dp))
                Text("相册选图")
            }
        }
        OutlinedTextField(
            name,
            { name = it.take(100) },
            label = { Text("衣物名称") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !busy,
        )
        ChoiceField("类别", category, categories.map { it.id to it.name }, enabled = !busy) {
            category = it
        }
        ChoiceField("成色", condition, conditions.toList(), enabled = !busy) { condition = it }
        ChoiceField("存放位置", location, locations.map { it.id to it.name }, enabled = !busy) {
            location = it
        }
        Text("适合季节（可多选）", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            seasons.forEach { (key, label) ->
                FilterChip(
                    selected = key in selectedSeasons,
                    onClick = {
                        selectedSeasons =
                            if (key in selectedSeasons) selectedSeasons - key
                            else selectedSeasons + key
                    },
                    enabled = !busy,
                    label = { Text(label) },
                    leadingIcon = {
                        if (key in selectedSeasons)
                            Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                    },
                )
            }
        }
        OutlinedTextField(
            notes,
            { notes = it },
            label = { Text("备注") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            enabled = !busy,
        )
        BusyButton("保存衣物", busy, item != null || selectedPhoto != null) {
            val data =
                ItemWrite(
                    name.trim().ifBlank { null },
                    category,
                    condition,
                    location,
                    orderedSeasons(selectedSeasons),
                    notes.trim().ifBlank { null },
                )
            vm.submit(
                onSuccess = { result ->
                    selectedPhoto?.delete()
                    photoPath = null
                    saved(result.id)
                    vm.message("衣物已保存")
                },
            ) {
                vm.saveItem(item?.id, data, selectedPhoto)
            }
        }
    }
}
