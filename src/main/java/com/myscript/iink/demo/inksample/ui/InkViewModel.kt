package com.myscript.iink.demo.inksample.ui

import android.util.DisplayMetrics
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.gson.Gson
import com.microsoft.device.ink.InkView
import com.myscript.iink.EditorError
import com.myscript.iink.Engine
import com.myscript.iink.OffscreenEditor
import com.myscript.iink.ContentPart
import com.myscript.iink.ItemIdHelper
import com.myscript.iink.IOffscreenEditorListener
import com.myscript.iink.MimeType
import com.myscript.iink.demo.ink.serialization.json
import com.myscript.iink.demo.ink.serialization.parseJson
import com.myscript.iink.demo.inksample.InkApplication
import com.myscript.iink.demo.inksample.data.InkRepository
import com.myscript.iink.demo.inksample.data.InkRepositoryImpl
import com.myscript.iink.demo.inksample.util.autoCloseable
import com.myscript.iink.demo.ink.serialization.jiix.RecognitionRoot
import com.myscript.iink.demo.ink.serialization.jiix.Word
import com.myscript.iink.demo.ink.serialization.jiix.toScreenCoordinates
import com.myscript.iink.demo.ink.serialization.jiix.toPointerEvents
import com.myscript.iink.demo.ink.serialization.jiix.convertPointerEvent
import com.myscript.iink.demo.inksample.util.DisplayMetricsConverter
import com.myscript.iink.demo.inksample.util.iinkExportConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.Exception

enum class ToolType {
    PEN, ERASER
}
data class ToolState(
    val type: ToolType,
    val isSelected: Boolean = false
)

data class RecognitionFeedback(
    val isVisible: Boolean = false,
    val words: List<Word> = emptyList()
)

class InkViewModel(
    private val repository: InkRepository,
    private val engine: Engine,
    private val dataDir: File,
    private val exportConfiguration: String
    ): ViewModel() {

    private val _strokes: MutableLiveData<List<InkView.Brush>> = MutableLiveData(listOf())
    val strokes: LiveData<List<InkView.Brush>>
        get() = _strokes

    private val _recognitionFeedback: MutableLiveData<RecognitionFeedback> = MutableLiveData(RecognitionFeedback())
    val recognitionContent: LiveData<RecognitionFeedback>
        get() = _recognitionFeedback

    private val _availableTools: MutableLiveData<List<ToolState>> = MutableLiveData(listOf(
        ToolState(type = ToolType.PEN, isSelected = true),
        ToolState(type = ToolType.ERASER, isSelected = false)
    ))
    val availableTools: LiveData<List<ToolState>>
        get() = _availableTools

    private var offscreenEditor by autoCloseable<OffscreenEditor>()
    private var currentPart by autoCloseable<ContentPart>()
    private var itemIdHelper by autoCloseable<ItemIdHelper>()

    private var converter: DisplayMetricsConverter? = null

    private val contentFile: File
        get() = File(dataDir, "OffscreenEditor.iink")

    var displayMetrics: DisplayMetrics? = null
        set(value) {
            field = value
            if (value != null) {
                converter = DisplayMetricsConverter(value)
            }
        }

    private val offScreenEditorListener: IOffscreenEditorListener = object : IOffscreenEditorListener {
        override fun partChanged(editor: OffscreenEditor) {
            // no-op
        }

        override fun contentChanged(editor: OffscreenEditor, blockIds: Array<out String>) {
            viewModelScope.launch(Dispatchers.Main) {
                val exportedData = withContext(Dispatchers.Default) {
                    offscreenEditor?.export_(emptyArray(), MimeType.JIIX)
                }
                val adjustedWords = if (exportedData == null) {
                    emptyList()
                } else {
                    val recognitionRoot = Gson().fromJson(exportedData, RecognitionRoot::class.java)

                    // filter out elements that are null or contains any null list of words
                    recognitionRoot.elements?.flatMap { element ->
                        element.words?.map { word ->
                            word.toScreenCoordinates(converter)
                        } ?: emptyList()
                    } ?: emptyList()
                }
                _recognitionFeedback.value = _recognitionFeedback.value?.copy(words = adjustedWords)
            }
        }

        override fun onError(editor: OffscreenEditor, blockId: String, err: EditorError, message: String) {
            Log.e(TAG, "IOffscreenEditorListener error (${err.name}): $message")
        }
    }

    init {
        dataDir.mkdirs()
        offscreenEditor = engine.createOffscreenEditor(1f, 1f)
        offscreenEditor?.let { editor ->
            itemIdHelper = engine.createItemIdHelper(editor)
            val configuration = editor.configuration
            configuration.inject(exportConfiguration)
            editor.addListener(offScreenEditorListener)
        }

        viewModelScope.launch(Dispatchers.Main) {
            currentPart = withContext(Dispatchers.IO) {
                try {
                    if (contentFile.exists()) {
                        engine.openPackage(contentFile).use { contentPackage ->
                            contentPackage.getPart(0)
                        }
                    } else {
                        engine.createPackage(contentFile).use { contentPackage ->
                            contentPackage.createPart("Raw Content")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error while retrieving ContentPart", e)
                    null
                }
            }
            offscreenEditor?.part = currentPart
        }
    }

    fun clearInk() {
        viewModelScope.launch(Dispatchers.Main) {
            _strokes.value = emptyList()
            offscreenEditor?.clear()
        }
    }

    fun loadInk() {
        viewModelScope.launch(Dispatchers.Main) {
            val jsonString = withContext(Dispatchers.IO) {
                repository.readInkFromFile()
            }
            jsonString?.let {
                val brushes = parseJson(jsonString)
                _strokes.value = brushes
            } ?: run {
                _strokes.value = listOf()
            }
        }
    }

    fun saveInk() {
        viewModelScope.launch(Dispatchers.Main) {
            withContext(Dispatchers.IO) {
                _strokes.value?.json()?.let { jsonString ->
                    repository.saveInkToFile(jsonString)
                }
                engine.openPackage(contentFile).use { contentPackage ->
                    contentPackage.save()
                }
            }
        }
    }

    fun selectTool(toolType: ToolType) {
        viewModelScope.launch(Dispatchers.Main) {
            _availableTools.value = _availableTools.value?.map {
                it.copy(isSelected = it.type == toolType)
            } ?: emptyList()
        }
    }

    fun toggleRecognition(isVisible: Boolean) {
        viewModelScope.launch(Dispatchers.Main) {
            _recognitionFeedback.value = _recognitionFeedback.value?.copy(isVisible = isVisible)
        }
    }

    fun addStroke(brush: InkView.Brush) {
        viewModelScope.launch(Dispatchers.Main) {
            _strokes.also { it.value = it.value?.plus(brush) }
            val pointerEvents = brush.stroke.toPointerEvents().map { pointerEvent ->
                pointerEvent.convertPointerEvent(converter)
            }.toTypedArray()

            offscreenEditor?.addStrokes(pointerEvents, false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentPart = null
        offscreenEditor = null
        itemIdHelper = null
    }

    companion object {
        private const val TAG = "InkViewModel"

        val Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                val engine = checkNotNull((application as InkApplication).engine)
                val dataDir = File(application.filesDir, "data")
                InkViewModel(InkRepositoryImpl(dataDir), engine, dataDir, iinkExportConfig)
            }
        }
    }
}