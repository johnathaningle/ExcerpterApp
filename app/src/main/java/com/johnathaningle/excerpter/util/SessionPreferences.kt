package com.johnathaningle.excerpter.util

import android.content.Context
import android.content.SharedPreferences

class SessionPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("excerpter_session", Context.MODE_PRIVATE)

    var lastOpenedPdfUri: String?
        get() = prefs.getString(KEY_LAST_OPENED_PDF, null)
        set(value) = prefs.edit().putString(KEY_LAST_OPENED_PDF, value).apply()

    var lastSelectedColor: Long
        get() = prefs.getLong(KEY_LAST_COLOR, 0xFFFF0000)
        set(value) = prefs.edit().putLong(KEY_LAST_COLOR, value).apply()

    var autoRotateColor: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ROTATE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_ROTATE, value).apply()

    var modelPath: String?
        get() = prefs.getString(KEY_MODEL_PATH, null)
        set(value) = prefs.edit().putString(KEY_MODEL_PATH, value).apply()

    var modelDisplayName: String?
        get() = prefs.getString(KEY_MODEL_NAME, null)
        set(value) = prefs.edit().putString(KEY_MODEL_NAME, value).apply()

    var useVulkan: Boolean
        get() = prefs.getBoolean(KEY_USE_VULKAN, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_VULKAN, value).apply()

    companion object {
        private const val KEY_LAST_OPENED_PDF = "last_opened_pdf"
        private const val KEY_LAST_COLOR = "last_selected_color"
        private const val KEY_AUTO_ROTATE = "auto_rotate_color"
        private const val KEY_MODEL_PATH = "model_path"
        private const val KEY_MODEL_NAME = "model_display_name"
        private const val KEY_USE_VULKAN = "use_vulkan"
    }
}
