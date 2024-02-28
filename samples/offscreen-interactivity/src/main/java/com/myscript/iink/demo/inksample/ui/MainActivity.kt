// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.inksample.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
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

        binding.iinkModelPreview.apply {
            webViewClient = WebViewClient()
            settings.apply {
                javaScriptEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
        }

        inkViewModel.displayMetrics = resources.displayMetrics

        // In the Microsoft surface Duo sample (which serves as basis for this demo), the code in InkView may require some time to prepare after a rotation.
        // If we do not account for this delay, the ViewModel may transmit the strokes prematurely,
        // at a time when the canvas is not yet available.
        binding.inkView.doOnLayout {
            // Be aware that calling drawStrokes in this context may not be optimal for performance,
            // as it triggers a complete redraw with each LiveData update.
            // While this method serves as a quick demonstration of how strokes are drawn, your application should be designed to handle this more efficiently
            inkViewModel.strokes.observe(this, binding.inkView::drawStrokes)
        }
        inkViewModel.recognitionFeedback.observe(this, ::onRecognitionUpdate)
        inkViewModel.iinkModel.observe(this, ::onIInkModelUpdate)
        inkViewModel.undoRedoState.observe(this, ::onUndoRedoStateUpdate)
    }

    override fun onStart() {
        super.onStart()

        with(binding) {
            inkView.strokesListener = StrokesListener()
            undoBtn.setOnClickListener { inkViewModel.undo() }
            redoBtn.setOnClickListener { inkViewModel.redo() }
            clearInkBtn.setOnClickListener { inkViewModel.clearInk() }
            recognitionSwitch.setOnCheckedChangeListener { _, isChecked ->
                inkViewModel.toggleRecognition(isVisible = isChecked)
            }
            iinkModelPreviewSwitch.setOnCheckedChangeListener { _, isChecked ->
                iinkModelPreviewLayout.isVisible = isChecked
            }
        }
    }

    override fun onStop() {
        inkViewModel.saveInk {
            // no op
        }
        with(binding) {
            inkView.strokesListener = null
            clearInkBtn.setOnClickListener(null)
            recognitionSwitch.setOnCheckedChangeListener(null)
        }

        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        inkViewModel.displayMetrics = null
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

    private fun onIInkModelUpdate(htmlExport: String) {
        binding.iinkModelPreview.loadData(htmlExport, "text/html", Charsets.UTF_8.toString())
    }

    private fun onUndoRedoStateUpdate(undoRedoState: UndoRedoState) {
        with(binding) {
            undoBtn.isEnabled = undoRedoState.canUndo
            redoBtn.isEnabled = undoRedoState.canRedo
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
}