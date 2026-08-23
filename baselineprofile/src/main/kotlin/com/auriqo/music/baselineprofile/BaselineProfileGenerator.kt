package com.auriqo.music.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun coreNavigation() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
    ) {
        pressHome()
        startActivityAndWait(
            Intent().setClassName(PACKAGE_NAME, MAIN_ACTIVITY).setAction(ACTION_SEARCH),
        )

        pressHome()
        startActivityAndWait(
            Intent().setClassName(PACKAGE_NAME, MAIN_ACTIVITY).setAction(ACTION_LIBRARY),
        )
    }

    private companion object {
        const val PACKAGE_NAME = "com.auriqo.music"
        const val MAIN_ACTIVITY = "$PACKAGE_NAME.MainActivity"
        const val ACTION_SEARCH = "com.auriqo.music.action.SEARCH"
        const val ACTION_LIBRARY = "com.auriqo.music.action.LIBRARY"
    }
}
