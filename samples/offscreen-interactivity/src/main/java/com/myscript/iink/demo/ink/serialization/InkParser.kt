// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.ink.serialization

import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import com.google.gson.Gson
import com.google.ink.GoogleInkStroke
import com.google.ink.GoogleInkStrokeView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun List<GoogleInkStroke>.json(): String {
    val inkFormats = mapNotNull { googleInkStroke ->
        val X = mutableListOf<Float>()
        val Y = mutableListOf<Float>()
        val deltas = mutableListOf<Long>()
        val F = mutableListOf<Float>()

        for (index in 0 until googleInkStroke.stroke.inputs.size) {
            X.add(googleInkStroke.stroke.inputs[index].x)
            Y.add(googleInkStroke.stroke.inputs[index].y)
            deltas.add(googleInkStroke.stroke.inputs[index].elapsedTimeMillis - googleInkStroke.stroke.inputs[0].elapsedTimeMillis)
            F.add(0f)
        }

        InkFormat(
            timestamp = formatTimestamp(googleInkStroke.stroke.inputs[0].elapsedTimeMillis),
            X = X,
            Y = Y,
            T = deltas,
            F = F,
            type = "stroke",
            id = googleInkStroke.id
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

fun parseJson(json: String): List<GoogleInkStroke> {
    val inkRoot = Gson().fromJson(json, InkRoot::class.java)
    return inkRoot.items.map { item ->
        val initialTimestamp = stringToTimestamp(item.timestamp)

        val strokeInputBatch = MutableStrokeInputBatch()
        item.X.forEachIndexed { index, x ->
            strokeInputBatch.add(StrokeInput.create(x, item.Y[index],  initialTimestamp + item.T[index], pressure = item.F[index]))
        }

        GoogleInkStroke(id = item.id, stroke = Stroke(GoogleInkStrokeView.DEFAULT_BRUSH, strokeInputBatch.toImmutable()))
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