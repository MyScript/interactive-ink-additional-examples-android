// Copyright @ MyScript. All rights reserved.
package com.myscript.iink.samples.keyboardinput

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import com.myscript.iink.ContentBlock
import com.myscript.iink.ContentPackage
import com.myscript.iink.ContentPart
import com.myscript.iink.ContentSelection
import com.myscript.iink.Editor
import com.myscript.iink.EditorError
import com.myscript.iink.Engine
import com.myscript.iink.GestureAction
import com.myscript.iink.IEditorListener
import com.myscript.iink.IGestureHandler
import com.myscript.iink.MimeType
import com.myscript.iink.NativeObjectHandle
import com.myscript.iink.PlaceholderController
import com.myscript.iink.PointerTool
import com.myscript.iink.graphics.Rectangle
import com.myscript.iink.samples.keyboardinput.databinding.ActivityMainBinding
import com.myscript.iink.uireferenceimplementation.EditorBinding
import com.myscript.iink.uireferenceimplementation.EditorData
import com.myscript.iink.uireferenceimplementation.EditorView
import com.myscript.iink.uireferenceimplementation.FontUtils
import com.myscript.iink.uireferenceimplementation.InputController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.UUID
import kotlin.math.roundToInt

private fun EditText.getTextWithLineBreaks() : String {
    return (0 until layout.lineCount).joinToString("") {
        val line = layout.text.subSequence(layout.getLineStart(it), layout.getLineVisibleEnd(it))
        if ((line.isEmpty() || (line.first() != '\n' && line.last() != '\n')) && it < layout.lineCount - 1) {
            "$line\n"
        } else {
            line
        }
    }
}

class MainActivity : AppCompatActivity() {
    private var engine: Engine? = null
    private var contentPackage: ContentPackage? = null
    private var contentPart: ContentPart? = null

    private var editorData: EditorData? = null
    private var editorView: EditorView? = null

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private var currentlyEditedView = -1
    private var currentlyEditedPlaceholder: String? = null

    private val editorListener = object : IEditorListener {
        override fun partChanging(editor: Editor, oldPart: ContentPart?, newPart: ContentPart?) = Unit
        override fun partChanged(editor: Editor) = Unit
        override fun contentChanged(editor: Editor, blockIds: Array<out String>?) {
            invalidateOptionsMenu()
        }
        override fun onError(editor: Editor, blockId: String, err: EditorError, message: String) = Unit
        override fun selectionChanged(editor: Editor) = Unit
        override fun activeBlockChanged(editor: Editor, blockId: String) = Unit
    }

    private val gestureHandler = object : IGestureHandler {
        override fun onTap(editor: Editor, tool: PointerTool?, gestureStrokeId: String, x: Float, y: Float): GestureAction {
            val rootBlock = editor.rootBlock ?: return GestureAction.APPLY_GESTURE
            val transform = editor.renderer.viewTransform ?: return GestureAction.APPLY_GESTURE
            transform.invert()
            val pointMM = transform.apply(x, y)
            val blockIds = getBlocksAt(editor, rootBlock, pointMM.x, pointMM.y)
            val handled = blockIds.isNotEmpty()
            lifecycleScope.launch(Dispatchers.Main) {
                blockIds.firstOrNull()?.let {
                    editor.getBlockById(it)?.let { imageBlock ->
                        val data = editor.placeholderController.getUserData(imageBlock)
                        editor.renderer.viewTransform?.let { transform ->
                            val topLeft = transform.apply(imageBlock.box.x, imageBlock.box.y)
                            editTextBlockAt(topLeft.x, topLeft.y, text = data)
                            editor.placeholderController.setVisible(imageBlock, false)
                            currentlyEditedPlaceholder = imageBlock.id
                        }
                    }
                }
            }
            return if (handled) GestureAction.IGNORE else GestureAction.APPLY_GESTURE
        }

        private fun getBlocksAt(editor: Editor, contentBlock: ContentBlock, x: Float, y: Float): List<String> {
            val blocksAtCoordinates = mutableListOf<String>()
            contentBlock.children.forEach {
                val box = it.box
                if (x >= box.x && x <= box.x + box.width && y >= box.y && y <= box.y + box.height && editor.placeholderController.isPlaceholder(it)) {
                    blocksAtCoordinates.add(it.id)
                }
                blocksAtCoordinates.addAll(getBlocksAt(editor, it, x, y))
            }
            return blocksAtCoordinates
        }

        override fun onDoubleTap(editor: Editor, tool: PointerTool?, gestureStrokeIds: Array<out String>?, x: Float, y: Float): GestureAction = GestureAction.APPLY_GESTURE

        override fun onLongPress(editor: Editor, tool: PointerTool?, gestureStrokeId: String, x: Float, y: Float): GestureAction {
            editor.pointerCancel(editorData?.inputController?.previousPointerId ?: 0)
            lifecycleScope.launch(Dispatchers.Main) {
                val fingerSizeInMM = 10f
                val fingerSizeInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, fingerSizeInMM, resources.displayMetrics)
                val correctedY = y - fingerSizeInPixels / 2
                editTextBlockAt(x = x, y = correctedY)
            }

            return GestureAction.IGNORE
        }

