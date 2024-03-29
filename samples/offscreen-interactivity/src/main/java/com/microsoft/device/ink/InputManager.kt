/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License.
 */

/*
 * https://github.com/microsoft/surface-duo-sdk/blob/main/inksdk/ink/src/main/java/com/microsoft/device/ink/InputManager.kt
 */

package com.microsoft.device.ink

import android.annotation.SuppressLint
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

class InputManager(
    view: View,
    private val penInputHandler: PenInputHandler,
    private val penHoverHandler: PenHoverHandler? = null,
    private val timeOffset: Long = System.currentTimeMillis() - SystemClock.uptimeMillis()) {

    var currentStroke = ExtendedStroke()

    init {
        setupInputEvents(view)
        currentStroke.reset()
    }

    interface PenInputHandler {
        fun strokeStarted(penInfo: PenInfo, stroke: ExtendedStroke)
        fun strokeUpdated(penInfo: PenInfo, stroke: ExtendedStroke)
        fun strokeCompleted(penInfo: PenInfo, stroke: ExtendedStroke)
    }

    interface PenHoverHandler {
        fun hoverStarted(penInfo: PenInfo)
        fun hoverMoved(penInfo: PenInfo)
        fun hoverEnded(penInfo: PenInfo)
    }

    enum class PointerType {
        MOUSE,
        FINGER,
        PEN_TIP,
        PEN_ERASER,
        UNKNOWN
    }

    class Point(
        val x: Float,
        val y: Float,
    )

    data class PenInfo(
        val pointerType: PointerType,
        val x: Float,
        val y: Float,
        val timestamp: Long,
        val pressure: Float,
        val orientation: Float,
        val tilt: Float,
        val primaryButtonState: Boolean,
        val secondaryButtonState: Boolean
    ) {
        companion object {
            fun createFromEvent(event: MotionEvent, timeOffset: Long): PenInfo {
                val pointerType: PointerType = when (event.getToolType(0)) {
                    MotionEvent.TOOL_TYPE_FINGER -> PointerType.FINGER
                    MotionEvent.TOOL_TYPE_MOUSE -> PointerType.MOUSE
                    MotionEvent.TOOL_TYPE_STYLUS -> PointerType.PEN_TIP
                    MotionEvent.TOOL_TYPE_ERASER -> PointerType.PEN_ERASER
                    else -> PointerType.UNKNOWN
                }

                return PenInfo(
                    pointerType = pointerType,
                    x = event.x,
                    y = event.y,
                    timestamp = timeOffset + event.eventTime,
                    pressure = event.pressure,
                    orientation = event.orientation,
                    tilt = event.getAxisValue(MotionEvent.AXIS_TILT),
                    primaryButtonState = ((event.buttonState and MotionEvent.BUTTON_PRIMARY) > 0)
                            or ((event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) > 0),
                    secondaryButtonState = ((event.buttonState and MotionEvent.BUTTON_SECONDARY) > 0)
                            or ((event.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY) > 0)
                )
            }

            fun createFromHistoryEvent(event: MotionEvent, pos: Int, timeOffset: Long): PenInfo {
                val pointerType: PointerType = when (event.getToolType(0)) {
                    MotionEvent.TOOL_TYPE_FINGER -> PointerType.FINGER
                    MotionEvent.TOOL_TYPE_MOUSE -> PointerType.MOUSE
                    MotionEvent.TOOL_TYPE_STYLUS -> PointerType.PEN_TIP
                    MotionEvent.TOOL_TYPE_ERASER -> PointerType.PEN_ERASER
                    else -> PointerType.UNKNOWN
                }

                return PenInfo(
                    pointerType = pointerType,
                    x = event.getHistoricalX(pos),
                    y = event.getHistoricalY(pos),
                    timestamp = timeOffset + event.getHistoricalEventTime(pos),
                    pressure = event.getHistoricalPressure(pos),
                    orientation = event.getHistoricalOrientation(pos),
                    tilt = event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, pos),
                    primaryButtonState = ((event.buttonState and MotionEvent.BUTTON_PRIMARY) > 0)
                            or ((event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) > 0),
                    secondaryButtonState = ((event.buttonState and MotionEvent.BUTTON_SECONDARY) > 0)
                            or ((event.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY) > 0)
                )
            }
        }
    }

    class ExtendedStroke {
        private var builder = mutableListOf<Point>()
        private var penInfos = HashMap<Int, PenInfo>()

        private var _lastPointReferenced = 0
        var lastPointReferenced: Int
            get() = _lastPointReferenced
            set(value) {
                _lastPointReferenced = value
            }

        fun addPoint(penInfo: PenInfo) {
            val point = Point(penInfo.x, penInfo.y)
            builder.add(point)
            penInfos[builder.lastIndex] = penInfo // hash codes don't serialize well, so use index
        }

        fun getPoints(): List<Point> {
            return builder
        }

        fun getPenInfo(point: Point): PenInfo? {
            return penInfos[builder.indexOf(point)]
        }

        fun reset() {
            builder.clear()
            lastPointReferenced = 0
            penInfos.clear()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupInputEvents(view: View) {

        view.setOnGenericMotionListener { _: View, event: MotionEvent ->
            var consumed = true
            if (penHoverHandler == null) {
                consumed = false
            } else {
                val penInfo = PenInfo.createFromEvent(event, timeOffset)

                when (event.actionMasked) {
                    MotionEvent.ACTION_HOVER_MOVE -> {
                        for (i in 0 until event.historySize) {
                            penHoverHandler.hoverMoved(PenInfo.createFromHistoryEvent(event, i, timeOffset))
                        }
                        penHoverHandler.hoverMoved(penInfo)
                    }
                    MotionEvent.ACTION_HOVER_ENTER -> {
                        penHoverHandler.hoverStarted(penInfo)
                    }
                    MotionEvent.ACTION_HOVER_EXIT -> {
                        penHoverHandler.hoverEnded(penInfo)
                    }
                    else -> consumed = false
                }
            }
            consumed
        }
        view.setOnTouchListener { _: View, event: MotionEvent ->
            var consumed = true
            val penInfo = PenInfo.createFromEvent(event, timeOffset)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    currentStroke = ExtendedStroke()
                    currentStroke.addPoint(penInfo)
                    penInputHandler.strokeStarted(penInfo, currentStroke)
                }
                MotionEvent.ACTION_MOVE -> {

                    for (i in 0 until event.historySize) {
                        currentStroke.addPoint(PenInfo.createFromHistoryEvent(event, i, timeOffset))
                    }
                    currentStroke.addPoint(penInfo)
                    penInputHandler.strokeUpdated(penInfo, currentStroke)
                }
                MotionEvent.ACTION_UP -> {
                    currentStroke.addPoint(penInfo)
                    penInputHandler.strokeCompleted(penInfo, currentStroke)
                }
                else -> consumed = false
            }

            consumed
        }
    }
}