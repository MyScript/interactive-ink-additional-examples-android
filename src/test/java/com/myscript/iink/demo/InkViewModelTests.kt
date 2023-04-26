package com.myscript.iink.demo

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.myscript.iink.demo.ink.InkView
import com.myscript.iink.demo.ink.InputManager
import com.myscript.iink.demo.inksample.ui.InkViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class InkViewModelTests {

    // Run tasks synchronously
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    // Sets the main coroutines dispatcher to a TestCoroutineScope for unit testing.
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private val inkRepository = FakeInkRepository()

    private lateinit var inkViewModel: InkViewModel

    @Before
    fun setUp() {
        inkViewModel = InkViewModel(repository = FakeInkRepository())
    }

    @Test
    fun `loading ink should update live data`() = runBlocking {
        inkRepository.saveInkToFile("{\"version\":\"3\",\"type\":\"Drawing\",\"id\":\"MainBlock\",\"items\":[{\"timestamp\":\"-1\",\"X\":[1.0,2.0,3.0],\"Y\":[1.0,2.0,3.0],\"F\":[0.0,0.0,0.0],\"type\":\"stroke\",\"id\":\"1\"}]}")

        inkViewModel.loadInk()
        assertTrue(inkViewModel.strokes.getOrAwaitValue().isNotEmpty())
    }

    @Test
    fun `loading ink when the repository returns a null json string should result in an empty strokes list`() = runBlocking {
        inkViewModel.loadInk()
        assertTrue(inkViewModel.strokes.getOrAwaitValue().isEmpty())
    }

    @Test
    fun `clearing ink should empty live data's content`() = runBlocking {
        // start with a state where the viewModel has strokes
        inkRepository.saveInkToFile("{\"version\":\"3\",\"type\":\"Drawing\",\"id\":\"MainBlock\",\"items\":[{\"timestamp\":\"-1\",\"X\":[1.0,2.0,3.0],\"Y\":[1.0,2.0,3.0],\"F\":[0.0,0.0,0.0],\"type\":\"stroke\",\"id\":\"1\"}]}")
        inkViewModel.loadInk()

        inkViewModel.clearInk()
        assertTrue(inkViewModel.strokes.getOrAwaitValue().isEmpty())
    }

    @Test
    fun `adding a stroke should update live data`() = runBlocking {
        val point1 = InputManager.PenInfo(
            pointerType = InputManager.PointerType.PEN_TIP,
            x = 1f,
            y = 1f,
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

        val oldStrokes = inkViewModel.strokes.getOrAwaitValue()
        inkViewModel.addStroke(brush)
        val currentStrokes = inkViewModel.strokes.getOrAwaitValue()
        assertTrue(currentStrokes.size == oldStrokes.size + 1)
    }
}