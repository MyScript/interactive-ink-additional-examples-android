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
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.isVisible
import com.myscript.iink.demo.ink.InkView
import com.myscript.iink.demo.ink.InkView.Brush
import com.myscript.iink.demo.ink.InkView.DynamicPaintHandler
import com.myscript.iink.demo.ink.InputManager
import com.myscript.iink.offscreen.demo.R

class MainActivity : AppCompatActivity() {

    private lateinit var inkView: InkView
    private lateinit var webView: WebView
    private lateinit var fancySwitch: SwitchCompat
    private lateinit var seekBar: SeekBar
    private lateinit var circleView: ImageView

    private val inkViewModel: InkViewModel by viewModels { InkViewModel.Factory }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        inkView = findViewById(R.id.inkView)
        webView = findViewById(R.id.webView)
        fancySwitch = findViewById(R.id.fancySwitch)
        seekBar = findViewById(R.id.seekBar)
        circleView = findViewById(R.id.circleView)

        setupClickListeners()

        webView.webViewClient = WebViewClient()
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.cacheMode = WebSettings.LOAD_NO_CACHE

        webView.loadUrl("https://en.wikipedia.org/wiki/Special:Random")

        seekBar.setOnSeekBarChangeListener(
            object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    setStrokeWidth()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            },
        )

        setStrokeWidth()

        inkViewModel.strokes.observe(this, inkView::drawStrokes)
    }

    override fun onStart() {
        super.onStart()

        inkView.strokesListener = StrokesListener()
    }

    override fun onStop() {
        super.onStop()

        inkView.strokesListener = null
    }

    fun setStrokeWidth() {
        val layoutParams: ViewGroup.LayoutParams = circleView.layoutParams
        val factor = resources.displayMetrics.density
        layoutParams.width = (seekBar.progress * factor).toInt()
        layoutParams.height = (seekBar.progress * factor).toInt()
        circleView.layoutParams = layoutParams

        if (inkView.pressureEnabled) {
            inkView.strokeWidth = seekBar.progress / STROKE_MAX_MIN_RATIO
            inkView.strokeWidthMax = seekBar.progress.toFloat()
        } else {
            inkView.strokeWidth = seekBar.progress.toFloat()
            inkView.strokeWidthMax = seekBar.progress.toFloat()
        }
    }

    private fun setupClickListeners() {
        findViewById<Button>(R.id.btnClear)?.setOnClickListener(::clickClear)
        findViewById<Button>(R.id.btnSave)?.setOnClickListener(::saveInk)
        findViewById<Button>(R.id.btnLoad)?.setOnClickListener(::loadInk)
        fancySwitch.setOnClickListener(::fancySwitchChanged)
        findViewById<SwitchCompat>(R.id.webSwitch)?.setOnClickListener(::webSwitchChanged)
        findViewById<ImageView>(R.id.imageCopy)?.setOnClickListener(::copyImage)
    }

    private fun clickClear(@Suppress("UNUSED_PARAMETER") view: View) {
        inkViewModel.clearInk()
    }

    private fun copyImage(view: View) {
        val image = view as ImageView
        image.setImageBitmap(inkView.saveBitmap())
    }

    private fun saveInk(@Suppress("UNUSED_PARAMETER") view: View) {
        inkViewModel.saveInk()
    }

    private fun loadInk(@Suppress("UNUSED_PARAMETER") view: View) {
        inkViewModel.loadInk()
    }

    private fun fancySwitchChanged(view: View) {
        val switch = view as SwitchCompat
        if (switch.isChecked) {
            inkView.dynamicPaintHandler = FancyPaintHandler()
        } else {
            inkView.dynamicPaintHandler = null
        }
    }

    /**
     * Renders the ink with transparency linked to the pressure on the pen.
     */
    inner class FancyPaintHandler : DynamicPaintHandler {
        override fun generatePaintFromPenInfo(penInfo: InputManager.PenInfo): Paint {
            val paint = Paint()
            val alpha = penInfo.pressure * 255

            paint.color = Color.argb(
                alpha.toInt(),
                inkView.color.red,
                inkView.color.green,
                inkView.color.blue
            )
            paint.isAntiAlias = true
            // Set stroke width based on display density.
            paint.strokeWidth = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                penInfo.pressure * (inkView.strokeWidthMax - inkView.strokeWidth) + inkView.strokeWidth,
                resources.displayMetrics
            )
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeCap = Paint.Cap.ROUND

            return paint
        }
    }

    /**
     * Listen for strokes from InkView.InputManager
     */
    inner class StrokesListener: InkView.StrokesListener {
        override fun onStrokeAdded(brush: Brush) {
            inkViewModel.addStroke(brush)
        }
    }

    private fun webSwitchChanged(view: View) {
        val switch = view as SwitchCompat
        this.webView.isVisible = switch.isChecked
    }

    companion object {
        const val STROKE_MAX_MIN_RATIO = 10f
    }
}