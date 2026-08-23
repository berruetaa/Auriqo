package com.auriqo.music.fonts

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.datastore.preferences.core.Preferences
import com.auriqo.music.constants.AppFontKey
import com.auriqo.music.constants.LyricsFontKey
import com.auriqo.music.constants.PlayerFontKey
import com.auriqo.music.utils.dataStore
import com.auriqo.music.utils.read

/**
 * The parts of the app that can carry their own font.
 *
 * [APP] is the base: everything follows it unless a more specific target opts out. The others
 * default to [AppFont.INHERIT_ID], which resolves to whatever [APP] is using.
 */
enum class FontTarget(
    val preferenceKey: Preferences.Key<String>,
    val defaultId: String,
) {
    APP(AppFontKey, AppFont.SYSTEM_ID),
    LYRICS(LyricsFontKey, AppFont.INHERIT_ID),
    PLAYER(PlayerFontKey, AppFont.INHERIT_ID),
    ;

    val inheritable: Boolean get() = this != APP
}

/**
 * Fonts for the targets that Material's typography cannot express on its own.
 *
 * Both always hold a *resolved* family — inheritance is applied before the value is provided — so
 * consumers can apply them unconditionally. `null` means the system font, never "unset".
 */
val LocalLyricsFontFamily = staticCompositionLocalOf<FontFamily?> { null }

val LocalPlayerFontFamily = staticCompositionLocalOf<FontFamily?> { null }

/**
 * Family for a secondary target, falling back to [appFamily] when it is set to inherit.
 *
 * [rememberFontFamily] is called unconditionally so the composition keeps a stable shape across
 * recompositions, whichever branch the value takes.
 */
/**
 * The id [this] target actually renders with, inheritance already applied.
 *
 * This is a suspend read because DataStore access must never block the caller.
 */
 suspend fun FontTarget.resolvedFontId(context: Context): String {
    val stored = context.dataStore.read(preferenceKey, defaultId)
    return if (stored == AppFont.INHERIT_ID) {
        context.dataStore.read(FontTarget.APP.preferenceKey, FontTarget.APP.defaultId)
    } else {
        stored
    }
}

@Composable
fun rememberInheritingFontFamily(fontId: String, appFamily: FontFamily?): FontFamily? {
    val inherits = fontId == AppFont.INHERIT_ID
    val ownFamily = rememberFontFamily(if (inherits) AppFont.SYSTEM_ID else fontId)
    return if (inherits) appFamily else ownFamily
}