        override fun onUnderline(editor: Editor, tool: PointerTool?, gestureStrokeId: String, selection: NativeObjectHandle<ContentSelection>): GestureAction = GestureAction.APPLY_GESTURE

        override fun onSurround(editor: Editor, tool: PointerTool?, gestureStrokeId: String, selection: NativeObjectHandle<ContentSelection>): GestureAction = GestureAction.APPLY_GESTURE

        override fun onJoin(editor: Editor, tool: PointerTool?, gestureStrokeId: String, before: NativeObjectHandle<ContentSelection>, after: NativeObjectHandle<ContentSelection>): GestureAction = GestureAction.APPLY_GESTURE

        override fun onInsert(editor: Editor, tool: PointerTool?, gestureStrokeId: String, before: NativeObjectHandle<ContentSelection>, after: NativeObjectHandle<ContentSelection>): GestureAction = GestureAction.APPLY_GESTURE

        override fun onStrikethrough(editor: Editor, tool: PointerTool?, gestureStrokeId: String, selection: NativeObjectHandle<ContentSelection>): GestureAction = GestureAction.APPLY_GESTURE

        override fun onScratch(editor: Editor, tool: PointerTool?, gestureStrokeId: String, selection: NativeObjectHandle<ContentSelection>): GestureAction = GestureAction.APPLY_GESTURE
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        engine = IInkApplication.getEngine()

        if (engine == null) {
            // The certificate provided is incorrect, you need to use the one provided by MyScript
            AlertDialog.Builder(this)
                    .setTitle( getString(R.string.app_error_invalid_certificate_title))
                    .setMessage( getString(R.string.app_error_invalid_certificate_message))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            finishAndRemoveTask() // be sure to end the application
            return
        }

        // configure recognition
        engine?.configuration?.let { configuration ->
            val confDir = "zip://$packageCodePath!/assets/conf"
            configuration.setStringArray("configuration-manager.search-path", arrayOf(confDir))
            configuration.setString("content-package.temp-folder", File(filesDir, "tmp").absolutePath)

            configuration.setString("raw-content.line-pattern", "grid")
        }

        setContentView(binding.root)

        val typefaceMap = FontUtils.loadFontsFromAssets(applicationContext.assets)

        editorView = findViewById<EditorView?>(com.myscript.iink.uireferenceimplementation.R.id.editor_view).apply {
            setTypefaces(typefaceMap)
        }

        val editorBinding = EditorBinding(engine, typefaceMap)
        editorData = editorBinding.openEditor(editorView)

        val editor = requireNotNull(editorData?.editor)
        editor.setGestureHandler(gestureHandler)
        editor.addListener(editorListener)

        editorData?.inputController?.inputMode = InputController.INPUT_MODE_AUTO

        val file = File(filesDir, "file.iink")
        engine?.deletePackage(file)
        contentPackage = engine?.createPackage(file)
        contentPart = contentPackage?.createPart("Raw Content")

        supportActionBar?.title = getString(R.string.app_name)

