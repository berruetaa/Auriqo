package com.auriqo.music

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.crossfade
import com.auriqo.music.constants.*
import com.auriqo.music.di.ApplicationScope
import com.auriqo.music.echomusic.updater.scheduleUpdateChecks
import com.auriqo.music.extensions.toEnum
import com.auriqo.music.extensions.toInetSocketAddress
import com.auriqo.music.listentogether.ListenTogetherServers
import com.auriqo.music.utils.CrashHandler
import com.auriqo.music.utils.cipher.CipherDeobfuscator
import com.auriqo.music.utils.dataStore
import com.auriqo.music.utils.debug.DebugLogTree
import com.auriqo.music.utils.reportException
import com.music.innertube.YouTube
import com.music.innertube.YouTubeAccountSession
import com.music.innertube.YouTubeConnectionConfig
import com.music.innertube.models.IpVersion
import com.music.innertube.models.YouTubeLocale
import com.music.kugou.KuGou
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import timber.log.Timber
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.Proxy
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory {

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun startForegroundService(service: Intent): android.content.ComponentName? {
        return try {
            super.startForegroundService(service)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is android.app.ForegroundServiceStartNotAllowedException) {
                Timber.e(e, "Suppressed ForegroundServiceStartNotAllowedException in App")
                null
            } else {
                throw e
            }
        }
    }

    private fun isCrashProcess(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName() == "$packageName:crash"
        }
        val pid = android.os.Process.myPid()
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (processInfo in manager.runningAppProcesses ?: emptyList()) {
            if (processInfo.pid == pid) {
                return processInfo.processName == "$packageName:crash"
            }
        }
        return false
    }

    override fun onCreate() {
        super.onCreate()

        if (isCrashProcess()) {
            CrashHandler.install(this)
            return
        }

        CrashHandler.install(this)
        CipherDeobfuscator.initialize(this)

        if (BuildConfig.DEBUG) {
            DebugLogTree.install()
            Timber.plant(Timber.DebugTree())
        }

        scheduleUpdateChecks(this)

        applicationScope.launch(Dispatchers.IO) {
            ListenTogetherServers.refresh()
        }

        applicationScope.launch(Dispatchers.IO) {
            cachedCoilCacheSize = dataStore.data.map { it[MaxImageCacheSizeKey] ?: 512 }.first()
        }

        applicationScope.launch {
            initializeSettings()
            observeSettingsChanges()
        }
    }

    private suspend fun initializeSettings() {
        val settings = dataStore.data.first()
        val languageTag = Locale.getDefault().language

        if (languageTag == "zh-TW") {
            KuGou.useTraditionalChinese = true
        }

        applyConnectionSettings(settings.toInnerTubeConnectionPreferences())

        val channel = NotificationChannel(
            "updates",
            getString(R.string.update_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.update_channel_desc)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun observeSettingsChanges() {
        com.auriqa.music.utils.lastfm.LastFM.initialize(
            apiKey = BuildConfig.LASTFM_API_KEY.takeIf { it.isNotEmpty() } ?: "",
            secret = BuildConfig.LASTFM_SECRET.takeIf { it.isNotEmpty() } ?: "",
        )

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { preferences -> preferences.toInnerTubeConnectionPreferences() }
                .distinctUntilChanged()
                .collect(::applyConnectionSettings)
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { preferences -> preferences.toInnerTubeAccountPreferences() }
                .distinctUntilChanged()
                .collect(::applyAccountSettings)
        }
    }

    private suspend fun applyConnectionSettings(settings: InnerTubeConnectionPreferences) {
        val systemLocale = Locale.getDefault()
        val effectiveAppLocale = settings.appLanguage
            ?.takeUnless { it == SYSTEM_DEFAULT }
            ?.let(Locale::forLanguageTag)
            ?: systemLocale

        val locale = YouTubeLocale(
            gl = settings.contentCountry?.takeIf { it != SYSTEM_DEFAULT }
                ?: effectiveAppLocale.country.takeIf { it in CountryCodeToName }
                ?: systemLocale.country.takeIf { it in CountryCodeToName }
                ?: "US",
            hl = settings.contentLanguage?.takeIf { it != SYSTEM_DEFAULT }
                ?: effectiveAppLocale.toLanguageTag().takeIf { it in LanguageCodeToName }
                ?: effectiveAppLocale.language.takeIf { it in LanguageCodeToName }
                ?: "en",
        )

        var proxy: Proxy? = null
        var proxyAuth: String? = null
        if (settings.proxyEnabled) {
            val type = settings.proxyType.toEnum(defaultValue = Proxy.Type.HTTP)
            val username = settings.proxyUsername.orEmpty()
            val password = settings.proxyPassword.orEmpty()

            if (username.isNotEmpty() || password.isNotEmpty()) {
                if (type == Proxy.Type.HTTP) {
                    proxyAuth = Credentials.basic(username, password)
                } else {
                    Authenticator.setDefault(object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication =
                            PasswordAuthentication(username, password.toCharArray())
                    })
                }
            }

            try {
                settings.proxyUrl?.let {
                    proxy = Proxy(type, it.toInetSocketAddress())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@App, getString(R.string.failed_to_parse_proxy), Toast.LENGTH_SHORT).show()
                }
                reportException(e)
            }
        }

        YouTube.applyConnectionConfig(
            YouTubeConnectionConfig(
                locale = locale,
                proxy = proxy,
                proxyAuth = proxyAuth,
                useLoginForBrowse = settings.useLoginForBrowse,
                ipVersion = settings.ipVersion.toEnum(defaultValue = IpVersion.AUTO),
            ),
        )
    }

    private suspend fun applyAccountSettings(settings: InnerTubeAccountPreferences) {
        val visitorData = settings.visitorData
            ?.takeIf { it != "null" }
            ?: YouTube.visitorData().getOrNull()?.also { newVisitorData ->
                dataStore.edit { preferences ->
                    preferences[VisitorDataKey] = newVisitorData
                }
            }

        val dataSyncId = settings.dataSyncId?.let {
            it.takeIf { value -> !value.contains("||") }
                ?: it.takeIf { value -> value.endsWith("||") }?.substringBefore("||")
                ?: it.substringAfter("||")
        }

        try {
            YouTube.applyAccountSession(
                YouTubeAccountSession(
                    cookie = settings.cookie,
                    visitorData = visitorData,
                    dataSyncId = dataSyncId,
                ),
            )
        } catch (e: Exception) {
            Timber.e(e, "Could not apply InnerTube account session. Clearing existing account state.")
            forgetAccount(this@App)
        }
    }

    private fun Preferences.toInnerTubeConnectionPreferences() = InnerTubeConnectionPreferences(
        contentCountry = this[ContentCountryKey],
        contentLanguage = this[ContentLanguageKey],
        appLanguage = this[AppLanguageKey],
        proxyEnabled = this[ProxyEnabledKey] == true,
        proxyUsername = this[ProxyUsernameKey],
        proxyPassword = this[ProxyPasswordKey],
        proxyType = this[ProxyTypeKey],
        proxyUrl = this[ProxyUrlKey],
        useLoginForBrowse = this[UseLoginForBrowse] ?: true,
        ipVersion = this[IpVersionKey],
    )

    private fun Preferences.toInnerTubeAccountPreferences() = InnerTubeAccountPreferences(
        cookie = this[InnerTubeCookieKey],
        visitorData = this[VisitorDataKey],
        dataSyncId = this[DataSyncIdKey],
    )

    private data class InnerTubeConnectionPreferences(
        val contentCountry: String?,
        val contentLanguage: String?,
        val appLanguage: String?,
        val proxyEnabled: Boolean,
        val proxyUsername: String?,
        val proxyPassword: String?,
        val proxyType: String?,
        val proxyUrl: String?,
        val useLoginForBrowse: Boolean,
        val ipVersion: String?,
    )

    private data class InnerTubeAccountPreferences(
        val cookie: String?,
        val visitorData: String?,
        val dataSyncId: String?,
    )

    @Volatile
    private var cachedCoilCacheSize: Int? = null

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val cacheSize = cachedCoilCacheSize ?: 512
        return ImageLoader.Builder(this).apply {
            crossfade(true)
            allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)

            memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            if (cacheSize == 0) {
                diskCachePolicy(CachePolicy.DISABLED)
            } else {
                diskCache(
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("coil"))
                        .maxSizeBytes(cacheSize * 1024 * 1024L)
                        .build()
                )
            }
        }.build()
    }

    companion object {
        suspend fun forgetAccount(context: Context) {
            Timber.d("forgetAccount: Starting logout process")

            context.dataStore.edit { settings ->
                settings.remove(InnerTubeCookieKey)
                settings.remove(VisitorDataKey)
                settings.remove(DataSyncIdKey)
                settings.remove(AccountNameKey)
                settings.remove(AccountEmailKey)
                settings.remove(AccountChannelHandleKey)
            }

            YouTube.applyAccountSession(
                YouTubeAccountSession(
                    cookie = null,
                    visitorData = null,
                    dataSyncId = null,
                ),
            )

            withContext(Dispatchers.Main) {
                android.webkit.CookieManager.getInstance().apply {
                    removeAllCookies { removed ->
                        Timber.d("forgetAccount: CookieManager.removeAllCookies callback: removed=$removed")
                    }
                    flush()
                }
            }
            Timber.d("forgetAccount: Logout process complete")
        }
    }
}
