package com.myscript.iink.demo

import com.myscript.iink.demo.ink.InkView.Brush
import com.myscript.iink.demo.ink.InputManager
import com.myscript.iink.demo.ink.InputManager.ExtendedStroke
import com.myscript.iink.demo.ink.InputManager.PenInfo
import com.myscript.iink.demo.ink.serialization.json
import com.myscript.iink.demo.ink.serialization.parseJson
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class InkParserTests {

    @Test
    fun serializeStrokesTest() {
        val point1 = PenInfo(
            pointerType = InputManager.PointerType.PEN_TIP,
            x = 1f,
            y = 1f,
            pressure = 0f,
            orientation = 0f,
            tilt = 0f,
            primaryButtonState = false,
            secondaryButtonState = false
        )
        val point2 = PenInfo(
            pointerType = InputManager.PointerType.PEN_TIP,
            x = 2f,
            y = 2f,
            pressure = 0f,
            orientation = 0f,
            tilt = 0f,
            primaryButtonState = false,
            secondaryButtonState = false
        )
        val point3 = PenInfo(
            pointerType = InputManager.PointerType.PEN_TIP,
            x = 3f,
            y = 3f,
            pressure = 0f,
            orientation = 0f,
            tilt = 0f,
            primaryButtonState = false,
            secondaryButtonState = false
        )
        val stroke = ExtendedStroke().apply {
            addPoint(point1)
            addPoint(point2)
            addPoint(point3)
        }
        val brush = Brush(
            id = "1",
            stroke = stroke
        )

        val serialized = listOf(brush).json()

        val json = "{\"version\":\"3\",\"type\":\"Drawing\",\"id\":\"MainBlock\",\"items\":[{\"timestamp\":\"-1\",\"X\":[1.0,2.0,3.0],\"Y\":[1.0,2.0,3.0],\"F\":[0.0,0.0,0.0],\"type\":\"stroke\",\"id\":\"1\"}]}"
        assertEquals(json, serialized)
    }

    @Test
    fun serializeEmptyStrokeListTest() {
        val serialized = emptyList<Brush>().json()

        val json = "{\"version\":\"3\",\"type\":\"Drawing\",\"id\":\"MainBlock\",\"items\":[]}"

        assertEquals(json, serialized)
    }

    @Test
    fun deserializeStrokesTest() {
        val json = "{\"version\":\"3\",\"type\":\"Drawing\",\"id\":\"MainBlock\",\"items\":[{\"timestamp\":\"-1\",\"X\":[1.0,2.0,3.0],\"Y\":[1.0,2.0,3.0],\"F\":[0.0,0.0,0.0],\"type\":\"stroke\",\"id\":\"1\"}]}"

        val brushes = parseJson(json)

        assertTrue(brushes.isNotEmpty())
        assertTrue(brushes.size == 1)

        val brush = brushes.first()
        val points = brush.stroke.getPoints()
        assertTrue(points.isNotEmpty())
        assertTrue(points.size == 3)

        val point1 = points.first()
        assertTrue(point1.x == 1f)
        assertTrue(point1.y == 1f)

        val point2 = points[1]
        assertTrue(point2.x == 2f)
        assertTrue(point2.y == 2f)

        val point3 = points[2]
        assertTrue(point3.x == 3f)
        assertTrue(point3.y == 3f)
    }

    @Test
    fun deserializeEmptyStrokeListTest() {
        val json = "{\"version\":\"3\",\"type\":\"Drawing\",\"id\":\"MainBlock\",\"items\":[]}"

        val brushes = parseJson(json)

        assertTrue(brushes.isEmpty())
    }
}