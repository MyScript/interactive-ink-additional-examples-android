// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.serialization

import com.microsoft.device.ink.InkView
import com.microsoft.device.ink.InputManager
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
        val point1 = InputManager.PenInfo(
            pointerType = InputManager.PointerType.PEN_TIP,
            x = 1f,
            y = 1f,
            timestamp = 0L,
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
            timestamp = 1000L,
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
            timestamp = 2000L,
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

        val serialized = listOf(brush).json()

        val initialTimestamp = "1970-01-01 01:00:00.000"
        val json = "{\"version\":\"3\",\"type\":\"Drawing\",\"id\":\"MainBlock\",\"items\":[{\"timestamp\":\"$initialTimestamp\",\"X\":[1.0,2.0,3.0],\"Y\":[1.0,2.0,3.0],\"T\":[0,1000,2000],\"F\":[0.0,0.0,0.0],\"type\":\"stroke\",\"id\":\"1\"}]}"
        assertEquals(json, serialized)
    }

    @Test
    fun serializeEmptyStrokeListTest() {
        val serialized = emptyList<InkView.Brush>().json()

        val json = "{\"version\":\"3\",\"type\":\"Drawing\",\"id\":\"MainBlock\",\"items\":[]}"

        assertEquals(json, serialized)
    }

    @Test
    fun deserializeStrokesTest() {
        val initialTimestamp = "1970-01-01 01:00:00.000"
        val json = "{\"version\":\"3\",\"type\":\"Drawing\",\"id\":\"MainBlock\",\"items\":[{\"timestamp\":\"$initialTimestamp\",\"X\":[1.0,2.0,3.0],\"Y\":[1.0,2.0,3.0],\"T\":[0,1000,2000],\"F\":[0.0,0.0,0.0],\"type\":\"stroke\",\"id\":\"1\"}]}"

        val brushes = parseJson(json)

        assertTrue(brushes.isNotEmpty())
        assertTrue(brushes.size == 1)

        val brush = brushes.first()
        val points = brush.stroke.getPoints()
        assertTrue(points.isNotEmpty())
        assertTrue(points.size == 3)

        val point1 = brush.stroke.getPenInfo(points.first())
        assertTrue(point1?.x == 1f)
        assertTrue(point1?.y == 1f)
        assertTrue(point1?.timestamp == 0L)

        val point2 = brush.stroke.getPenInfo(points[1])
        assertTrue(point2?.x == 2f)
        assertTrue(point2?.y == 2f)
        assertTrue(point2?.timestamp == 1000L)

        val point3 = brush.stroke.getPenInfo(points[2])
        assertTrue(point3?.x == 3f)
        assertTrue(point3?.y == 3f)
        assertTrue(point3?.timestamp == 2000L)
    }

    @Test
    fun deserializeEmptyStrokeListTest() {
        val json = "{\"version\":\"3\",\"type\":\"Drawing\",\"id\":\"MainBlock\",\"items\":[]}"

        val brushes = parseJson(json)

        assertTrue(brushes.isEmpty())
    }
}
