

package com.auriqo.music.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.auriqo.music.extensions.toEnum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.properties.ReadOnlyProperty

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

suspend fun <T> DataStore<Preferences>.read(key: Preferences.Key<T>): T? =
    data.first()[key]

suspend fun <T> DataStore<Preferences>.read(
    key: Preferences.Key<T>,
    defaultValue: T,
): T = data.first()[key] ?: defaultValue

@Deprecated(
    message = "Blocking preference reads can stall the caller. Use read() or the DataStore flow instead.",
)
operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T? =
    runBlocking(Dispatchers.IO) {
        read(key)
    }

@Deprecated(
    message = "Blocking preference reads can stall the caller. Use read() or the DataStore flow instead.",
)
fun <T> DataStore<Preferences>.get(
    key: Preferences.Key<T>,
    defaultValue: T,
): T =
    runBlocking(Dispatchers.IO) {
        read(key, defaultValue)
    }

fun <T> preference(
    context: Context,
    key: Preferences.Key<T>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key] ?: defaultValue }

inline fun <reified T : Enum<T>> enumPreference(
    context: Context,
    key: Preferences.Key<String>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key].toEnum(defaultValue) }

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val state =
        remember(key, defaultValue) {
            context.dataStore.data
                .map { it[key] ?: defaultValue }
                .distinctUntilChanged()
        }.collectAsState(initial = defaultValue)

    return remember(key, coroutineScope, state) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        context.dataStore.edit {
                            it[key] = value
                        }
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val state =
        remember(key, defaultValue) {
            context.dataStore.data
                .map { it[key].toEnum(defaultValue = defaultValue) }
                .distinctUntilChanged()
        }.collectAsState(initial = defaultValue)

    return remember(key, coroutineScope, state) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        context.dataStore.edit {
                            it[key] = value.name
                        }
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
