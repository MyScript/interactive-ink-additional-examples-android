/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License.
 */

package com.myscript.iink.demo.inksample.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.microsoft.device.ink.InkView
import com.myscript.iink.offscreen.demo.databinding.MainActivityBinding

class MainActivity : AppCompatActivity() {

    private val binding by lazy { MainActivityBinding.inflate(layoutInflater) }

    private val inkViewModel: InkViewModel by viewModels { InkViewModel.Factory }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

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
            saveInkBtn.setOnClickListener {
                inkViewModel.saveInk {
                    Toast.makeText(this@MainActivity, "Ink saved", Toast.LENGTH_SHORT).show()
                }
            }
            loadInkBtn.setOnClickListener { inkViewModel.loadInk() }

            penBtn.setOnClickListener {
                inkViewModel.selectTool(ToolType.PEN)
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
            penBtn.setOnClickListener(null)
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
            }
        }
    }

    private fun handlePen(tool: ToolState) {
        binding.penBtn.isSelected = tool.isSelected
        if (tool.isSelected) {
            binding.inkView.dynamicPaintHandler = null
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