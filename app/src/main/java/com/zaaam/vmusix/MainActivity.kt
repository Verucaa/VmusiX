package com.zaaam.vmusix

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zaaam.vmusix.core.CrashLog
import com.zaaam.vmusix.ui.browse.BrowseScreen
import com.zaaam.vmusix.ui.home.HomeScreen
import com.zaaam.vmusix.ui.library.LibraryScreen
import com.zaaam.vmusix.ui.library.LibraryViewModel
import com.zaaam.vmusix.ui.playlist.PlaylistViewModel
import com.zaaam.vmusix.ui.nav.Tab
import com.zaaam.vmusix.ui.player.MiniPlayer
import com.zaaam.vmusix.ui.player.NowPlayingScreen
import com.zaaam.vmusix.ui.player.PlaybackViewModel
import com.zaaam.vmusix.ui.search.SearchScreen
import com.zaaam.vmusix.ui.theme.Accent
import com.zaaam.vmusix.ui.theme.VmusiXTheme
import com.zaaam.vmusix.ui.theme.TextSecondary
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val player: PlaybackViewModel by viewModels()
    private val libraryVm: LibraryViewModel by viewModels()
    private val playlistVm: PlaylistViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lastCrash = CrashLog.read(this)
        if (!lastCrash.isNullOrBlank()) {
            CrashLog.clear(this)
            lastCrash
                .lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.let { summary ->
                    Log.w("VmusiXCrash", "crash terakhir: $summary")
                }
        }
        setContent {
            VmusiXTheme {
                VmusiXScaffold(player, libraryVm, playlistVm)
            }
        }
    }
}

@Composable
fun VmusiXScaffold(
    player: PlaybackViewModel,
    libraryVm: LibraryViewModel,
    playlistVm: PlaylistViewModel,
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val pState by player.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    val hazeState = remember { HazeState() }

    // Error player (URL basi, service putus, gagal muat) harus terlihat dari
    // MANA pun — bukan cuma dari layar Now Playing. Sebelumnya LaunchedEffect
    // penampilannya hidup di dalam NowPlayingScreen, jadi kalau mini-player
    // yang aktif, user tidak pernah diberi tahu kenapa lagunya diam.
    LaunchedEffect(pState.error) {
        val e = pState.error
        if (e != null) {
            snack.showSnackbar(e)
            player.clearError()
        }
    }

    com.zaaam.vmusix.ui.theme.AmbientBackground(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = com.zaaam.vmusix.ui.theme.TextPrimary,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 10.dp)) {
                AnimatedVisibility(
                    visible = !pState.isExpanded && pState.current != null,
                    enter = fadeIn(tween(220)) + slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                    ),
                    exit = fadeOut(tween(180)) + slideOutVertically(
                        targetOffsetY = { it / 2 },
                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                    ),
                ) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF2C2C2E).copy(alpha = 0.50f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, com.zaaam.vmusix.ui.theme.GlassRim),
                        modifier = Modifier.padding(bottom = 8.dp).hazeChild(
                            state = hazeState,
                            style = HazeDefaults.style(
                                backgroundColor = Color(0xFF2C2C2E).copy(alpha = 0.55f),
                                blurRadius = 34.dp,
                            ),
                        ),
                    ) {
                        MiniPlayer(
                            state = pState,
                            onTap = { player.setExpanded(true) },
                            onToggle = { player.togglePlayPause() },
                            onNext = { player.next() },
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color(0xFF1C1C1E).copy(alpha = 0.50f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, com.zaaam.vmusix.ui.theme.GlassRim),
                    modifier = Modifier.hazeChild(
                        state = hazeState,
                        style = HazeDefaults.style(
                            backgroundColor = Color(0xFF1C1C1E).copy(alpha = 0.55f),
                            blurRadius = 34.dp,
                        ),
                    ),
                ) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TabItem(Tab.Home, Icons.Filled.Home, route, nav, Modifier.weight(1f))
                        TabItem(Tab.New, Icons.Filled.AutoAwesome, route, nav, Modifier.weight(1f))
                        TabItem(Tab.Library, Icons.Filled.LibraryMusic, route, nav, Modifier.weight(1f))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            val onSearch = route?.startsWith("search") == true
                            IconButton(
                                onClick = {
                                    nav.navigate("search") {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                modifier = Modifier.size(44.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(
                                        if (onSearch) Accent else Color.White.copy(alpha = 0.14f),
                                    ),
                            ) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = "Search",
                                    tint = if (onSearch) Color.White else TextSecondary,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).haze(state = hazeState)) {
            NavHost(nav, startDestination = Tab.Library.route) {
                composable(Tab.Home.route) {
                    HomeScreen(
                        player = player,
                        libVm = libraryVm,
                        plVm = playlistVm,
                        onGenre = {
                            nav.navigate("search?preset=" + android.net.Uri.encode(it)) {
                                launchSingleTop = true
                            }
                        },
                        onOpenBrowse = { nav.navigate(Tab.New.route) { launchSingleTop = true } },
                        onOpenSearch = { nav.navigate("search") { launchSingleTop = true } },
                        onOpenFavorit = {
                            nav.navigate("library?openFavorit=true") { launchSingleTop = true }
                        },
                    )
                }
                composable(Tab.New.route) {
                    BrowseScreen(onGenre = {
                        nav.navigate("search?preset=" + android.net.Uri.encode(it)) {
                            launchSingleTop = true
                        }
                    })
                }
                composable(
                    "library?openFavorit={openFavorit}",
                    arguments = listOf(navArgument("openFavorit") { type = NavType.BoolType; defaultValue = false }),
                ) { entry ->
                    LibraryScreen(
                        player = player,
                        vm = libraryVm,
                        plVm = playlistVm,
                        openFavorit = entry.arguments?.getBoolean("openFavorit") ?: false,
                    )
                }
                composable(
                    "search?preset={preset}",
                    arguments = listOf(navArgument("preset") { type = NavType.StringType; defaultValue = "" }),
                ) { entry ->
                    SearchScreen(preset = entry.arguments?.getString("preset") ?: "", player = player, plVm = playlistVm)
                }
            }
            androidx.activity.compose.BackHandler(enabled = pState.isExpanded) {
                player.setExpanded(false)
            }
            AnimatedVisibility(
                visible = pState.isExpanded,
                enter = slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                ) + fadeIn(tween(300)) + scaleIn(
                    initialScale = 0.96f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                ),
                exit = slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                ) + fadeOut(tween(300)) + scaleOut(
                    targetScale = 0.96f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                ),
            ) {
                NowPlayingScreen(
                    state = pState,
                    player = player,
                    snack = snack,
                    plVm = playlistVm,
                    onAddSongs = {
                        player.setExpanded(false)
                        nav.navigate("search") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    }
    }
}

@Composable
private fun TabItem(
    tab: Tab,
    icon: ImageVector,
    route: String?,
    nav: androidx.navigation.NavController,
    modifier: Modifier = Modifier,
) {
    val selected = route?.substringBefore("?") == tab.route
    val tint by androidx.compose.animation.animateColorAsState(
        if (selected) Accent else TextSecondary,
        androidx.compose.animation.core.tween(240),
        label = "tabTint",
    )
    IconButton(
        onClick = {
            nav.navigate(tab.route) {
                launchSingleTop = true
                restoreState = true
                popUpTo(nav.graph.startDestinationId) { saveState = true }
            }
        },
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = tab.label,
                tint = tint,
            )
            Text(
                tab.label,
                fontSize = 10.sp,
                color = tint,
            )
        }
    }
}
