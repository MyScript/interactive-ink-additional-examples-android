// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.inksample.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.view.View
import com.myscript.iink.demo.ink.serialization.jiix.Word

class WordView(context: Context, private val word: Word) : View(context) {

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        textSize = 48f
    }

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        word.boundingBox?.let { box ->
            canvas.drawRect(
                box.x,
                box.y,
                box.x + box.width,
                box.y + box.height,
                boxPaint
            )
            word.label?.let { label ->
                canvas.drawText(
                    label,
                    box.x + 5,
                    box.y + box.height - textPaint.descent(),
                    textPaint
                )
            }
        }
    }
}