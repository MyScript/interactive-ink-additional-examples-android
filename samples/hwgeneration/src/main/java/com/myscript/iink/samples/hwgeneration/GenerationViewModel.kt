// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.samples.hwgeneration

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myscript.iink.Engine
import com.myscript.iink.HandwritingGenerator
import com.myscript.iink.HandwritingGeneratorError
import com.myscript.iink.HandwritingResult
import com.myscript.iink.IHandwritingGeneratorListener
import com.myscript.iink.MimeType
import com.myscript.iink.PointerEvent
import com.myscript.iink.PredefinedHandwritingProfileId
import com.myscript.iink.graphics.Transform
import com.myscript.iink.samples.hwgeneration.None.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class HWRResult(val word: String, val events: Array<PointerEvent>)
object None: HWRResult("", emptyArray())

class GenerationViewModel(private val engine: Engine) : ViewModel() {

    private val generator: HandwritingGenerator

    private val _isGenerating = MutableLiveData(false)
    val isGenerating: LiveData<Boolean>
        get() = _isGenerating

    private val _hwrResults = MutableLiveData<List<HWRResult>>()
    val hwrResults: LiveData<List<HWRResult>>
        get() = _hwrResults

    init {
        _isGenerating.value = false

        generator = engine.createHandwritingGenerator()
    }

    override fun onCleared() {
        super.onCleared()
    }

    private interface HWResultListener {
        fun onResult(result: HWRResult)
    }

    fun cancel() {
        generator.cancel()
        viewModelScope.launch(Dispatchers.Main) {
            _isGenerating.value = false
            _hwrResults.value = emptyList()
        }
    }

    fun generateHandwriting(inputSentence: String, profileId: PredefinedHandwritingProfileId, step: Float, inputTextSize: Float, inputX: Float, inputY: Float, width: Float, transform: Transform) {
        if (inputSentence.isEmpty()) return

        viewModelScope.launch(Dispatchers.Main) {
            _isGenerating.value = true

            withContext(Dispatchers.IO) {
                generate(inputSentence, profileId, step, inputTextSize, inputX, inputY, width, transform, object : HWResultListener {
                    override fun onResult(result: HWRResult) {
                        viewModelScope.launch(Dispatchers.Main) {
                            val previousList: MutableList<HWRResult> = _hwrResults.value?.toMutableList() ?: mutableListOf()
                            previousList.add(result)
                            _hwrResults.value = previousList
                        }
                    }
                })
            }
            _isGenerating.value = false
        }
    }

    private fun generate(sentence: String, profileId: PredefinedHandwritingProfileId, step: Float, textSize: Float, xOffset: Float, yOffset: Float, width: Float, transform: Transform, listener: HWResultListener): HWRResult {
        val builder = generator.createHandwritingProfileBuilder()
        val profile = builder.createFromId(profileId)

        val indexLock = Any()
        var currentPointerEventIndex = 0
        var wordIndex = 0

        val words = sentence.split(" ").filter { it.isNotEmpty() }

        generator.setListener(object: IHandwritingGeneratorListener {
            override fun onPartialResult(generator: HandwritingGenerator, result: HandwritingResult) {
                val allEvents = result.toPointerEvents(transform)
                val newEvents = allEvents.drop(synchronized(indexLock) { currentPointerEventIndex })

                listener.onResult(HWRResult(words[wordIndex], newEvents.toTypedArray()))

                synchronized(indexLock) {
                    currentPointerEventIndex += newEvents.size
                    wordIndex++
                }
            }
            override fun onEnd(generator: HandwritingGenerator) = Unit
            override fun onError(generator: HandwritingGenerator, code: HandwritingGeneratorError, message: String) = Unit
            override fun onUnsupportedCharacter(generator: HandwritingGenerator, label: String, character: String, index: Int) = Unit
        })

        generator.start("Text", profile, engine.createParameterSet().apply {
            setNumber("handwriting-generation.timestamp-ms", -1)
            setNumber("handwriting-generation.stroke-delta-timing-ms", 10.0)
            setNumber("handwriting-generation.sample-delta-timing-ms", 10.0)

            setBoolean("handwriting-generation.force-new-line", false)

            val invertTransform = Transform(transform).apply {
                invert()
            }
            val offsetMM = invertTransform.apply(xOffset, yOffset)
            val widthMM = invertTransform.apply(width, 0f)
            setNumber("handwriting-generation.width-mm", widthMM.x - offsetMM.x)
            setNumber("handwriting-generation.left-x-mm", offsetMM.x)
            setNumber("handwriting-generation.origin-y-mm", offsetMM.y)

            setNumber("handwriting-generation.line-gap-mm", textSize * LINE_GAP_RATIO)
            setNumber("handwriting-generation.text-scale", textSize)
            setNumber("handwriting-generation.step-count", step) // 25 default
        })

        words.forEach { word ->
            generator.add(word, MimeType.TEXT)
        }

        generator.end()
        generator.waitForIdle()

        val result = generator.getResult()
        val events = result.toPointerEvents(transform)

        return HWRResult(word, events)
    }

    companion object {
        private const val LINE_GAP_RATIO = 3
    }
}

class GenerationViewModelFactory(private val engine: Engine) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(GenerationViewModel::class.java) -> {
                GenerationViewModel(engine) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel $modelClass")
        }
    }
}
