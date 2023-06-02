/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License.
 */

package com.myscript.iink.demo.inksample.ui

import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Bundle
import android.util.TypedValue
import android.webkit.WebSettings
import android.webkit.WebViewClient
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import com.microsoft.device.ink.InkView
import com.microsoft.device.ink.InkView.DynamicPaintHandler
import com.microsoft.device.ink.InputManager
import com.myscript.iink.offscreen.demo.databinding.MainActivityBinding

class MainActivity : AppCompatActivity() {

    private val binding by lazy { MainActivityBinding.inflate(layoutInflater) }

    private val inkViewModel: InkViewModel by viewModels { InkViewModel.Factory }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.webView.apply {
            webViewClient = WebViewClient()
            settings.apply {
                javaScriptEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            loadUrl("https://en.wikipedia.org/wiki/Special:Random")
        }

        inkViewModel.displayMetrics = resources.displayMetrics

        inkViewModel.strokes.observe(this, binding.inkView::drawStrokes)
        inkViewModel.availableTools.observe(this, ::onToolsChanged)
        inkViewModel.recognitionContent.observe(this, ::onRecognitionUpdate)
    }

    override fun onStart() {
        super.onStart()

        with(binding) {
            inkView.strokesListener = StrokesListener()
            clearInkBtn.setOnClickListener { inkViewModel.clearInk() }
            saveInkBtn.setOnClickListener { inkViewModel.saveInk() }
            loadInkBtn.setOnClickListener { inkViewModel.loadInk() }

            webViewSwitch.setOnClickListener {
                webView.isVisible = (it as SwitchCompat).isChecked
            }
            penBtn.setOnClickListener {
                inkViewModel.selectTool(ToolType.PEN)
            }
            eraserBtn.setOnClickListener {
                inkViewModel.selectTool(ToolType.ERASER)
            }
            recognitionSwitch.setOnCheckedChangeListener { _, isChecked ->
                inkViewModel.toggleRecognition(isVisible = isChecked)
            }
        }
    }

    override fun onStop() {
        with(binding) {
            inkView.strokesListener = null
            clearInkBtn.setOnClickListener(null)
            saveInkBtn.setOnClickListener(null)
            loadInkBtn.setOnClickListener(null)
            webViewSwitch.setOnClickListener(null)
            penBtn.setOnClickListener(null)
            eraserBtn.setOnClickListener(null)
            recognitionSwitch.setOnCheckedChangeListener(null)
        }

        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        inkViewModel.displayMetrics = null
    }

    private fun onToolsChanged(tools: List<ToolState>) {
        tools.forEach { tool ->
            when (tool.type) {
                ToolType.PEN -> handlePen(tool)
                ToolType.ERASER -> handleEraser(tool)
            }
        }
    }

    private fun handlePen(tool: ToolState) {
        binding.penBtn.isSelected = tool.isSelected
        if (tool.isSelected) {
            binding.inkView.dynamicPaintHandler = null
        }
    }

    private fun handleEraser(tool: ToolState) {
        binding.eraserBtn.isSelected = tool.isSelected
        if (tool.isSelected) {
            binding.inkView.dynamicPaintHandler = EraserPaintHandler()
        }
    }

    private fun onRecognitionUpdate(recognitionFeedback: RecognitionFeedback) {
        with(binding) {
            recognitionContent.removeAllViews()

            inkView.alpha = if (recognitionFeedback.isVisible) .25f else 1f

            if (recognitionFeedback.isVisible) {
                recognitionFeedback.words.forEach { word ->
                    val customView = WordView(this@MainActivity, word)
                    recognitionContent.addView(customView)
                }
            }
        }
    }

    inner class EraserPaintHandler : DynamicPaintHandler {
        override fun generatePaintFromPenInfo(penInfo: InputManager.PenInfo): Paint {
            val inkView = binding.inkView
            return Paint().apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

                isAntiAlias = true
                // Set stroke width based on display density.
                strokeWidth = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        penInfo.pressure * (inkView.strokeWidthMax - inkView.strokeWidth) + inkView.strokeWidth,
                        resources.displayMetrics
                )
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }
        }
    }

    /**
     * Listen for strokes from InkView.InputManager
     */
    inner class StrokesListener: InkView.StrokesListener {
        override fun onStrokeAdded(brush: InkView.Brush) {
            inkViewModel.addStroke(brush)
        }
    }

    companion object {
        const val STROKE_MAX_MIN_RATIO = 10f
    }
}