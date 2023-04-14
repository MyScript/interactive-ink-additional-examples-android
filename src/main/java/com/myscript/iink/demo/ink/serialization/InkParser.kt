package com.myscript.iink.demo.ink.serialization

import com.google.gson.Gson
import com.myscript.iink.demo.ink.InkView
import com.myscript.iink.demo.ink.InputManager

fun List<InkView.Brush>.json(): String {
    val inkFormats = mutableListOf<InkFormat>()

    map { brush ->
        val stroke = brush.stroke
        val points = stroke.getPoints()
        val item = InkFormat(
            timestamp = "-1",
            X = points.map { point -> point.x },
            Y = points.map { point ->  point.y},
            F = points.map { point -> stroke.getPenInfo(point)?.pressure ?: 0f },
            type = "stroke",
            id = brush.id
        )
        inkFormats.add(item)
    }

    val inkRoot = InkRoot(
        version = "3",
        type = "Drawing",
        id = "MainBlock",
        items = inkFormats
    )

    return Gson().toJson(inkRoot)
}

fun parseJson(json: String): List<InkView.Brush> {
    val inkRoot = Gson().fromJson(json, InkRoot::class.java)
    val brushes = mutableListOf<InkView.Brush>()

    inkRoot.items.map { item ->
        val stroke = InputManager.ExtendedStroke()
        for (i in 0 until item.X.size) {
            val penInfo = InputManager.PenInfo(
                pointerType = InputManager.PointerType.PEN_TIP,
                x = item.X[i],
                y = item.Y[i],
                pressure = item.F[i],
                orientation = 0f,
                tilt = 0f,
                primaryButtonState = false,
                secondaryButtonState = false
            )
            stroke.addPoint(penInfo)
        }
        val brush = InkView.Brush(
            id = item.id,
            stroke = stroke
        )
        brushes.add(brush)
    }

    return brushes
}