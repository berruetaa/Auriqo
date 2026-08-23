package com.auriqo.music.lyrics

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.music.paxsenix.Paxsenix
import com.auriqo.music.constants.EnableBetterLyricsKey
import com.auriqo.music.constants.EnableKugouKey
import com.auriqo.music.constants.EnableLetrasComKey
import com.auriqo.music.constants.EnableLrcLibKey
import com.auriqo.music.constants.EnablePaxsenixKey
import com.auriqo.music.constants.EnableSimpMusicKey
import com.auriqo.music.constants.EnableYouLyPlusKey
import com.auriqo.music.constants.LyricsProviderOrderKey
import com.auriqo.music.constants.PreferredLyricsProvider
import com.auriqo.music.constants.PreferredLyricsProviderKey
import com.auriqo.music.constants.UnisonLyricsEnabledKey
import com.auriqo.music.extensions.toEnum

/**
 * Stable application-owned registry for lyrics providers.
 *
 * Persisted provider order uses [canonicalName], never implementation class names or provider
 * module details. Enablement is resolved from one Preferences snapshot so provider selection never
 * needs to block the caller on DataStore I/O.
 */
object LyricsProviderRegistry {
    private data class Registration(
        val canonicalName: String,
        val displayName: String,
        val provider: LyricsProvider,
        val enabledKey: Preferences.Key<Boolean>? = null,
        val defaultEnabled: Boolean = true,
        val prepare: (Context) -> Unit = {},
    )

    private val registrations = listOf(
        Registration("YouLyPlus", "YouLyPlus", YouLyPlusLyricsProvider, EnableYouLyPlusKey),
        Registration(
            "Paxsenix",
            "PaxSenix",
            PaxSenixLyricsProvider,
            EnablePaxsenixKey,
            prepare = { context -> Paxsenix.init(context) },
        ),
        Registration("BetterLyrics", "Better Lyrics", BetterLyricsProvider, EnableBetterLyricsKey),
        Registration("Unison", "Unison", UnisonLyricsProvider, UnisonLyricsEnabledKey),
        Registration("SimpMusic", "SimpMusic", SimpMusicLyricsProvider, EnableSimpMusicKey),
        Registration("LrcLib", "LrcLib", LrcLibLyricsProvider, EnableLrcLibKey),
        Registration("Kugou", "KuGou", KuGouLyricsProvider, EnableKugouKey),
        Registration("YouTubeSubtitle", "YouTube Subtitle", YouTubeSubtitleLyricsProvider),
        Registration("YouTubeMusic", "YouTube Music", YouTubeLyricsProvider),
        Registration("LetrasCom", "Letras.com", LetrasComLyricsProvider, EnableLetrasComKey),
    )

    private val registrationMap = registrations.associateBy { it.canonicalName }

    val providerNames: List<String> = registrations.map { it.canonicalName }

    fun getProviderByName(name: String): LyricsProvider? = registrationMap[name]?.provider

    fun deserializeProviderOrder(orderString: String): List<String> {
        if (orderString.isBlank()) return getDefaultProviderOrder()
        return orderString
            .split(",")
            .map { it.trim() }
            .filter { it in registrationMap }
            .distinct()
    }

    fun serializeProviderOrder(providers: List<String>): String =
        providers.filter { it in registrationMap }.distinct().joinToString(",")

    /** Resolves persisted order, including the one-time legacy preferred-provider fallback. */
    fun resolveProviderOrder(preferences: Preferences): List<String> {
        val persistedOrder = preferences[LyricsProviderOrderKey].orEmpty()
        if (persistedOrder.isNotBlank()) return deserializeProviderOrder(persistedOrder)

        val preferred = preferences[PreferredLyricsProviderKey]
            .toEnum(PreferredLyricsProvider.YOULYPLUS)
        val preferredName = getProviderNameForEnum(preferred)
        return listOf(preferredName) + getDefaultProviderOrder().filter { it != preferredName }
    }

    fun getDefaultProviderOrder(): List<String> = listOf(
        "YouLyPlus",
        "Paxsenix",
        "Unison",
        "BetterLyrics",
        "SimpMusic",
        "LrcLib",
        "Kugou",
        "LetrasCom",
        "YouTubeSubtitle",
        "YouTubeMusic",
    )

    fun getOrderedProviders(orderString: String): List<LyricsProvider> =
        deserializeProviderOrder(orderString).mapNotNull { getProviderByName(it) }

    /** Resolves enabled provider names without performing any DataStore reads or initialization. */
    fun getEnabledProviderNames(
        order: List<String>,
        preferences: Preferences,
    ): List<String> = order.filter { name ->
        val registration = registrationMap[name] ?: return@filter false
        registration.enabledKey?.let { key ->
            preferences[key] ?: registration.defaultEnabled
        } ?: registration.defaultEnabled
    }

    /** Resolve and prepare enabled providers without performing any DataStore reads. */
    fun getOrderedEnabledProviders(
        order: List<String>,
        preferences: Preferences,
        context: Context,
    ): List<LyricsProvider> = getEnabledProviderNames(order, preferences).mapNotNull { name ->
        val registration = registrationMap[name] ?: return@mapNotNull null
        registration.prepare(context)
        registration.provider
    }

    fun getProviderNameForEnum(enum: PreferredLyricsProvider): String = when (enum) {
        PreferredLyricsProvider.LRCLIB -> "LrcLib"
        PreferredLyricsProvider.KUGOU -> "Kugou"
        PreferredLyricsProvider.BETTER_LYRICS -> "BetterLyrics"
        PreferredLyricsProvider.SIMPMUSIC -> "SimpMusic"
        PreferredLyricsProvider.YOULYPLUS -> "YouLyPlus"
        PreferredLyricsProvider.PAXSENIX -> "Paxsenix"
        PreferredLyricsProvider.UNISON -> "Unison"
        PreferredLyricsProvider.LETRAS_COM -> "LetrasCom"
    }

    fun getDisplayName(name: String): String = registrationMap[name]?.displayName ?: name
}
