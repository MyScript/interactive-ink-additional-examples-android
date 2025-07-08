// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.inksample.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.TextPaint
import android.view.View

class RecognitionItemView(context: Context, private val item: RecognitionItem) : View(context) {

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 48f
    }

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val typePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 32f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
    }

    private val solvingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val solvingPath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        item.boundingBox?.let { box ->
            canvas.drawRect(
                box.x,
                box.y,
                box.x + box.width,
                box.y + box.height,
                boxPaint.apply {
                    color = when(item.type) {
                        BlockType.TEXT -> TEXT_COLOR
                        BlockType.MATH -> MATH_COLOR
                        BlockType.SOLVING -> SOLVER_COLOR
                    }
                }
            )

            if (item.text.isNotEmpty()) {
                canvas.drawText(
                    item.text,
                    box.x + 5,
                    box.y + box.height - textPaint.descent(),
                    textPaint
                )
            }

            canvas.drawText(
                when(item.type) {
                    BlockType.TEXT -> "abc"
                    BlockType.MATH -> "Σ"
                    BlockType.SOLVING -> "="
                },
                box.x + 5,
                box.y + (textPaint.descent() - textPaint.ascent()) / 2,
                typePaint.apply {
                    color = when(item.type) {
                        BlockType.TEXT -> TEXT_COLOR
                        BlockType.MATH -> MATH_COLOR
                        BlockType.SOLVING -> SOLVER_COLOR
                    }
                }
            )

            if (item.type == BlockType.SOLVING && item.strokes.isNotEmpty()) {
                item.strokes.forEach { stroke ->
                    if (stroke.points.isNotEmpty()) {
                        solvingPath.reset()
                        solvingPath.moveTo(stroke.points[0].x, stroke.points[0].y)
                        for (i in 1 until stroke.points.size) {
                            solvingPath.lineTo(stroke.points[i].x, stroke.points[i].y)
                        }
                        canvas.drawPath(solvingPath, solvingPaint)
                    }
                }
            }
        }
    }

    companion object {
        private const val TEXT_COLOR = Color.RED
        private const val MATH_COLOR = Color.BLUE
        private const val SOLVER_COLOR = Color.GREEN
    }
}