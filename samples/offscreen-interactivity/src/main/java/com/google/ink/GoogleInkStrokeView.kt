package com.google.ink

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import androidx.input.motionprediction.MotionEventPredictor
import com.myscript.iink.offscreen.demo.R

class CanvasView : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    interface DrawingListener {
        fun onDraw(canvas: Canvas)
    }

    var drawingListener: DrawingListener? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawingListener?.onDraw(canvas)
    }
}

class GoogleInkStrokeView : FrameLayout {

    interface StrokesListener {
        fun onStrokeAdded(stroke: GoogleInkStroke)
    }

    private var currentPointerId = 0
    private var currentStrokeId: InProgressStrokeId? = null
    private val brush = DEFAULT_BRUSH

    var strokesListener : StrokesListener? = null

    private lateinit var motionEventPredictor: MotionEventPredictor

    private val strokes = mutableListOf<GoogleInkStroke>()
    private val canvasStrokeRenderer = CanvasStrokeRenderer.create()
    private val canvasTransform = Matrix()

    private val canvas: CanvasView
        get() = findViewById(R.id.canvas)

    constructor(context: Context) : super(context) {
        init()
    }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun init() {
        inflate(context, R.layout.view_stroke, this)

        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        canvas.drawingListener = object : CanvasView.DrawingListener {
            override fun onDraw(canvas: Canvas) {
                for (stroke in strokes) {
                    canvasStrokeRenderer.draw(
                        stroke = stroke.stroke,
                        canvas = canvas,
                        strokeToScreenTransform = canvasTransform
                    )
                }
            }
        }

        val strokeView = findViewById<InProgressStrokesView>(R.id.strokeview)

        motionEventPredictor = MotionEventPredictor.newInstance(strokeView)

        strokeView.apply {
            addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
                override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                    val googleInStrokes = strokes.values.map { stroke ->  GoogleInkStroke(stroke = stroke) }
                    this@GoogleInkStrokeView.strokes.addAll(googleInStrokes)
                    canvas.invalidate()

                    removeFinishedStrokes(strokes.keys)

                    googleInStrokes.forEach { googleInkStroke ->
                        strokesListener?.onStrokeAdded(googleInkStroke)
                    }
                }
            })

            setOnTouchListener { view, event -> handleTouchEvent(view, event) }
        }
    }

    private fun handleTouchEvent(view: View, event: MotionEvent): Boolean {
        require(view is InProgressStrokesView)

        motionEventPredictor.record(event)
        val predictedEvent = motionEventPredictor.predict()
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.requestUnbufferedDispatch(event)
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                currentPointerId = pointerId
                currentStrokeId = view.startStroke(event = event, pointerId = pointerId, brush = brush)
                true
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerId = currentPointerId
                val strokeId = checkNotNull(currentStrokeId)

                for (pointerIndex in 0 until event.pointerCount) {
                    if (event.getPointerId(pointerIndex) != pointerId) continue
                    view.addToStroke(
                        event,
                        pointerId,
                        strokeId,
                        predictedEvent,
                    )
                }
                true
            }

            MotionEvent.ACTION_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                check(pointerId == currentPointerId)
                val strokeId = checkNotNull(currentStrokeId)
                view.finishStroke(event, pointerId, strokeId)
                view.performClick()
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                check(pointerId == currentPointerId)

                val strokeId = checkNotNull(currentStrokeId)
                view.cancelStroke(strokeId, event)
                true
            }

            else -> false
        }.also {
            predictedEvent?.recycle()
        }
    }

    fun drawStrokes(newStrokes: List<GoogleInkStroke>) {
        strokes.clear()
        strokes.addAll(newStrokes)
        canvas.invalidate()
    }

    companion object {
        val DEFAULT_BRUSH = Brush.createWithColorIntArgb(family = StockBrushes.marker(), colorIntArgb = Color.RED, size = 10f, epsilon = 0.1F)
    }
}