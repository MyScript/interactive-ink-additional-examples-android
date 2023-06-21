// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.inksample.ui

import android.util.DisplayMetrics
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.gson.Gson
import com.microsoft.device.ink.InkView
import com.myscript.iink.ContentPart
import com.myscript.iink.EditorError
import com.myscript.iink.Engine
import com.myscript.iink.IOffscreenEditorListener
import com.myscript.iink.IOffscreenGestureHandler
import com.myscript.iink.ItemIdCombinationModifier
import com.myscript.iink.ItemIdHelper
import com.myscript.iink.MimeType
import com.myscript.iink.OffscreenEditor
import com.myscript.iink.OffscreenGestureAction
import com.myscript.iink.PointerEvent
import com.myscript.iink.demo.ink.serialization.jiix.RecognitionRoot
import com.myscript.iink.demo.ink.serialization.jiix.Word
import com.myscript.iink.demo.ink.serialization.jiix.convertPointerEvent
import com.myscript.iink.demo.ink.serialization.jiix.toBrush
import com.myscript.iink.demo.ink.serialization.jiix.toPointerEvents
import com.myscript.iink.demo.ink.serialization.jiix.toScreenCoordinates
import com.myscript.iink.demo.ink.serialization.json
import com.myscript.iink.demo.ink.serialization.parseJson
import com.myscript.iink.demo.inksample.InkApplication
import com.myscript.iink.demo.inksample.data.InkRepository
import com.myscript.iink.demo.inksample.data.InkRepositoryImpl
import com.myscript.iink.demo.inksample.util.DisplayMetricsConverter
import com.myscript.iink.demo.inksample.util.autoCloseable
import com.myscript.iink.demo.inksample.util.iinkConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

enum class ToolType {
    PEN
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
    private val engine: Engine?,
    private val dataDir: File,
    private val exportConfiguration: String,
    private val workDispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    private val uiDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _strokes: MutableLiveData<List<InkView.Brush>> = MutableLiveData(emptyList())
    val strokes: LiveData<List<InkView.Brush>>
        get() = _strokes

    private val _recognitionFeedback: MutableLiveData<RecognitionFeedback> = MutableLiveData(RecognitionFeedback())
    val recognitionFeedback: LiveData<RecognitionFeedback>
        get() = _recognitionFeedback

    private val _iinkModel: MutableLiveData<String> = MutableLiveData(EMPTY_HTML)
    val iinkModel: LiveData<String>
        get() = _iinkModel

    private val _availableTools: MutableLiveData<List<ToolState>> = MutableLiveData(listOf(
        ToolState(type = ToolType.PEN, isSelected = true)
    ))
    val availableTools: LiveData<List<ToolState>>
        get() = _availableTools

    private var offscreenEditor by autoCloseable<OffscreenEditor>()
    private var currentPart by autoCloseable<ContentPart>()
    private var itemIdHelper by autoCloseable<ItemIdHelper>()

    private var converter: DisplayMetricsConverter? = null
    // maps iink offScreen editor's data model ids to app's data model ids
    private val strokeIdsMapping: MutableMap<String /*id of iink stroke*/, String /* id of app's stroke*/> = mutableMapOf()

    private val contentFile: File
        get() = File(dataDir, "OffscreenEditor.iink")

    var displayMetrics: DisplayMetrics? = null
        set(value) {
            field = value
            converter = if (value != null) {
                DisplayMetricsConverter(value)
            } else {
                null
            }
        }

