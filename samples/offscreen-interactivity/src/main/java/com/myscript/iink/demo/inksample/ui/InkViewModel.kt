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

data class RecognitionFeedback(
    val isVisible: Boolean = false,
    val words: List<Word> = emptyList()
)

enum class UndoRedoAction {
    ADD,
    REMOVE
}

data class UndoRedoItem(
        val undoRedoAction: UndoRedoAction,
        val strokes: List<InkView.Brush>
)

data class UndoRedoState(
        val canUndo: Boolean = false,
        val canRedo: Boolean = false
)

/**
 * ViewModel responsible for maintaining the state of the OffScreenInteractivity demo application.
 *
 * This ViewModel is designed to interact with the iink engine, manage offscreen editing, process ink gestures,
 * handle ink recognition, and keep the link & mapping between the application ink model and iink's model
 */
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

    private val undoRedoStack = mutableListOf<List<UndoRedoItem>>()
    private var undoRedoIndex = 0
    private val _undoRedoState: MutableLiveData<UndoRedoState> = MutableLiveData(UndoRedoState())
    val undoRedoState: LiveData<UndoRedoState>
        get() = _undoRedoState

    // The iinkModel and recognitionFeedback are straightforward methods for debugging and showcasing, providing visual representation for easier understanding.
    // While this is not the method your app should use to display recognition, it can provide a starting point or guide on how to accomplish this.
    private val _recognitionFeedback: MutableLiveData<RecognitionFeedback> = MutableLiveData(RecognitionFeedback())
    val recognitionFeedback: LiveData<RecognitionFeedback>
        get() = _recognitionFeedback

    private val _iinkModel: MutableLiveData<String> = MutableLiveData(EMPTY_HTML)
    val iinkModel: LiveData<String>
        get() = _iinkModel

    private var offscreenEditor by autoCloseable<OffscreenEditor>()
    private var currentPart by autoCloseable<ContentPart>()

    // Through the use of itemIdHelper and strokeIdsMapping, we aim to underscore the importance of maintaining a connection between your application's stroke model and the one used by iink.
    // ItemIdHelper facilitates the handling of item ids such as strokes & partial strokes that are associated with the offscreen editor.
    // If you wish to reflect updates from the editor's strokes in your application's strokes, maintaining a mapping will facilitate this process.
    private var itemIdHelper by autoCloseable<ItemIdHelper>()

    // This function converts stroke points from pixels, which are used in device coordinates,
    // to millimeters, which are used in offscreen editor coordinates, and it can perform this conversion in reverse as well.
    private var converter: DisplayMetricsConverter? = null
    // Maps the data model IDs of the iink offscreen editor to the data model IDs of the application.
    private val strokeIdsMapping: MutableMap<String /* id of iink stroke*/, String /* id of app's stroke*/> = mutableMapOf()

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
                undoRedoStack.removeAt(--undoRedoIndex)

                val remainingStrokes = _strokes.value?.toMutableList() ?: mutableListOf()

                // With workDispatcher, this snippet below will not be processed in parallel but rather one at a time.
                // This is especially useful when you want to ensure that tasks are executed in a specific order,
                // or when tasks have side-effects that must be isolated to a single thread.
                withContext(workDispatcher) {
                    // ItemIds may refer to partial strokes, retrieve the corresponding full strokes ids
                    val fullStrokeIds = itemIds.map(itemIdHelper::getFullItemId)

                    val strokesToRemove = mutableListOf<InkView.Brush>()
                    fullStrokeIds.forEach { strokeId ->
                        val appStrokeId = strokeIdsMapping[strokeId]
                        remainingStrokes.firstOrNull { it.id == appStrokeId }?.let { strokeBrush ->
                            strokesToRemove.add(strokeBrush)
                        }
                    }
                    addToUndoRedoStack(UndoRedoAction.REMOVE, strokesToRemove)

                    // Erase the gesture stroke (gestureStrokeId) and the erased strokes (fullItemIds) in your application
                    (fullStrokeIds + gestureStrokeId).forEach { strokeId ->
                        val appStrokeId = strokeIdsMapping[strokeId]
                        strokeIdsMapping.remove(strokeId)
                        val strokeBrush = remainingStrokes.firstOrNull { it.id == appStrokeId }
                        remainingStrokes.remove(strokeBrush)
                    }

                    // Erase the scratched strokes (fullItemIds) in offscreen editor
                    val strokeIdsToErase = (fullStrokeIds - gestureStrokeId).toTypedArray()
                    editor.erase(strokeIdsToErase)
                }
                _strokes.value = remainingStrokes
            }
            // Discard the gesture stroke (gestureStrokeId) in offscreen editor
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
                    undoRedoStack.removeAt(--undoRedoIndex)

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

                    val strokesToRemove = mutableListOf<InkView.Brush>()
                    fullItemIds.forEach { strokeId ->
                        val appStrokeId = strokeIdsMapping[strokeId]
                        remainingStrokes.firstOrNull { it.id == appStrokeId }?.let {
                            strokesToRemove.add(it)
                        }
                    }
                    addToUndoRedoStack(UndoRedoAction.REMOVE, strokesToRemove)

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

        // This method is triggered when the content in the editor changes.
        // It could be due to new strokes added, existing strokes updated or deleted etc.
        // The blockIds parameter contains the ids of the blocks that were changed.
        override fun contentChanged(editor: OffscreenEditor, blockIds: Array<out String>) {
            viewModelScope.launch(uiDispatcher) {
                val htmlExport = withContext(defaultDispatcher) {
                    offscreenEditor?.export_(emptyArray(), MimeType.HTML)
                }

                _iinkModel.value = htmlExport ?: EMPTY_HTML

                // Export the content as JIIX (JSON format that describes the recognition result).
                // The computation is offloaded to a CPU bounded dispatcher as it may be a potentially long-running operation.
                val exportedData = withContext(defaultDispatcher) {
                    offscreenEditor?.export_(emptyArray(), MimeType.JIIX)
                }
                // Parse the exported JIIX content and map it to a list of words that are displayed on the screen.
                // If the exportedData is null, an empty list is used instead.
                val adjustedWords = if (exportedData == null) {
                    emptyList()
                } else {
                    val recognitionRoot = Gson().fromJson(exportedData, RecognitionRoot::class.java)

                    // Filter out elements that are null or contain any null list of words
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
            // Enable text and gestures recognition
            val configuration = editor.configuration
            configuration.inject(exportConfiguration)
            editor.addListener(offScreenEditorListener)
            editor.setGestureHandler(offScreenGestureHandler)
        }

        // Create a package with a new part
        viewModelScope.launch(uiDispatcher) {
            currentPart = withContext(ioDispatcher) {
                try {
                    if (contentFile.exists()) {
                        loadInk()
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

    private fun addToUndoRedoStack(action: UndoRedoAction, strokes: List<InkView.Brush>) {
        addToUndoRedoStack(listOf(UndoRedoItem(action, strokes)))
    }

    private fun addToUndoRedoStack(undoRedoItems: List<UndoRedoItem>) {
        viewModelScope.launch(uiDispatcher) {
            if (undoRedoStack.isNotEmpty()) {
                for (i in (undoRedoStack.size - 1).downTo(undoRedoIndex)) {
                    undoRedoStack.removeAt(i)
                }
            }
            undoRedoStack.add(undoRedoIndex++, undoRedoItems)

            _undoRedoState.value = UndoRedoState(
                canUndo = undoRedoIndex > 0,
                canRedo = undoRedoIndex < undoRedoStack.size
            )
        }
    }

    private fun addStrokesForUndoRedo(strokesToAdd: List<InkView.Brush>) {
        viewModelScope.launch(uiDispatcher) {
            val strokes = _strokes.value?.toMutableList() ?: mutableListOf()
            strokes.addAll(strokesToAdd)

            val pointerEvents = strokesToAdd.flatMap { brush ->
                brush.stroke.toPointerEvents().map { pointerEvent ->
                    pointerEvent.convertPointerEvent(converter)
                }
            }.toTypedArray()

            if (pointerEvents.isNotEmpty()) {
                val addedStrokes = offscreenEditor?.addStrokes(pointerEvents, false)

                if (addedStrokes != null) {
                    strokesToAdd.forEachIndexed { index, brush ->
                        if (index in addedStrokes.indices) {
                            strokeIdsMapping[addedStrokes[index]] = brush.id
                        }
                    }
                }
            }
            _strokes.value = strokes
        }
    }

    private fun removeStrokesForUndoRedo(strokesToRemove: List<InkView.Brush>) {
        viewModelScope.launch(uiDispatcher) {
            val strokes = _strokes.value?.toMutableList() ?: mutableListOf()
            if (strokes.isEmpty()) return@launch

            val updatedStrokes = strokes.filter {
                it.id !in strokesToRemove.map { strokeToRemove -> strokeToRemove.id }
            }

            val strokeToUndoMapping = strokeIdsMapping.filter { (_, appStrokeId) ->
                appStrokeId in strokesToRemove.map { strokeToRemove -> strokeToRemove.id }
            }
            offscreenEditor?.erase(strokeToUndoMapping.keys.toTypedArray())
            strokeToUndoMapping.forEach {
                strokeIdsMapping.remove(it.key)
            }

            _strokes.value = updatedStrokes
        }
    }

    private fun clearUndoRedoStack() {
        viewModelScope.launch(uiDispatcher) {
            undoRedoStack.clear()
            undoRedoIndex = 0
            _undoRedoState.value = UndoRedoState()
        }
    }

    fun undo() {
        viewModelScope.launch(uiDispatcher) {
            if (undoRedoIndex == 0 || undoRedoStack.isEmpty()) return@launch

            val undoItems = undoRedoStack[--undoRedoIndex]
            undoItems.forEach { item ->
                when (item.undoRedoAction) {
                    UndoRedoAction.ADD -> removeStrokesForUndoRedo(item.strokes)
                    UndoRedoAction.REMOVE -> addStrokesForUndoRedo(item.strokes)
                }
            }

            _undoRedoState.value = UndoRedoState(
                    canUndo = undoRedoIndex > 0,
                    canRedo = undoRedoIndex < undoRedoStack.size
            )
        }
    }

    fun redo() {
        viewModelScope.launch(uiDispatcher) {
            if (undoRedoIndex == undoRedoStack.size || undoRedoStack.isEmpty()) return@launch

            val redoItems = undoRedoStack[undoRedoIndex++]
            redoItems.forEach { item ->
                when (item.undoRedoAction) {
                    UndoRedoAction.ADD -> addStrokesForUndoRedo(item.strokes)
                    UndoRedoAction.REMOVE -> removeStrokesForUndoRedo(item.strokes)
                }
            }

            _undoRedoState.value = UndoRedoState(
                    canUndo = undoRedoIndex > 0,
                    canRedo = undoRedoIndex < undoRedoStack.size
            )
        }
    }

    fun clearInk() {
        viewModelScope.launch(uiDispatcher) {
            offscreenEditor?.clear()
            strokeIdsMapping.clear()

            addToUndoRedoStack(UndoRedoAction.REMOVE, _strokes.value ?: emptyList())

            _strokes.value = emptyList()
        }
    }

    fun loadInk() {
        clearUndoRedoStack()

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

                // OffscreenEditor requires a non empty array
                if (pointerEvents.isNotEmpty()) {
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

            addToUndoRedoStack(UndoRedoAction.ADD, listOf(brush))
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
