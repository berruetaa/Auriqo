

package com.auriqo.music.listentogether

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import java.net.URI

@Serializable
data class ListenTogetherServer(
    val name: String,
    val url: String,
    val location: String,
    val operator: String
)

object ListenTogetherServers {
    private const val SERVER_JSON_URL = "https://raw.githubusercontent.com/Auriqo/Auriqo/refs/heads/main/app/server.json"

    private val FALLBACK_SERVER = ListenTogetherServer(
        name = "Auriqo Server",
        url = "wss://berruetaa-echomusic.hf.space/ws",
        location = "Global",
        operator = "ECHO"
    )

    private val _servers = MutableStateFlow(listOf(FALLBACK_SERVER))
    
    val serversFlow: StateFlow<List<ListenTogetherServer>> = _servers

    val servers: List<ListenTogetherServer>
        get() = _servers.value

    private val LOCAL_CLEARTEXT_HOSTS = setOf(
        "localhost",
        "127.0.0.1",
        "10.0.2.2",
        "10.0.3.2",
    )

    fun isAllowedServerUrl(value: String): Boolean {
        val parsed = runCatching { URI(value.trim()) }.getOrNull() ?: return false
        val scheme = parsed.scheme?.lowercase() ?: return false
        val host = parsed.host?.lowercase() ?: return false
        if (parsed.userInfo != null || parsed.fragment != null) return false
        return when (scheme) {
            "wss" -> true
            "ws" -> host in LOCAL_CLEARTEXT_HOSTS
            else -> false
        }
    }

    init {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder().url(SERVER_JSON_URL).build()
                val response = client.newCall(request).execute()
                response.use { responseBody ->
                    if (!responseBody.isSuccessful) return@use
                    val jsonString = responseBody.body.string()
                    val jsonObject = Json.parseToJsonElement(jsonString).jsonObject
                    val name = jsonObject["name"]?.jsonPrimitive?.content ?: "Hugging Face Sync"
                    val url = jsonObject["serverUrl"]?.jsonPrimitive?.content ?: "wss://devilmi-vivi-music-listen-together.hf.space"
                    val region = jsonObject["region"]?.jsonPrimitive?.content ?: "Global - VIVIDH"

                    if (!isAllowedServerUrl(url)) return@use
                    _servers.value = listOf(
                        ListenTogetherServer(
                            name = name,
                            url = url,
                            location = region,
                            operator = ""
                        )
                    )
                }
            } catch (e: Exception) {
                // Fallback implicitly retained
            }
        }
    }

    val defaultServerUrl: String
        get() = servers.first().url

    fun findByUrl(url: String): ListenTogetherServer? = servers.firstOrNull { it.url == url }
}
