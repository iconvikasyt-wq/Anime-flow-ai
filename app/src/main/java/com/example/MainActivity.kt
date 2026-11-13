package com.example

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.firebase.configureAppCheck
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOut
import com.example.ui.screens.CharactersScreen
import com.example.ui.screens.ModelsAndCostsScreen
import com.example.ui.screens.StoryboardScreen
import com.example.ui.screens.StudioWorkspaceScreen
import com.example.ui.theme.AnimeFlowTheme
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.SakuraMagenta
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.GenerationTaskState
import com.example.ui.viewmodel.StudioViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val ROUTE_STUDIO = "studio"
const val ROUTE_STORYBOARD = "storyboard"
const val ROUTE_CHARACTERS = "characters"
const val ROUTE_MODELS = "models"

private data class StudioNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val StudioTabs = listOf(
    StudioNavTab(ROUTE_STUDIO, "Studio", Icons.Filled.MovieCreation, Icons.Outlined.MovieCreation, "nav_studio"),
    StudioNavTab(ROUTE_STORYBOARD, "Storyboard", Icons.Filled.ViewCarousel, Icons.Outlined.ViewCarousel, "nav_storyboard"),
    StudioNavTab(ROUTE_CHARACTERS, "Cast", Icons.Filled.Face, Icons.Outlined.Face, "nav_characters"),
    StudioNavTab(ROUTE_MODELS, "Models", Icons.Filled.Tune, Icons.Outlined.Tune, "nav_models")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        // Must run before setContent, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        setContent {
            AnimeFlowTheme {
                AnimeFlowAppRoot()
            }
        }
    }
}

private fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val auth = Firebase.auth
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    auth.addAuthStateListener(listener)
    trySend(auth.currentUser)
    awaitClose { auth.removeAuthStateListener(listener) }
}

@Composable
private fun AnimeFlowAppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val currentUser by remember { authStateFlow() }.collectAsStateWithLifecycle(
        initialValue = Firebase.auth.currentUser
    )
    var autoSignInAttempted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!autoSignInAttempted && Firebase.auth.currentUser == null) {
            autoSignInAttempted = true
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            MainStudioShell(
                currentUserId = user.uid,
                onSignOut = {
                    signOut(
                        credentialManager = credentialManager,
                        scope = scope
                    )
                }
            )
        }
    }
}

