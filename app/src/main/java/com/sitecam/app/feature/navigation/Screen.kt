package com.sitecam.app.feature.navigation

sealed class Screen(val route: String) {
    data object Camera : Screen("camera")
    data object Projects : Screen("projects")
    data object Gallery : Screen("gallery?projectId={projectId}") {
        fun createRoute(projectId: Long? = null): String {
            return if (projectId != null) "gallery?projectId=$projectId" else "gallery"
        }
    }
    data object PhotoDetail : Screen("photo_detail/{mediaId}") {
        fun createRoute(mediaId: Long): String = "photo_detail/$mediaId"
    }
    data object Settings : Screen("settings")
    data object Help : Screen("help")
    data object Onboarding : Screen("onboarding?replay={replay}") {
        fun createRoute(replay: Boolean = false): String = "onboarding?replay=$replay"
    }
    data object WatermarkEditor : Screen("watermark_editor/{templateId}") {
        fun createRoute(templateId: Long): String = "watermark_editor/$templateId"
    }
    data object PhotoAnnotation : Screen("photo_annotation/{mediaId}") {
        fun createRoute(mediaId: Long): String = "photo_annotation/$mediaId"
    }
}
