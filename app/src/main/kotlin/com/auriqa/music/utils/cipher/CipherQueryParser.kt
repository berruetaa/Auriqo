package com.auriqo.music.utils.cipher

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Parses the URL-encoded query carried by YouTube's signatureCipher/cipher fields. */
internal object CipherQueryParser {
    fun parse(query: String): Map<String, String> = buildMap {
        for (pair in query.split('&')) {
            val separator = pair.indexOf('=')
            if (separator <= 0) continue

            val key = decode(pair.substring(0, separator))
            val value = decode(pair.substring(separator + 1))
            put(key, value)
        }
    }

    private fun decode(value: String): String = URLDecoder.decode(
        value.replace("+", "%2B"),
        StandardCharsets.UTF_8.name(),
    )
}
