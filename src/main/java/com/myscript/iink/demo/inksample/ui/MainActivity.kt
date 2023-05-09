/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License.
 */

package com.myscript.iink.demo.inksample.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebViewClient
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import com.microsoft.device.ink.InkView
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

        inkViewModel.strokes.observe(this, binding.inkView::drawStrokes)
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
        }
    }

    override fun onStop() {
        with(binding) {
            inkView.strokesListener = null
            clearInkBtn.setOnClickListener(null)
            saveInkBtn.setOnClickListener(null)
            loadInkBtn.setOnClickListener(null)
            webViewSwitch.setOnClickListener(null)
        }

        super.onStop()
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