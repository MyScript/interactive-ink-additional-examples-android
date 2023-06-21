// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.microsoft.device.ink.InkView
import com.microsoft.device.ink.InputManager
import com.myscript.iink.Engine
import com.myscript.iink.demo.inksample.data.InkRepository
import com.myscript.iink.demo.inksample.ui.InkViewModel
import com.myscript.nebo.test.utils.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunWith(JUnit4::class)
class InkViewModelTests {

    // Run tasks synchronously
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    // Sets the main coroutines dispatcher to a TestCoroutineScope for unit testing.
    @get:Rule
    var mainDispatcherRule = MainDispatcherRule()

    private val inkRepository = FakeInkRepository()

    private inline fun usingViewModel(
        repository: InkRepository = inkRepository,
        engine: Engine? = null,
        dataDir: File = File("data"),
        exportConfiguration: String = "",
        test: InkViewModel.() -> Unit
    ) {
        InkViewModel(
            repository = repository,
            engine = engine,
            dataDir = dataDir,
            exportConfiguration = exportConfiguration,
            uiDispatcher = mainDispatcherRule.testDispatcher,
            ioDispatcher = mainDispatcherRule.testDispatcher,
            defaultDispatcher = mainDispatcherRule.testDispatcher
        ).also { inkViewModel ->
            try {
                test(inkViewModel)
            } finally {
                inkViewModel.onCleared()
            }
        }
    }

    @Test
    fun `loading ink should update live data`() = runTest {
        inkRepository.saveInkToFile("{\"version\":\"3\",\"type\":\"Drawing\",\"id\":\"MainBlock\",\"items\":[{\"timestamp\":\"2023-06-21 11:44:00.000\",\"X\":[1.0,2.0,3.0],\"Y\":[1.0,2.0,3.0],\"F\":[0.0,0.0,0.0],\"T\":[0.0,0.0,0.0],\"type\":\"stroke\",\"id\":\"1\"}]}")

        usingViewModel {
            loadInk()
            assertTrue(strokes.getOrAwaitValue().isNotEmpty())
        }
    }

    @Test
    fun `loading ink when the repository returns a null json string should result in an empty strokes list`() = runTest {
        usingViewModel {
            loadInk()
            assertTrue(strokes.getOrAwaitValue().isEmpty())
        }
    }

    @Test
    fun `clearing ink should empty live data's content`() = runTest {
        // start with a state where the viewModel has strokes
        inkRepository.saveInkToFile("{\"version\":\"3\",\"type\":\"Drawing\",\"id\":\"MainBlock\",\"items\":[{\"timestamp\":\"2023-06-21 11:44:00.000\",\"X\":[1.0,2.0,3.0],\"Y\":[1.0,2.0,3.0],\"F\":[0.0,0.0,0.0],\"T\":[0.0,0.0,0.0],\"type\":\"stroke\",\"id\":\"1\"}]}")

        usingViewModel {
            loadInk()
            clearInk()
            assertTrue(strokes.getOrAwaitValue().isEmpty())
        }
    }

    @Test
    fun `adding a stroke should update live data`() = runTest {
        val point1 = InputManager.PenInfo(
            pointerType = InputManager.PointerType.PEN_TIP,
            x = 1f,
            y = 1f,
            timestamp = -1,
            pressure = 0f,
            orientation = 0f,
            tilt = 0f,
            primaryButtonState = false,
            secondaryButtonState = false
        )
        val point2 = InputManager.PenInfo(
            pointerType = InputManager.PointerType.PEN_TIP,
            x = 2f,
            y = 2f,
            timestamp = -1,
            pressure = 0f,
            orientation = 0f,
            tilt = 0f,
            primaryButtonState = false,
            secondaryButtonState = false
        )
        val point3 = InputManager.PenInfo(
            pointerType = InputManager.PointerType.PEN_TIP,
            x = 3f,
            y = 3f,
            timestamp = -1,
            pressure = 0f,
            orientation = 0f,
            tilt = 0f,
            primaryButtonState = false,
            secondaryButtonState = false
        )
        val stroke = InputManager.ExtendedStroke().apply {
            addPoint(point1)
            addPoint(point2)
            addPoint(point3)
        }
        val brush = InkView.Brush(
            id = "1",
            stroke = stroke
        )

        usingViewModel {
            val oldStrokes = strokes.getOrAwaitValue()
            addStroke(brush)
            val currentStrokes = strokes.getOrAwaitValue()
            assertTrue(currentStrokes.size == oldStrokes.size + 1)
        }
    }
}