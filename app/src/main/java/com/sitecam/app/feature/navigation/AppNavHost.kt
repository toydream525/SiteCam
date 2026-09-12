package com.sitecam.app.feature.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.sitecam.app.core.layout.rememberScreenEnvironment
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sitecam.app.feature.permissions.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sitecam.app.core.database.entity.IssueEntity
import com.sitecam.app.core.di.AppContainer
import com.sitecam.app.feature.annotation.PhotoAnnotationScreen
import com.sitecam.app.feature.annotation.PhotoAnnotationViewModel
import com.sitecam.app.feature.camera.CameraScreen
import com.sitecam.app.feature.camera.CameraViewModel
import com.sitecam.app.feature.gallery.GalleryScreen
import com.sitecam.app.feature.gallery.GalleryViewModel
import com.sitecam.app.feature.gallery.PhotoDetailScreen
import com.sitecam.app.feature.gallery.PhotoDetailViewModel
import com.sitecam.app.feature.issue.IssueDialog
import com.sitecam.app.feature.help.HelpScreen
import com.sitecam.app.feature.onboarding.OnboardingPreferences
import com.sitecam.app.feature.onboarding.OnboardingLoadingScreen
import com.sitecam.app.feature.onboarding.OnboardingScreen
import com.sitecam.app.feature.projects.ProjectListScreen
import com.sitecam.app.feature.projects.ProjectViewModel
import com.sitecam.app.feature.settings.SettingsScreen
import com.sitecam.app.feature.settings.SettingsViewModel
import com.sitecam.app.feature.watermark.WatermarkCustomizationScreen
import com.sitecam.app.feature.watermark.WatermarkEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppNavHost(
    navController: NavHostController,
    appContainer: AppContainer,
    modifier: Modifier = Modifier
) {
    var quickIssueMediaId by remember { mutableStateOf<Long?>(null) }
    val issueScope = rememberCoroutineScope()
    val onboardingScope = rememberCoroutineScope()
    val context = LocalContext.current
    val onboardingPreferences = remember(context) { OnboardingPreferences(context) }
    val onboardingState by onboardingPreferences.state.collectAsState(initial = null)
    var startupPermissions by remember { mutableStateOf<CapturePermissions?>(null) }
    var keepPermissionGuideOpen by remember { mutableStateOf(false) }
    var permissionGuideFinished by remember { mutableStateOf(false) }
    val latestRequested by rememberUpdatedState(onboardingState?.requestedPermissions.orEmpty())
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(onboardingState?.requestedPermissions) {
        startupPermissions = PermissionAccess.read(context, latestRequested)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) startupPermissions = PermissionAccess.read(context, latestRequested)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(startupPermissions?.allGranted, onboardingState?.hasHandledPermissionGuide) {
        if (startupPermissions?.allGranted == true && onboardingState?.hasHandledPermissionGuide == false) {
            onboardingPreferences.markPermissionGuideHandled()
        }
    }
    val effectiveOnboardingState = onboardingState?.let { if (permissionGuideFinished) it.copy(hasHandledPermissionGuide = true) else it }
    val startupGate = resolveStartupGate(effectiveOnboardingState, startupPermissions, keepPermissionGuideOpen)

    when (startupGate) {
        StartupGate.LOADING -> OnboardingLoadingScreen(modifier = modifier)
        StartupGate.FEATURE_GUIDE -> OnboardingScreen(
            isReplay = false,
            onFinish = {
                onboardingScope.launch {
                    onboardingPreferences.markGuideDismissed()
                }
            },
            modifier = modifier
        )
        StartupGate.PERMISSION_GUIDE -> PermissionGuideScreen(
            preferences = onboardingPreferences,
            requestedPermissions = onboardingState?.requestedPermissions.orEmpty(),
            onInteractionStarted = { keepPermissionGuideOpen = true },
            onContinue = {
                onboardingScope.launch {
                    onboardingPreferences.markPermissionGuideHandled()
                    permissionGuideFinished = true
                    keepPermissionGuideOpen = false
                }
            },
            modifier = modifier
        )
        StartupGate.APP -> NavHost(
            navController = navController,
            startDestination = Screen.Camera.route,
            modifier = modifier
        ) {
        composable(Screen.Camera.route) {
            val cameraViewModel: CameraViewModel = viewModel(
                factory = CameraViewModel.provideFactory(appContainer)
            )
            CameraScreen(
                viewModel = cameraViewModel,
                onNavigateToProjects = {
                    navController.navigate(Screen.Projects.route)
                },
                onNavigateToGallery = { projectId ->
                    navController.navigate(Screen.Gallery.createRoute(projectId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onQuickIssuePrompt = { mediaId ->
                    quickIssueMediaId = mediaId
                }
            )
        }

        composable(Screen.Projects.route) {
            val projectViewModel: ProjectViewModel = viewModel(
                factory = ProjectViewModel.provideFactory(appContainer)
            )
            ProjectListScreen(
                viewModel = projectViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToGallery = { projectId ->
                    navController.navigate(Screen.Gallery.createRoute(projectId))
                }
            )
        }

        composable(
            route = Screen.Gallery.route,
            arguments = listOf(
                navArgument("projectId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val projectIdStr = backStackEntry.arguments?.getString("projectId")
            val projectId = projectIdStr?.toLongOrNull()
            val galleryViewModel: GalleryViewModel = viewModel(
                factory = GalleryViewModel.provideFactory(appContainer, projectId)
            )
            var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
            val environment = rememberScreenEnvironment()
            BackHandler(selectedId != null) { selectedId = null }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val split = environment.profile.large && maxWidth >= 600.dp
                val listWidth = if (split) (maxWidth * .4f).coerceIn(280.dp, 420.dp)
                    else if (selectedId == null) maxWidth else 0.dp
                Row(Modifier.fillMaxSize()) {
                    // Stable composition slots preserve list scroll and the original detail state.
                    Box(Modifier.width(listWidth).fillMaxHeight()) {
                        GalleryScreen(
                            viewModel = galleryViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToDetail = { selectedId = it },
                            selectedMediaId = selectedId
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        selectedId?.let { id ->
                            key(id) {
                                val detail: PhotoDetailViewModel = viewModel(
                                    key = "gallery-detail-$id",
                                    factory = PhotoDetailViewModel.provideFactory(appContainer, id)
                                )
                                PhotoDetailScreen(detail, onNavigateBack = { selectedId = null }, onMissingMedia = { selectedId = null },
                                    onNavigateToAnnotation = { navController.navigate(Screen.PhotoAnnotation.createRoute(it)) })
                            }
                        } ?: if (split) Text("选择照片或视频预览") else Unit
                    }
                }
            }
        }

        composable(
            route = Screen.PhotoDetail.route,
            arguments = listOf(
                navArgument("mediaId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getLong("mediaId") ?: 0L
            val detailViewModel: PhotoDetailViewModel = viewModel(
                factory = PhotoDetailViewModel.provideFactory(appContainer, mediaId)
            )
            PhotoDetailScreen(
                viewModel = detailViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAnnotation = { id ->
                    navController.navigate(Screen.PhotoAnnotation.createRoute(id))
                }
            )
        }

        composable(
            route = Screen.PhotoAnnotation.route,
            arguments = listOf(
                navArgument("mediaId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getLong("mediaId") ?: 0L
            val annotationViewModel: PhotoAnnotationViewModel = viewModel(
                factory = PhotoAnnotationViewModel.provideFactory(appContainer, mediaId)
            )
            PhotoAnnotationScreen(
                viewModel = annotationViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.WatermarkEditor.route,
            arguments = listOf(
                navArgument("templateId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val templateId = backStackEntry.arguments?.getLong("templateId") ?: 1L
            val watermarkViewModel: WatermarkEditorViewModel = viewModel(
                factory = WatermarkEditorViewModel.provideFactory(appContainer, templateId)
            )
            WatermarkCustomizationScreen(
                viewModel = watermarkViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.provideFactory(appContainer)
            )
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToWatermarkEditor = { templateId ->
                    navController.navigate(Screen.WatermarkEditor.createRoute(templateId))
                },
                onNavigateToHelp = {
                    navController.navigate(Screen.Help.route)
                }
            )
        }

        composable(Screen.Help.route) {
            HelpScreen(
                onNavigateBack = { navController.popBackStack() },
                onReplayOnboarding = {
                    navController.navigate(Screen.Onboarding.createRoute(replay = true))
                }
            )
        }

        composable(
            route = Screen.Onboarding.route,
            arguments = listOf(
                navArgument("replay") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val replay = backStackEntry.arguments?.getBoolean("replay") ?: false
            OnboardingScreen(
                isReplay = replay,
                onNavigateBack = { navController.popBackStack() },
                onFinish = {
                    onboardingScope.launch {
                        onboardingPreferences.markGuideDismissed()
                        navController.popBackStack()
                    }
                }
            )
        }
    }
    }

    // Quick Issue Dialog if triggered post-capture
    if (startupGate == StartupGate.APP) quickIssueMediaId?.let { mediaId ->
        IssueDialog(
            mediaId = mediaId,
            onDismiss = { quickIssueMediaId = null },
            onConfirm = { title, severity, description, status ->
                issueScope.launch(Dispatchers.IO) {
                    try {
                        val mediaItem = appContainer.database.mediaItemDao().getMediaItemById(mediaId)
                        if (mediaItem == null) {
                            throw IllegalStateException("媒体记录不存在")
                        }
                        appContainer.database.issueDao().insertIssueAndMarkMedia(
                            IssueEntity(
                                projectId = mediaItem.projectId,
                                mediaId = mediaId,
                                title = title,
                                severity = severity,
                                description = description,
                                status = status
                            )
                        )
                        withContext(Dispatchers.Main.immediate) {
                            android.widget.Toast.makeText(context, "问题记录已保存", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main.immediate) {
                            android.widget.Toast.makeText(context, "问题记录保存失败: ${e.message ?: "未知错误"}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
                quickIssueMediaId = null
            }
        )
    }
}