    private var offScreenGestureHandler: IOffscreenGestureHandler? = object : IOffscreenGestureHandler {
        override fun onUnderline(
            editor: OffscreenEditor,
            gestureStrokeId: String,
            itemIds: Array<out String>
        ): OffscreenGestureAction {
            Log.d(TAG, "IOffscreenGestureHandler onUnderline gesture detected")
            return OffscreenGestureAction.ADD_STROKE
        }

        override fun onSurround(
            editor: OffscreenEditor,
            gestureStrokeId: String,
            itemIds: Array<out String>
        ): OffscreenGestureAction {
            Log.d(TAG, "IOffscreenGestureHandler onSurround gesture detected")
            return OffscreenGestureAction.ADD_STROKE
        }

        override fun onJoin(
            editor: OffscreenEditor,
            gestureStrokeId: String,
            before: Array<out String>,
            after: Array<out String>
        ): OffscreenGestureAction {
            Log.d(TAG, "IOffscreenGestureHandler onJoin gesture detected")
            return OffscreenGestureAction.ADD_STROKE
        }

        override fun onInsert(
            editor: OffscreenEditor,
            gestureStrokeId: String,
            before: Array<out String>,
            after: Array<out String>
        ): OffscreenGestureAction {
            Log.d(TAG, "IOffscreenGestureHandler onInsert gesture detected")
            return OffscreenGestureAction.ADD_STROKE
        }

        override fun onStrikethrough(
            editor: OffscreenEditor,
            gestureStrokeId: String,
            itemIds: Array<out String>
        ): OffscreenGestureAction {
            Log.d(TAG, "IOffscreenGestureHandler onStrikethrough gesture detected")
            val itemIdHelper = itemIdHelper ?: return OffscreenGestureAction.ADD_STROKE

            viewModelScope.launch(Dispatchers.Main) {
                val remainingStrokes = _strokes.value?.toMutableList() ?: mutableListOf()

                withContext(workDispatcher) {
                    val fullStrokeIds = itemIds.map(itemIdHelper::getFullItemId) + gestureStrokeId

                    fullStrokeIds.forEach { strokeId ->
                        val appStrokeId = strokeIdsMapping[strokeId]
                        strokeIdsMapping.remove(strokeId)
                        val strokeBrush = remainingStrokes.firstOrNull { it.id == appStrokeId }
                        remainingStrokes.remove(strokeBrush)
                    }

                    val strokeIdsToErase = (fullStrokeIds - gestureStrokeId).toTypedArray()
                    editor.erase(strokeIdsToErase)
                }
                _strokes.value = remainingStrokes
            }
            return OffscreenGestureAction.IGNORE
        }

        override fun onScratch(
            editor: OffscreenEditor,
            gestureStrokeId: String,
            itemIds: Array<out String>
        ): OffscreenGestureAction {
            val itemIdHelper = itemIdHelper ?: return OffscreenGestureAction.ADD_STROKE

            viewModelScope.launch(uiDispatcher) {
                val remainingStrokes = _strokes.value?.toMutableList() ?: mutableListOf()

                withContext(workDispatcher) {
                    // Retrieve the full stroke ids
                    val fullItemIds = itemIds.map { itemId ->
                        if (itemIdHelper.isPartialItem(itemId))
                            itemIdHelper.getFullItemId(itemId)
                        else
                            itemId
                    }.toTypedArray()

                    // Compute the difference between full strokes and erased partial strokes to get remaining item ids
                    val remainingItemIds = itemIdHelper.combine(fullItemIds, itemIds, ItemIdCombinationModifier.DIFFERENCE)

                    // Retrieve the points for remaining item ids
                    val remainingItemEvents = remainingItemIds.map { remainingItemId ->
                        itemIdHelper.getPointsForItemId(remainingItemId).toList()
                    }

                    // If remaining items exist, replace strokes, else erase strokes
                    val newItemIds = if (remainingItemEvents.isNotEmpty()) {
                        offscreenEditor?.replaceStrokes(
                            fullItemIds,
                            remainingItemEvents.flatten().toTypedArray()
                        )
                    } else {
                        offscreenEditor?.erase(fullItemIds)
                        emptyArray()
                    }

                    // Erase the erased strokes and gesture strokes in your application
                    (fullItemIds + gestureStrokeId).forEach { strokeId ->
                        val appStrokeId = strokeIdsMapping[strokeId]
                        strokeIdsMapping.remove(strokeId)
                        val strokeBrush = remainingStrokes.firstOrNull { it.id == appStrokeId }
                        remainingStrokes.remove(strokeBrush)
                    }

                    // Convert remaining events to brushes and map to remaining item ids
                    val brushes = remainingItemEvents.map { pointerEvents -> pointerEvents.toBrush(converter) }
                    newItemIds?.zip(brushes)?.forEach { (iinkStrokeId, brush) ->
                        strokeIdsMapping[iinkStrokeId] = brush.id
                    }

                    // Add back the remaining strokes
                    remainingStrokes.addAll(brushes)
                }

                _strokes.value = remainingStrokes
            }
            return OffscreenGestureAction.IGNORE
        }
    }

