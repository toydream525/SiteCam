package com.sitecam.app.core.media

enum class MediaSaveTarget { APP_PRIVATE, SYSTEM_GALLERY }

/** A denied public request must fail, never silently switch ownership. */
fun resolveMediaSaveTarget(systemGallery: Boolean, sdk: Int, hasLegacyWritePermission: Boolean): MediaSaveTarget {
    if (!systemGallery) return MediaSaveTarget.APP_PRIVATE
    check(sdk >= 29 || hasLegacyWritePermission) { "请先允许存储权限，再保存到系统相册" }
    return MediaSaveTarget.SYSTEM_GALLERY
}