        // wait for view size initialization before setting part
        editorView?.post {
            editorView?.let { editorView ->
                editorView.renderer?.let { renderer ->
                    renderer.setViewOffset(0f, 0f)
                    renderer.viewScale = 1f
                    editorView.visibility = View.VISIBLE
                    editor.part = contentPart
                }
            }
        }

        with(binding) {
            container.setOnTouchListener { _, event ->
                var handled = false
                if (currentlyEditedView > -1) {
                    findViewById<EditText>(currentlyEditedView)?.let { view ->
                        val area = Rect(view.x.roundToInt(), view.y.roundToInt(), view.width, view.height)
                        if (!area.contains(event.x.roundToInt(), event.y.roundToInt())) {
                            currentlyEditedView = -1
                            addEditTextAsImage(view, view.x, view.y)
                            handled = true
                        }
                    }
                }
                handled
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {

        menu?.findItem(R.id.editor_menu_undo)?.isEnabled = editorData?.editor?.canUndo() ?: false
        menu?.findItem(R.id.editor_menu_redo)?.isEnabled = editorData?.editor?.canRedo() ?: false
        menu?.findItem(R.id.editor_menu_clear)?.isEnabled = editorData?.editor?.isEmpty(null) == false

        menu?.findItem(R.id.editor_menu_save)?.isEnabled = editorData?.editor?.isEmpty(null) == false


        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.editor_menu_undo -> {
                editorData?.editor?.undo()
                true
            }
            R.id.editor_menu_redo -> {
                editorData?.editor?.redo()
                true
            }
            R.id.editor_menu_clear -> {
                if (currentlyEditedView > -1) {
                    currentlyEditedPlaceholder = null
                    val editText = findViewById<EditText>(currentlyEditedView)
                    findViewById<ViewGroup>(R.id.container).removeView(editText)
                }
                editorData?.editor?.clear()
                true
            }
            R.id.editor_menu_open -> {
                importRequest.launch("*/*")
                true
            }
            R.id.editor_menu_save -> {
                export()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        editorData?.editor?.let {
            it.renderer.close()
            it.removeListener(editorListener)
            it.setGestureHandler(null)
            it.close()
        }

        editorView?.let {
            it.setOnTouchListener(null)
            it.editor = null
        }

        contentPart?.close()
        contentPart = null

        contentPackage?.close()
        contentPackage = null

        // IInkApplication has the ownership, do not close here
        engine = null

        super.onDestroy()
    }

    private fun editTextBlockAt(x: Float, y: Float, width: Float = 0f, text: String = ""): EditText {
        val editText = EditText(this).apply {
            id = View.generateViewId()
        }

        editText.setText(text)
        editText.setSelection(text.length)
        addViewAt(editText, x, y, width)

        currentlyEditedView = editText.id
        displayKeyboard(editText)

        return editText
    }

    private fun addViewAt(view: View, x: Float, y: Float, width: Float = 0f, height: Float = 0f) {
        val container = findViewById<FrameLayout>(R.id.container)

        val viewScale = editorData?.editor?.renderer?.viewScale ?: 1f
        view.scaleX = viewScale
        view.scaleY = viewScale
        view.pivotX = 0f

        container.addView(view)

        view.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = x.roundToInt()
            topMargin = y.roundToInt()

            if (width > 0) {
                this.width = (width / view.scaleX).roundToInt()
            }
            if (height > 0) {
                this.height = (height / view.scaleY).roundToInt()
            }
        }

        // When typing, edittext grow bigger if unrestrained.
        // When zoomed, it won't respect screen border so we need to artificially set it.
        if (view is EditText && view.scaleX != 1f) {
            view.maxWidth = ((container.width - x) / view.scaleX).roundToInt()
        }
    }

    private fun addEditTextAsImage(editText: EditText, x: Float, y: Float) {
        hideKeyboard()

        // Before creating image to remove cursor
        editText.clearFocus()
        editText.inputType = editText.inputType or TYPE_TEXT_FLAG_NO_SUGGESTIONS
        editText.clearComposingText()

        // Remove background to remove underline
        editText.background = ResourcesCompat.getDrawable(resources, android.R.color.transparent, theme)

        // Add implicit line breaks to final text.
        addViewAsImage(editText, x, y, editText.getTextWithLineBreaks())
        val container = findViewById<ViewGroup>(R.id.container)
        container.postDelayed({
            container.removeView(editText)
        }, 50)
    }

    private fun addViewAsImage(view: View, x: Float, y: Float, data: String) {
        val editor = editorData?.editor ?: return
        val bitmap = bitmapFromView(view)
        val imageFile = fileFromBitmap(bitmap)

        val rectangle = Rectangle(x, y, bitmap.width.toFloat(), bitmap.height.toFloat())

        val placeholderId = currentlyEditedPlaceholder
        if (placeholderId != null) {
            editor.getBlockById(placeholderId)?.use { block ->
                editor.placeholderController.update(block, rectangle, imageFile.absolutePath, MimeType.PNG, data)
                editor.placeholderController.setVisible(block, true)
            }
            currentlyEditedPlaceholder = null
        } else {
            editor.placeholderController.add(rectangle, imageFile.absolutePath, MimeType.PNG, data, false, PlaceholderController.PlaceholderInteractivityOptions()).also(ContentBlock::close)
        }
    }

    private fun bitmapFromView(view: View): Bitmap {
        val returnedBitmap = Bitmap.createBitmap((view.width * view.scaleX).roundToInt(), (view.height * view.scaleY).roundToInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if (bgDrawable != null) {
            bgDrawable.draw(canvas)
        } else {
            canvas.drawColor(ResourcesCompat.getColor(resources, android.R.color.transparent, theme))
        }
        canvas.scale(view.scaleX, view.scaleY)
        view.draw(canvas)
        return returnedBitmap
    }

    private fun fileFromBitmap(bitmap: Bitmap, fileName: String = "export.png"): File {
        // create a file to write bitmap data
        val imageFile = File(cacheDir, fileName)
        imageFile.delete()
        imageFile.createNewFile()

        // Convert bitmap to byte array
        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 0 /*ignored for PNG*/, bos)

        // write the bytes in file
        val fos = FileOutputStream(imageFile)
        fos.write(bos.toByteArray())
        fos.flush()
        fos.close()

        return imageFile
    }

    private fun displayKeyboard(view: View) {
        view.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    // No need to force hide if using `adjustPan`
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
    }

    private fun export() {
        val exportedFile = File(cacheDir, EXPORTED_FILE_NAME)
        contentPackage?.saveAs(exportedFile)

        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, exportedFile.name)
        }

        if (Build.VERSION.SDK_INT >= VERSION_CODES.O) {
            exportLauncher.launch(i)
        } else {
            Toast.makeText(applicationContext, "Not able to save file on this Android version", Toast.LENGTH_LONG).show()
        }
    }

