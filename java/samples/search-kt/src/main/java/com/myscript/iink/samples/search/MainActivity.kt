package com.myscript.iink.samples.search

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.myscript.iink.*
import com.myscript.iink.app.common.activities.ErrorActivity
import com.myscript.iink.uireferenceimplementation.*
import java.io.File


class MainActivity : AppCompatActivity(), View.OnClickListener {
    private val TAG = "MainActivity"
    private val IINK_PACKAGE_NAME = "my_search_iink_package"

    private var contentPackage : ContentPackage? = null
    private var editorData: EditorData? = null
    private var editorView: EditorView? = null
    private var searchView: SearchView? = null

    //create custom listener for editorView
    private val editorListener = object : IEditorListener {
        override fun partChanging(editor: Editor, oldPart: ContentPart?, newPart: ContentPart?) {
            //no op
        }

        override fun partChanged(editor: Editor) {
            invalidateOptionsMenu()
            invalidateIconButtons()
        }

        override fun contentChanged(editor: Editor, blockIds: Array<out String>) {
            invalidateOptionsMenu()
            invalidateIconButtons()
            doSearch()
        }

        override fun onError(editor: Editor, blockId: String, error: EditorError, message: String) {
            if (editorView == null) {
                Log.e(
                    TAG,
                    "Failed to edit block \"$blockId\"$message"
                )
                return
            }
            editorView!!.post {
                Toast.makeText(
                    editorView!!.context,
                    "$blockId:${error.name}:$message",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        override fun selectionChanged(editor: Editor) {
            // no-op
        }

        override fun activeBlockChanged(editor: Editor, blockId: String) {
            // no-op
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        ErrorActivity.setExceptionHandler(applicationContext)

        // Note: could be managed by domain layer and handled through observable error channel
        // but kept simple as is to avoid adding too much complexity for this special (unrecoverable) error case
        if (MyIInkApplication.getEngine() == null) {
            /* the certificate provided in `BatchModule.provideEngine` is most likely incorrect */
            AlertDialog.Builder(this)
                .setTitle( getString(com.myscript.iink.app.common.R.string.app_error_invalid_certificate_title))
                .setMessage( getString(com.myscript.iink.app.common.R.string.app_error_invalid_certificate_message))
                .setPositiveButton(com.myscript.iink.app.common.R.string.dialog_ok
                ) { _,
                    _ ->
                    finishAffinity()
                    finishAndRemoveTask() // be sure to end the application
                }
                .show()
            return
        }

        // we can also intialize engine here
        MyIInkApplication.getEngine()?.apply {
            // configure recognition
            configuration.let { conf ->
                val confDir = "zip://${packageCodePath}!/assets/conf"
                conf.setStringArray("configuration-manager.search-path", arrayOf(confDir))
                val tempDir = File(cacheDir, "tmp")
                conf.setString("content-package.temp-folder", tempDir.absolutePath)

                // To enable text recognition for a specific language,
                //conf.setString("lang", language);
                conf.setBoolean("export.jiix.text.chars", true) // for partial word searching
            }
        }

        editorView = findViewById<View>(R.id.back_frame).findViewById(com.myscript.iink.uireferenceimplementation.R.id.editor_view)
        searchView = findViewById<SearchView>(R.id.search_view)
        searchView?.visibility = View.INVISIBLE


        // Create a new package
        contentPackage = MyIInkApplication.getEngine()?.createPackage(IINK_PACKAGE_NAME)
        if(contentPackage==null){
            return
        }

        val editorBinding = EditorBinding(MyIInkApplication.getEngine(),
            FontUtils.loadFontsFromAssets(application.assets) ?: emptyMap())

        editorData = editorBinding.openEditor(editorView)
        editorData?.inputController?.setViewListener(editorView)

        /* value to test
        InputController.INPUT_MODE_FORCE_PEN
        InputController.INPUT_MODE_FORCE_TOUCH
        InputController.INPUT_MODE_ERASER
        InputController.INPUT_MODE_AUTO
        InputController.INPUT_MODE_NONE
         */
        setInputMode(InputController.INPUT_MODE_AUTO)

        val ed : Editor = editorData?.editor ?: return
        ed.renderer.setViewOffset(0f, 0f)
        ed.renderer.viewScale = 1.0f
        val displayMetrics = resources.displayMetrics
        // The editor requires a font metrics provider and a view size *before* calling setPart()
        val typefaceMap: Map<String, Typeface> = HashMap()
        ed.setFontMetricsProvider(FontMetricsProvider(displayMetrics, typefaceMap))
        ed.addListener(editorListener)

        // wait for view size initialization before setting part
        editorView!!.post(Runnable() {
            val partType = "Raw Content" //"Text Document" // other part to test
            try{
                val contentPart: ContentPart = contentPackage!!.createPart(partType)
                title = "Type: " + contentPart.type
                ed.setViewSize(editorView!!.width, editorView!!.height)
                ed.configuration.let { conf ->
                    conf.setBoolean("raw-content.recognition.shape", false)
                    conf.setBoolean("raw-content.recognition.text", true)
                    // Allow conversion of text
                    conf.setBoolean("raw-content.convert.text", true)
                }

                // now search feature is only available for 'Raw Content' part
                if (contentPart.type == "Raw Content" || contentPart.type == "Text Document")
                    searchView!!.visibility = View.VISIBLE

                ed.part = contentPart
            } catch (e: IllegalArgumentException){
                Log.e(
                    TAG,
                    "Failed to create part type \"$partType"
                )
                return@Runnable
            }

            editorView!!.visibility = View.VISIBLE
        })

        searchView!!.setEditor(ed)
        searchView!!.setPropagTouchView(editorView!!)

        findViewById<View>(R.id.button_do_search).setOnClickListener(this)
        findViewById<View>(R.id.button_input_mode_forcePen).setOnClickListener(this)
        findViewById<View>(R.id.button_input_mode_forceTouch).setOnClickListener(this)
        findViewById<View>(R.id.button_input_mode_auto).setOnClickListener(this)
        findViewById<View>(R.id.button_undo).setOnClickListener(this)
        findViewById<View>(R.id.button_redo).setOnClickListener(this)
        findViewById<View>(R.id.button_clear).setOnClickListener(this)

        invalidateIconButtons()
    }


    override fun onDestroy() {
        // this may be called when rotating screen
        // TODO : think about how to keep data when rotating screen
        editorView?.editor?.part?.close()
        editorView?.editor?.clear()
        editorView?.editor?.close()
        editorView?.renderer?.close()
        editorView=null
        contentPackage!!.close()
        contentPackage=null

        super.onDestroy()
    }

    private fun setInputMode(inputMode: Int) {

        editorData?.inputController?.inputMode = inputMode

        findViewById<View>(R.id.button_input_mode_forcePen).isEnabled =
            inputMode != InputController.INPUT_MODE_FORCE_PEN
        findViewById<View>(R.id.button_input_mode_forceTouch).isEnabled =
            inputMode != InputController.INPUT_MODE_FORCE_TOUCH
        findViewById<View>(R.id.button_input_mode_auto).isEnabled =
            inputMode != InputController.INPUT_MODE_AUTO

        searchView!!.forcePenInTouchMode(inputMode == InputController.INPUT_MODE_FORCE_TOUCH)
    }
    private fun invalidateIconButtons() {
        val editor = editorView!!.editor
        val canUndo = editor!!.canUndo()
        val canRedo = editor.canRedo()
        runOnUiThread {
            val imageButtonUndo =
                findViewById<View>(R.id.button_undo) as ImageButton
            imageButtonUndo.isEnabled = canUndo
            val imageButtonRedo =
                findViewById<View>(R.id.button_redo) as ImageButton
            imageButtonRedo.isEnabled = canRedo
            val imageButtonClear =
                findViewById<View>(R.id.button_clear) as ImageButton
            imageButtonClear.isEnabled = (editor.part) != null
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_main, menu)
        val convertMenuItem = menu.findItem(R.id.menu_convert)
        convertMenuItem.isEnabled = true
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (editorView == null) return super.onOptionsItemSelected(item)
        val editor = editorView!!.editor
        if (editor != null) {
            if (!editor.isIdle) editor.waitForIdle()
            when (item.itemId){
                R.id.menu_convert -> {
                    val conversionState = editor.getSupportedTargetConversionStates(editor.rootBlock)
                    if (conversionState.isNotEmpty()) {
                        editor.convert(editor.rootBlock, conversionState.first())
                        doSearch()
                    }
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.button_do_search -> doSearch()
            R.id.button_input_mode_forcePen -> setInputMode(InputController.INPUT_MODE_FORCE_PEN)
            R.id.button_input_mode_forceTouch -> setInputMode(InputController.INPUT_MODE_FORCE_TOUCH)
            R.id.button_input_mode_auto -> setInputMode(InputController.INPUT_MODE_AUTO)
            R.id.button_undo -> {
                editorView!!.editor!!.undo()
                doSearch()
            }
            R.id.button_redo -> {
                editorView!!.editor!!.redo()
                doSearch()
            }
            R.id.button_clear -> {
                editorView!!.editor!!.clear()
                clearSearchResult()
            }
            else -> Log.e(TAG, "Failed to handle click event")
        }
    }

    private fun clearSearchResult() {
        (findViewById<View>(R.id.edit_search_text) as EditText).text.clear()
        (findViewById<View>(R.id.edit_search_text) as EditText).clearFocus()
        searchView!!.clearSearchResult()
        // assign the system service to InputMethodManager then hide keyboard
        val manager: InputMethodManager = getSystemService(
            Context.INPUT_METHOD_SERVICE
        ) as InputMethodManager
        manager
            .hideSoftInputFromWindow(
                (findViewById<View>(R.id.edit_search_text) ).windowToken, 0
            )
    }

    private fun doSearch() {
        val searchWord = (findViewById<View>(R.id.edit_search_text) as EditText).text.toString()
        searchView!!.doSearch(searchWord)
        (findViewById<View>(R.id.edit_search_text) as EditText).clearFocus()
        val manager: InputMethodManager = getSystemService(
            Context.INPUT_METHOD_SERVICE
        ) as InputMethodManager
        manager
            .hideSoftInputFromWindow(
                (findViewById<View>(R.id.edit_search_text) ).windowToken, 0
            )
    }
}