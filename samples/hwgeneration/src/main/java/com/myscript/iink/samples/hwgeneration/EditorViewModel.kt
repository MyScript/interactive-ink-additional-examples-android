// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.samples.hwgeneration

import android.app.Application
import android.view.View
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myscript.iink.ContentPackage
import com.myscript.iink.ContentPart
import com.myscript.iink.ContentSelection
import com.myscript.iink.Editor
import com.myscript.iink.EditorError
import com.myscript.iink.Engine
import com.myscript.iink.GestureAction
import com.myscript.iink.IEditorListener
import com.myscript.iink.IGestureHandler
import com.myscript.iink.NativeObjectHandle
import com.myscript.iink.PointerEvent
import com.myscript.iink.PointerEventType
import com.myscript.iink.PointerTool
import com.myscript.iink.PointerType
import com.myscript.iink.graphics.Transform
import com.myscript.iink.uireferenceimplementation.EditorBinding
import com.myscript.iink.uireferenceimplementation.EditorView
import com.myscript.iink.uireferenceimplementation.FontUtils
import com.myscript.iink.uireferenceimplementation.InputController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.Reader

data class PartHistoryState(val canUndo: Boolean = false, val canRedo: Boolean = false)

data class OnLongPress(val show: Boolean = false, val x: Float = 0f, val y: Float = 0f)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _partHistoryState = MutableLiveData(PartHistoryState())
    val partHistoryState: LiveData<PartHistoryState>
        get() = _partHistoryState

    private val _isWriting = MutableLiveData(false)
    val isWriting: LiveData<Boolean>
        get() = _isWriting

    private val _onLongPress = MutableLiveData(OnLongPress())
    val onLongPress: LiveData<OnLongPress>
        get() = _onLongPress

    private val _isSelectionMode = MutableLiveData(false)
    val isSelectionMode: LiveData<Boolean>
        get() = _isSelectionMode

    private val _selection = MutableLiveData<ContentSelection>(null)
    val selection: LiveData<ContentSelection>
        get() = _selection

    private val editorListener: IEditorListener = object : IEditorListener {
        private fun notifyUndoRedo(editor: Editor) {
            val canUndo = editor.canUndo()
            val canRedo = editor.canRedo()
            viewModelScope.launch(Dispatchers.Main) {
                _partHistoryState.value = PartHistoryState(canUndo, canRedo)
            }
        }

        override fun partChanging(editor: Editor, oldPart: ContentPart?, newPart: ContentPart?) = Unit
        override fun partChanged(editor: Editor) {
            notifyUndoRedo(editor)
        }

        override fun contentChanged(editor: Editor, blockIds: Array<out String>) {
            notifyUndoRedo(editor)
        }

        override fun onError(editor: Editor, blockId: String, err: EditorError, message: String) = Unit

        override fun selectionChanged(editor: Editor) {
            viewModelScope.launch(Dispatchers.Main) {
                _selection.value = editor.selection
            }
        }

        override fun activeBlockChanged(editor: Editor, blockId: String) = Unit
    }

    private val gestureHandler = object : IGestureHandler {
        override fun onTap(editor: Editor, tool: PointerTool?, gestureStrokeId: String, x: Float, y: Float): GestureAction = GestureAction.APPLY_GESTURE
        override fun onDoubleTap(editor: Editor, tool: PointerTool?, gestureStrokeIds: Array<out String>, x: Float, y: Float): GestureAction = GestureAction.APPLY_GESTURE

        override fun onLongPress(editor: Editor, tool: PointerTool?, gestureStrokeId: String, x: Float, y: Float): GestureAction {
            viewModelScope.launch(Dispatchers.Main) {
                _onLongPress.value = OnLongPress(true, x, y)
            }
            return GestureAction.APPLY_GESTURE
        }

        override fun onUnderline(editor: Editor, tool: PointerTool?, gestureStrokeId: String, selection: NativeObjectHandle<ContentSelection>): GestureAction = GestureAction.APPLY_GESTURE
        override fun onSurround(editor: Editor, tool: PointerTool?, gestureStrokeId: String, selection: NativeObjectHandle<ContentSelection>): GestureAction = GestureAction.APPLY_GESTURE
        override fun onJoin(editor: Editor, tool: PointerTool?, gestureStrokeId: String, before: NativeObjectHandle<ContentSelection>, after: NativeObjectHandle<ContentSelection>): GestureAction = GestureAction.APPLY_GESTURE
        override fun onInsert(editor: Editor, tool: PointerTool?, gestureStrokeId: String, before: NativeObjectHandle<ContentSelection>, after: NativeObjectHandle<ContentSelection>): GestureAction = GestureAction.APPLY_GESTURE
        override fun onStrikethrough(editor: Editor, tool: PointerTool?, gestureStrokeId: String, selection: NativeObjectHandle<ContentSelection>): GestureAction = GestureAction.APPLY_GESTURE
        override fun onScratch(editor: Editor, tool: PointerTool?, gestureStrokeId: String, selection: NativeObjectHandle<ContentSelection>): GestureAction = GestureAction.APPLY_GESTURE
    }

    private var contentPackage: ContentPackage? = null
    private var contentPart: ContentPart? = null
    private var editor: Editor? = null

    fun openEditor(engine: Engine, editorView: EditorView) {
        val context = editorView.context.applicationContext

        val typefaceMap = FontUtils.loadFontsFromAssets(context.assets)
        editorView.setTypefaces(typefaceMap)

        val editorBinding = EditorBinding(engine, typefaceMap)
        val editorData = editorBinding.openEditor(editorView)

        val editor = requireNotNull(editorData.editor)
        this.editor = editor

        editorData.inputController?.inputMode = InputController.INPUT_MODE_AUTO

        val file = File(context.filesDir, "file.iink")

        contentPackage = try {
            engine.createPackage(file)
        } catch (e: IOException) {
            engine.openPackage(file)
        }
        contentPart = if (contentPackage?.partCount == 0) {
            contentPackage?.createPart("Raw Content")
        } else {
            contentPackage?.getPart(0)
        }

        editor.addListener(editorListener)

        // wait for view size initialization before setting part
        editorView.post {
            editorView.let { editorView ->
                editorView.renderer?.let { renderer ->
                    renderer.setViewOffset(0f, 0f)
                    renderer.viewScale = 1f
                    editorView.visibility = View.VISIBLE

                    val configuration = context.assets.open("parts/interactivity.json").bufferedReader().use(Reader::readText)
                    editor.configuration.inject(configuration)
                    editor.part = contentPart
                    editor.setGestureHandler(gestureHandler)

                    setSelectionMode(false)
                }
            }
        }
    }

    fun closeEditor() {
        contentPackage?.save()

        editor?.let {
            it.renderer.close()
            it.setGestureHandler(null)
            it.removeListener(editorListener)
            it.close()
        }

        contentPart?.close()
        contentPackage?.close()

        reset()
    }

    private fun reset() {
        synchronized(toWrite) {
            toWrite.clear()
        }
        viewModelScope.launch(Dispatchers.Main) {
            _isWriting.value = false
        }
    }

    override fun onCleared() {
        closeEditor()
        super.onCleared()
    }

    fun undo() {
        editor?.undo()
    }

    fun redo() {
        editor?.redo()
    }

    fun clear() {
        editor?.clear()
    }

    fun setSelectionMode(enabled: Boolean) {
        if (enabled) {
            editor?.toolController?.setToolForType(PointerType.PEN, PointerTool.SELECTOR)
        } else {
            editor?.toolController?.setToolForType(PointerType.PEN, PointerTool.PEN)
            editor?.setSelection(null)
        }
        viewModelScope.launch(Dispatchers.Main) {
            _isSelectionMode.value = enabled
        }
    }

    fun isEmpty(): Boolean {
        return editor?.isEmpty(null) == true
    }

    fun transform(): Transform {
        return editor?.renderer?.viewTransform ?: Transform()
    }

    private val toWrite = mutableListOf<Array<PointerEvent>>()

    fun write(eventsToStack: Array<PointerEvent>) {
        val editor = requireNotNull(editor)

        synchronized(toWrite) {
            toWrite.add(eventsToStack)
        }

        if (_isWriting.value == true) return

        viewModelScope.launch(Dispatchers.Default) {
            val previousGestureConf = editor.configuration.getBoolean("gesture.enable")
            editor.configuration.setBoolean("gesture.enable", false)

            setSelectionMode(false)

            withContext(Dispatchers.Main) {
                _isWriting.value = true
            }

            while(synchronized(toWrite) { toWrite.isNotEmpty() }) {
                val events = synchronized(toWrite) {
                    toWrite.removeAt(0)
                }
                try {
                    events.forEachIndexed { index, pointerEvent ->
                        withContext(Dispatchers.Main) {
                            when (pointerEvent.eventType) {
                                PointerEventType.DOWN -> {
                                    editor.pointerDown(pointerEvent.x, pointerEvent.y, pointerEvent.t, pointerEvent.f, pointerEvent.pointerType, pointerEvent.pointerId)
                                }
                                PointerEventType.MOVE -> {
                                    editor.pointerMove(pointerEvent.x, pointerEvent.y, pointerEvent.t, pointerEvent.f, pointerEvent.pointerType, pointerEvent.pointerId)
                                }
                                PointerEventType.UP -> {
                                    editor.pointerUp(pointerEvent.x, pointerEvent.y, pointerEvent.t, pointerEvent.f, pointerEvent.pointerType, pointerEvent.pointerId)
                                }
                                PointerEventType.CANCEL -> {
                                    editor.pointerCancel(pointerEvent.pointerId)
                                }
                            }
                        }

                        val delay = if (events.size < index + 1) {
                            events[index + 1].t - pointerEvent.t
                        } else 10
                        Thread.sleep(delay)
                    }
                } catch (e: IllegalStateException) {
                    reset()
                    return@launch
                }
            }

            editor.configuration.setBoolean("gesture.enable", previousGestureConf)

            withContext(Dispatchers.Main) {
                _isWriting.value = false
            }
        }
    }

    fun saveAs(file: File) {
        contentPackage?.saveAs(file)
    }

    fun onLongPressConsummed() {
        _onLongPress.value = OnLongPress()
    }
}

class EditorViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(EditorViewModel::class.java) -> {
                EditorViewModel(application) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel $modelClass")
        }
    }
}