@Composable
private fun MainStudioShell(
    currentUserId: String,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: StudioViewModel = viewModel(
        key = currentUserId,
        factory = StudioViewModel.provideFactory(context, currentUserId)
    )
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_STUDIO

    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val charactersState by viewModel.charactersState.collectAsStateWithLifecycle()
    val selectedProjectId by viewModel.selectedProjectId.collectAsStateWithLifecycle()
    val scenes by viewModel.scenesState.collectAsStateWithLifecycle()
    val selectedSceneId by viewModel.selectedSceneId.collectAsStateWithLifecycle()
    val mergedMoviePath by viewModel.mergedMoviePath.collectAsStateWithLifecycle()
    val taskState by viewModel.taskState.collectAsStateWithLifecycle()
    val lipSyncMap by viewModel.lipSyncMap.collectAsStateWithLifecycle()
    val hfToken by viewModel.hfToken.collectAsStateWithLifecycle()
    val exportResolution by viewModel.exportResolution.collectAsStateWithLifecycle()
    val defaultStoryModel by viewModel.defaultStoryModel.collectAsStateWithLifecycle()
    val defaultImageModel by viewModel.defaultImageModel.collectAsStateWithLifecycle()
    val defaultVideoModel by viewModel.defaultVideoModel.collectAsStateWithLifecycle()

    val projects = (projectsState as? UiState.Success)?.data.orEmpty()
    val activeProject = projects.firstOrNull { it.id == selectedProjectId } ?: projects.firstOrNull()
    val characters = (charactersState as? UiState.Success)?.data.orEmpty()

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianCanvas)
    ) {
        val isCompact = maxWidth < 720.dp

        val mainNavHost: @Composable (Modifier) -> Unit = { contentModifier ->
            Column(modifier = contentModifier) {
                GenerationStatusBanner(
                    taskState = taskState,
                    onDismiss = { viewModel.clearTaskStatus() },
                    onOpenExportedUri = { uri ->
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "video/mp4")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(intent) }
                    }
                )

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_STUDIO,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_STUDIO) {
                        StudioWorkspaceScreen(
                            projectsState = projectsState,
                            characters = characters,
                            selectedProjectId = selectedProjectId,
                            scenes = scenes,
                            selectedSceneId = selectedSceneId,
                            mergedMoviePath = mergedMoviePath,
                            exportResolutionLabel = exportResolution,
                            lipSyncMap = lipSyncMap,
                            isCompact = isCompact,
                            onSelectProject = viewModel::selectProject,
                            onDeleteProject = viewModel::deleteProject,
                            onSelectScene = viewModel::selectScene,
                            onCreateStoryMovie = viewModel::createStoryToMovieProject,
                            onCreateImageToVideo = viewModel::createImageToVideoScene,
                            onUpdateScene = viewModel::updateSceneEdits,
                            onRenderScene = viewModel::renderScene,
                            onExtendScene = viewModel::extendScene,
                            onRenderAllAndMerge = viewModel::renderAllAndMergeMovie,
                            onExportMp4 = viewModel::exportMovieToGallery
                        )
                    }
                    composable(ROUTE_STORYBOARD) {
                        StoryboardScreen(
                            project = activeProject,
                            scenes = scenes,
                            selectedSceneId = selectedSceneId,
                            isCompact = isCompact,
                            onSelectSceneInStudio = { sceneId ->
                                viewModel.selectScene(sceneId)
                                navigateToTab(ROUTE_STUDIO)
                            },
                            onRenderScene = viewModel::renderScene,
                            onExtendScene = viewModel::extendScene,
                            onDeleteScene = viewModel::deleteScene,
                            onRenderAllAndMerge = { proj ->
                                viewModel.renderAllAndMergeMovie(proj)
                                navigateToTab(ROUTE_STUDIO)
                            }
                        )
                    }
                    composable(ROUTE_CHARACTERS) {
                        CharactersScreen(
                            charactersState = charactersState,
                            isCompact = isCompact,
                            onCreateCharacter = viewModel::createConsistentCharacter,
                            onDeleteCharacter = viewModel::deleteCharacter
                        )
                    }
                    composable(ROUTE_MODELS) {
                        ModelsAndCostsScreen(
                            selectedStoryModelId = defaultStoryModel,
                            selectedImageModelId = defaultImageModel,
                            selectedVideoModelId = defaultVideoModel,
                            exportResolutionId = exportResolution,
                            exportCapabilities = viewModel.exportCapabilities,
                            savedHfToken = hfToken,
                            onSelectModels = viewModel::setDefaultModels,
                            onSelectExportResolution = viewModel::setExportResolution,
                            onSaveHfToken = viewModel::saveHfToken,
                            onSignOut = onSignOut
                        )
                    }
                }
            }
        }

        if (isCompact) {
            Scaffold(
                containerColor = ObsidianCanvas,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    NavigationBar(
                        containerColor = ObsidianSurface,
                        contentColor = TextPrimary,
                        tonalElevation = 0.dp
                    ) {
                        StudioTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CyanElectric,
                                    selectedTextColor = CyanElectric,
                                    indicatorColor = CyanElectric.copy(alpha = 0.16f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                mainNavHost(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .statusBarsPadding()
                )
            }
        } else {
            // Tablet / Foldable / PC Widescreen Studio Shell with Side Navigation Rail
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                NavigationRail(
                    containerColor = ObsidianSurface,
                    contentColor = TextPrimary,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "AF",
                        style = MaterialTheme.typography.titleLarge,
                        color = CyanElectric,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    StudioTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateToTab(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = CyanElectric,
                                selectedTextColor = CyanElectric,
                                indicatorColor = CyanElectric.copy(alpha = 0.16f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .testTag(tab.testTag)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(ObsidianBorder)
                )
                mainNavHost(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun GenerationStatusBanner(
    taskState: GenerationTaskState,
    onDismiss: () -> Unit,
    onOpenExportedUri: (android.net.Uri) -> Unit
) {
    AnimatedVisibility(visible = taskState !is GenerationTaskState.Idle) {
        when (taskState) {
            is GenerationTaskState.Idle -> Unit
            is GenerationTaskState.Running -> {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(1.dp, CyanElectric.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("generation_progress_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = taskState.stage,
                                style = MaterialTheme.typography.labelLarge,
                                color = CyanElectric
                            )
                            Text(
                                text = "${(taskState.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = SakuraMagenta
                            )
                        }
                        Text(
                            text = taskState.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        LinearProgressIndicator(
                            progress = { taskState.progress.coerceIn(0f, 1f) },
                            color = CyanElectric,
                            trackColor = ObsidianBorder,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            is GenerationTaskState.Completed -> {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = EmeraldReady.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, EmeraldReady.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("generation_completed_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldReady,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = taskState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        if (taskState.exportedUri != null) {
                            TextButton(onClick = { onOpenExportedUri(taskState.exportedUri) }) {
                                Icon(
                                    imageVector = Icons.Filled.OpenInNew,
                                    contentDescription = "Open exported video",
                                    tint = CyanElectric,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open", color = CyanElectric)
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Dismiss status",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }
            is GenerationTaskState.Failed -> {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CrimsonError.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, CrimsonError.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("generation_error_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFFCA5A5),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = taskState.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFCA5A5),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Dismiss error",
                                tint = Color(0xFFFCA5A5)
                            )
                        }
                    }
                }
            }
        }
    }
}