    private val offScreenEditorListener: IOffscreenEditorListener = object : IOffscreenEditorListener {
        override fun partChanged(editor: OffscreenEditor) {
            // no-op
        }

        override fun contentChanged(editor: OffscreenEditor, blockIds: Array<out String>) {
            viewModelScope.launch(uiDispatcher) {
                val htmlExport = withContext(defaultDispatcher) {
                    offscreenEditor?.export_(emptyArray(), MimeType.HTML)
                }

                _iinkModel.value = htmlExport ?: EMPTY_HTML

                val exportedData = withContext(defaultDispatcher) {
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
        offscreenEditor = engine?.createOffscreenEditor(1f, 1f)
        offscreenEditor?.let { editor ->
            itemIdHelper = engine?.createItemIdHelper(editor)
            val configuration = editor.configuration
            configuration.inject(exportConfiguration)
            editor.addListener(offScreenEditorListener)
            editor.setGestureHandler(offScreenGestureHandler)
        }

        viewModelScope.launch(uiDispatcher) {
            currentPart = withContext(ioDispatcher) {
                try {
                    if (contentFile.exists()) {
                        engine?.openPackage(contentFile).use { contentPackage ->
                            contentPackage?.getPart(0)
                        }
                    } else {
                        engine?.createPackage(contentFile).use { contentPackage ->
                            contentPackage?.createPart("Raw Content")
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
        viewModelScope.launch(uiDispatcher) {
            offscreenEditor?.clear()
            strokeIdsMapping.clear()
            _strokes.value = emptyList()
        }
    }

    fun loadInk() {
        viewModelScope.launch(uiDispatcher) {
            val jsonString = withContext(ioDispatcher) {
                repository.readInkFromFile()
            }

            _strokes.value = if (jsonString != null) {
                val brushes = parseJson(jsonString)
                offscreenEditor?.clear()
                strokeIdsMapping.clear()

                val pointerEvents = withContext(defaultDispatcher) {
                    brushes.flatMap { brush ->
                        brush.stroke.toPointerEvents().map { pointerEvent ->
                            pointerEvent.convertPointerEvent(converter)
                        }
                    }.toTypedArray()
                }

                if (brushes.isNotEmpty()) { // offscreenEditor will refuse an empty list and crash
                    val addedStrokes = offscreenEditor?.addStrokes(pointerEvents, false)
                    if (addedStrokes != null) {
                        brushes.forEachIndexed { index, brush ->
                            if (index in addedStrokes.indices) {
                                strokeIdsMapping[addedStrokes[index]] = brush.id
                            }
                        }
                    }
                }
                brushes
            } else {
                offscreenEditor?.clear()
                strokeIdsMapping.clear()
                emptyList()
            }
        }
    }

    fun saveInk(callback: () -> Unit) {
        viewModelScope.launch(uiDispatcher) {
            withContext(ioDispatcher) {
                _strokes.value?.json()?.let { jsonString ->
                    repository.saveInkToFile(jsonString)
                }
                engine?.openPackage(contentFile).use { contentPackage ->
                    contentPackage?.save()
                }
            }
            callback.invoke()
        }
    }

    fun selectTool(toolType: ToolType) {
        viewModelScope.launch(uiDispatcher) {
            _availableTools.value = _availableTools.value?.map {
                it.copy(isSelected = it.type == toolType)
            } ?: emptyList()
        }
    }

    fun toggleRecognition(isVisible: Boolean) {
        viewModelScope.launch(uiDispatcher) {
            _recognitionFeedback.value = _recognitionFeedback.value?.copy(isVisible = isVisible)
        }
    }

    fun addStroke(brush: InkView.Brush) {
        viewModelScope.launch(uiDispatcher) {
            _strokes.also { it.value = it.value?.plus(brush) }
            val pointerEvents = brush.stroke.toPointerEvents().map { pointerEvent ->
                pointerEvent.convertPointerEvent(converter)
            }.toTypedArray()

            offscreenEditor?.addStrokes(pointerEvents, true)?.firstNotNullOf { strokeId ->
                strokeIdsMapping[strokeId] = brush.id
            }
        }
    }

    @VisibleForTesting
    public override fun onCleared() {
        super.onCleared()
        currentPart = null
        offScreenGestureHandler = null
        offscreenEditor = null
        itemIdHelper = null
    }

    companion object {
        private const val TAG = "InkViewModel"
        private const val EMPTY_HTML = """<!DOCTYPE html><html>
<head>
  <title>iink model</title>
</head>
<body>
</body>
</html>"""

        val Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                val engine = checkNotNull((application as InkApplication).engine)
                val dataDir = File(application.filesDir, "data")
                InkViewModel(InkRepositoryImpl(dataDir), engine, dataDir, iinkConfig)
            }
        }
    }
}
