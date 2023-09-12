// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.ink.serialization

import com.google.gson.Gson
import com.microsoft.device.ink.InkView.Brush
import com.microsoft.device.ink.InputManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun List<Brush>.json(): String {
    val inkFormats = mapNotNull { brush ->
        val stroke = brush.stroke
        val points = stroke.getPoints()

        val initialTimestamp = stroke.getPenInfo(points.first())?.timestamp ?: return@mapNotNull null

        val deltas = points.mapNotNull { point ->
            stroke.getPenInfo(point)?.timestamp?.let { timestamp -> timestamp - initialTimestamp }
        }

        InkFormat(
            timestamp = formatTimestamp(initialTimestamp),
            X = points.map { point -> point.x },
            Y = points.map { point ->  point.y},
            T = deltas,
            F = points.map { point -> stroke.getPenInfo(point)?.pressure ?: 0f },
            type = "stroke",
            id = brush.id
        )
    }

    val inkRoot = InkRoot(
        version = "3",
        type = "Drawing",
        id = "MainBlock",
        items = inkFormats
    )

    return Gson().toJson(inkRoot)
}

fun parseJson(json: String): List<Brush> {
    val inkRoot = Gson().fromJson(json, InkRoot::class.java)
    return inkRoot.items.map { item ->
        val initialTimestamp = stringToTimestamp(item.timestamp)
        val stroke = InputManager.ExtendedStroke()

        item.X.forEachIndexed { index, x ->
            val penInfo = InputManager.PenInfo(
                pointerType = InputManager.PointerType.PEN_TIP,
                x = x,
                y = item.Y[index],
                timestamp = initialTimestamp + item.T[index],
                pressure = item.F[index],
                orientation = 0f,
                tilt = 0f,
                primaryButtonState = false,
                secondaryButtonState = false
            )
            stroke.addPoint(penInfo)
        }

        Brush(id = item.id, stroke = stroke)
    }
}

private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"
private val simpleDateFormat = SimpleDateFormat(DATE_FORMAT, Locale.US)

fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    return simpleDateFormat.format(date)
}

fun stringToTimestamp(dateString: String): Long {
    val date = simpleDateFormat.parse(dateString)
    return date?.time ?: 0L
}