package com.auriqo.music.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupWithBaselineProfile() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Require,
        ),
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    @Test
    fun coldStartupWithoutPrecompilation() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    private companion object {
        const val PACKAGE_NAME = "com.auriqo.music"
    }
}