    @RequiresApi(VERSION_CODES.O)
    val exportLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            it.data?.data?.also { uri ->
                val contentResolver = applicationContext.contentResolver
                val inputFile = File(cacheDir, EXPORTED_FILE_NAME)
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    Files.copy(inputFile.toPath(), outputStream)
                }
            }
        }

    private val importRequest = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val mimeType = DocumentFile.fromSingleUri(this@MainActivity, uri)?.type ?: contentResolver.getType(uri)
        when (mimeType) {
            "binary/octet-stream",
            "application/zip",
            "application/octet-stream",
            "application/binary",
            "application/x-zip" -> lifecycle.coroutineScope.launch {
                processUriFile(uri, File(cacheDir, "${UUID.randomUUID()}.iink")) { file ->
                    contentPart?.close()
                    contentPackage?.close()

                    engine?.openPackage(file).use { packageFile ->
                        contentPackage = packageFile
                        contentPart = contentPackage?.getPart(0)
                        editorData?.editor?.part = contentPart
                        editorData?.renderer?.setViewOffset(0f, 0f)
                    }
                }
            }
            else -> Toast.makeText(applicationContext, "Not able to open file", Toast.LENGTH_LONG).show()

        }
    }

    private suspend fun Context.processUriFile(uri: Uri, file: File, logic: (File) -> Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    file.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        }
        try {
            logic(file)
        } finally {
            file.deleteOnExit()
        }
    }

    companion object {
        private const val EXPORTED_FILE_NAME = "export.iink"
    }
}