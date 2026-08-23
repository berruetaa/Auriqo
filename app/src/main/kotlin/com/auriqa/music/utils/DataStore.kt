

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
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.auriqo.music.extensions.toEnum
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

suspend fun <T> DataStore<Preferences>.read(key: Preferences.Key<T>): T? =
    data.first()[key]

suspend fun <T> DataStore<Preferences>.read(
    key: Preferences.Key<T>,
    defaultValue: T,
): T = data.first()[key] ?: defaultValue

/**
 * Transitional non-blocking snapshot for legacy synchronous call sites.
 *
 * The first call returns the safe default while DataStore is being collected; subsequent calls
 * observe the latest emitted preferences. New code must use [read] or collect [DataStore.data]
 * directly so it can handle the initial load explicitly.
 */
private object DataStoreSnapshotRegistry {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val snapshots = ConcurrentHashMap<DataStore<Preferences>, StateFlow<Preferences>>()

    fun current(dataStore: DataStore<Preferences>): Preferences =
        snapshots.getOrPut(dataStore) {
            dataStore.data.stateIn(scope, SharingStarted.Eagerly, emptyPreferences())
        }.value
}

/**
 * Reads the latest already-emitted value without waiting for DataStore.
 *
 * This is only for synchronous Android callbacks and constructors that cannot suspend. It returns
 * the supplied default until the DataStore flow has emitted; suspend code must use [read] instead.
 */
fun <T> DataStore<Preferences>.snapshot(key: Preferences.Key<T>): T? =
    DataStoreSnapshotRegistry.current(this)[key]

fun <T> DataStore<Preferences>.snapshot(
    key: Preferences.Key<T>,
    defaultValue: T,
): T = DataStoreSnapshotRegistry.current(this)[key] ?: defaultValue

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
