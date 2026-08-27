package com.sitecam.app.feature.navigation

import androidx.compose.runtime.Composable
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
import com.sitecam.app.feature.issue.QuickIssueDialog
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
    val context = LocalContext.current

    NavHost(
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
            GalleryScreen(
                viewModel = galleryViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { mediaId ->
                    navController.navigate(Screen.PhotoDetail.createRoute(mediaId))
                }
            )
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
                }
            )
        }
    }

    // Quick Issue Dialog if triggered post-capture
    quickIssueMediaId?.let { mediaId ->
        QuickIssueDialog(
            mediaId = mediaId,
            onDismiss = { quickIssueMediaId = null },
            onConfirm = { title, severity, description ->
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
                                description = description
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
