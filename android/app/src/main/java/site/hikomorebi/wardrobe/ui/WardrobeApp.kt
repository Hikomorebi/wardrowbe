package site.hikomorebi.wardrobe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import site.hikomorebi.wardrobe.data.PhotoProcessor

@OptIn(coil.annotation.ExperimentalCoilApi::class)
@Composable
fun WardrobeApp(vm: AppViewModel) {
    val session by vm.auth.session.collectAsStateWithLifecycle()
    val server by vm.auth.server.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(session?.user?.id) {
        if (session == null)
            withContext(Dispatchers.IO) {
                PhotoProcessor.clean(context, all = true)
                context.imageLoader.memoryCache?.clear()
                context.imageLoader.diskCache?.clear()
            }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (session != null) vm.refresh() }
    key(session?.user?.id, server) {
        if (session == null) {
            Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
                LoginScreen(vm, Modifier.padding(padding))
            }
        } else {
            val nav = rememberNavController()
            val entry by nav.currentBackStackEntryAsState()
            val route = entry?.destination?.route
            val root = route in setOf("wardrobe", "outfits", "profile")
            fun go(path: String) {
                if (!busy) nav.navigate(path) { launchSingleTop = true }
            }
            fun back() {
                if (!busy) nav.popBackStack()
            }
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (root)
                        NavigationBar {
                            listOf(
                                    Triple("wardrobe", "衣橱", Icons.Default.Checkroom),
                                    Triple("outfits", "我的搭配", Icons.Default.Layers),
                                    Triple("profile", "我的", Icons.Default.Person),
                                )
                                .forEach { (path, label, icon) ->
                                    NavigationBarItem(
                                        selected = route == path,
                                        enabled = !busy,
                                        onClick = {
                                            nav.navigate(path) {
                                                popUpTo("wardrobe") { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(icon, label) },
                                        label = { Text(label) },
                                    )
                                }
                        }
                },
            ) { padding ->
                NavHost(
                    navController = nav,
                    startDestination = "wardrobe",
                    modifier = Modifier.padding(padding),
                ) {
                    composable("wardrobe") {
                        WardrobeScreen(
                            vm,
                            { go("item/new") },
                            { go("item/$it") },
                            { go("matches/$it") },
                        )
                    }
                    composable("item/new") {
                        ItemEditorScreen(vm, null, ::back) { id ->
                            nav.popBackStack()
                            go("item/$id")
                        }
                    }
                    composable("item/{id}") { stack ->
                        val id = stack.arguments!!.getString("id")!!
                        ItemDetailScreen(
                            vm,
                            id,
                            ::back,
                            { go("item/$id/edit") },
                            { go("matches/$id") },
                        )
                    }
                    composable("item/{id}/edit") { stack ->
                        ItemEditorScreen(vm, stack.arguments!!.getString("id"), ::back) {
                            nav.popBackStack()
                        }
                    }
                    composable("matches/{id}") { stack ->
                        OutfitsScreen(
                            vm,
                            stack.arguments!!.getString("id"),
                            ::back,
                            { go("item/$it") },
                            { go("outfits/new") },
                            { go("outfits/$it/edit") },
                        )
                    }
                    composable("outfits") {
                        OutfitsScreen(
                            vm,
                            null,
                            null,
                            { go("item/$it") },
                            { go("outfits/new") },
                            { go("outfits/$it/edit") },
                        )
                    }
                    composable("outfits/new") {
                        OutfitEditorScreen(vm, null, ::back) { nav.popBackStack() }
                    }
                    composable("outfits/{id}/edit") { stack ->
                        OutfitEditorScreen(vm, stack.arguments!!.getString("id"), ::back) {
                            nav.popBackStack()
                        }
                    }
                    composable("profile") { ProfileScreen(vm, { go(it) }) }
                    composable("categories") { EntriesScreen(vm, true, ::back) }
                    composable("locations") { EntriesScreen(vm, false, ::back) }
                    composable("password") { PasswordScreen(vm, ::back) }
                }
                // Register after NavHost so system Back cannot leave a submitted form.
                BackHandler(enabled = busy) {}
            }
        }
    }
}
