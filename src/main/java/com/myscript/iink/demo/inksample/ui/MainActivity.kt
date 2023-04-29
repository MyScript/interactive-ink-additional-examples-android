/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License.
 */

package com.myscript.iink.demo.inksample.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.TypedValue
import android.webkit.WebSettings
import android.webkit.WebViewClient
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.isVisible
import com.microsoft.device.ink.InkView
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

        setStrokeWidth(binding.inkWidthSeekbar.progress)

        inkViewModel.strokes.observe(this, binding.inkView::drawStrokes)
    }

    override fun onStart() {
        super.onStart()

        with(binding) {
            inkView.strokesListener = StrokesListener()
            clearInkBtn.setOnClickListener { inkViewModel.clearInk() }
            saveInkBtn.setOnClickListener { inkViewModel.saveInk() }
            loadInkBtn.setOnClickListener { inkViewModel.loadInk() }
            inkPressureSwitch.setOnClickListener {
                inkView.dynamicPaintHandler = when {
                    (it as SwitchCompat).isChecked -> FancyPaintHandler()
                    else -> null
                }
            }

            webViewSwitch.setOnClickListener {
                webView.isVisible = (it as SwitchCompat).isChecked
            }

            inkWidthSeekbar.setOnSeekBarChangeListener(
                    object : OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                            setStrokeWidth(progress)
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                        override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                    }
            )
        }
    }

    override fun onStop() {
        with(binding) {
            inkView.strokesListener = null
            clearInkBtn.setOnClickListener(null)
            saveInkBtn.setOnClickListener(null)
            loadInkBtn.setOnClickListener(null)
            inkPressureSwitch.setOnClickListener(null)
            webViewSwitch.setOnClickListener(null)
            inkWidthSeekbar.setOnSeekBarChangeListener(null)
        }

        super.onStop()
    }

    private fun setStrokeWidth(ratio: Int) {
        with(binding.inkWidthFeedback) {
            layoutParams = layoutParams.apply {
                val density = resources.displayMetrics.density
                val inkWidth = (ratio * density).toInt()
                width = inkWidth
                height = inkWidth
            }
        }

        with(binding.inkView) {
            strokeWidth = when {
                pressureEnabled -> ratio / STROKE_MAX_MIN_RATIO
                else -> ratio.toFloat()
            }
            strokeWidthMax = ratio.toFloat()
        }
    }

    /**
     * Renders the ink with transparency linked to the pressure on the pen.
     */
    inner class FancyPaintHandler : InkView.DynamicPaintHandler {
        override fun generatePaintFromPenInfo(penInfo: InputManager.PenInfo): Paint {
            return Paint().apply {
                val alpha = penInfo.pressure * 255
                val inkViewColor = binding.inkView.color
                color = Color.argb(
                        alpha.toInt(),
                        inkViewColor.red,
                        inkViewColor.green,
                        inkViewColor.blue
                )
                isAntiAlias = true
                // Set stroke width based on display density.
                strokeWidth = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        penInfo.pressure * (binding.inkView.strokeWidthMax - binding.inkView.strokeWidth) + binding.inkView.strokeWidth,
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