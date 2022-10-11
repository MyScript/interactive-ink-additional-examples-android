package com.myscript.iink.samples.assessment

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.annotation.DimenRes
import androidx.annotation.NonNull
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.myscript.iink.*
import com.myscript.iink.app.common.activities.ErrorActivity
import com.myscript.iink.uireferenceimplementation.*

class MainActivity : AppCompatActivity(), IEditorListener {
    private val TAG = "Main activity"
    private val IINK_PACKAGE_NAME = "my_iink_package"

    private var contentPackage : ContentPackage? = null
    private var answerEditorView1: EditorView? = null
    private var answerEditorView2: EditorView? = null
    private var answerEditorView3: EditorView? = null
    private var answerEditorView4: EditorView? = null
    private var answerEditorView5: EditorView? = null
    private var activeEditorView: EditorView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ErrorActivity.setExceptionHandler(applicationContext)

        // Note: could be managed by domain layer and handled through observable error channel
        // but kept simple as is to avoid adding too much complexity for this special (unrecoverable) error case
        if (MyIInkApplication.getEngine() == null) {
            // the certificate provided in `BatchModule.provideEngine` is most likely incorrect
            AlertDialog.Builder(this)
                    .setTitle( getString(R.string.app_error_invalid_certificate_title))
                    .setMessage( getString(R.string.app_error_invalid_certificate_message))
                    .setPositiveButton(R.string.dialog_ok,
                        DialogInterface.OnClickListener {
                            _,
                            _ ->
                            finishAffinity()
                            finishAndRemoveTask() // be sure to end the application
                        })
                    .show()
            return
        }
        // Create a new package
        contentPackage = MyIInkApplication.getEngine()?.createPackage(IINK_PACKAGE_NAME)
        if(contentPackage==null){
            return;
        }


        // TODO: try different part types: Diagram, Drawing, Math, Text, Text Document.
        answerEditorView1 =findViewById<View>(R.id.problemSolver1).findViewById(R.id.editor_view)
        initWith(answerEditorView1, contentPackage!!,"Math","standard")

        answerEditorView2 =findViewById<View>(R.id.problemSolver2).findViewById(R.id.editor_view)
        initWith(answerEditorView2, contentPackage!!,"Math","standardK8")

        answerEditorView3 =findViewById<View>(R.id.problemSolver3).findViewById(R.id.editor_view)
        initWith(answerEditorView3, contentPackage!!,"Text")

        answerEditorView4 =findViewById<View>(R.id.problemSolver4).findViewById(R.id.editor_view)
        initWith(answerEditorView4, contentPackage!!,"Diagram")

        answerEditorView5 =findViewById<View>(R.id.problemSolver5).findViewById(R.id.editor_view)
        initWith(answerEditorView5, contentPackage!!,"Drawing")
    }

    fun initWith(@NonNull editorView: EditorView?,@NonNull contentPackage: ContentPackage,@NonNull  partType:String, mathConfig: String="" ){
        if(editorView==null||contentPackage==null||partType.isEmpty())
            return

        val editorBinding: EditorBinding = EditorBinding(MyIInkApplication.getEngine(),
            FontUtils.loadFontsFromAssets(application.assets) ?: emptyMap())

        val editorData = editorBinding.openEditor(editorView)
        editorData.inputController?.setViewListener(editorView)

        /* value to test
        InputController.INPUT_MODE_FORCE_PEN
        InputController.INPUT_MODE_FORCE_TOUCH
        InputController.INPUT_MODE_ERASER
        InputController.INPUT_MODE_AUTO
        InputController.INPUT_MODE_NONE
         */
        editorData.inputController?.inputMode = InputController.INPUT_MODE_FORCE_PEN

        val ed : Editor = editorData.editor ?: return
        ed.renderer.setViewOffset(0f, 0f)
        ed.renderer.viewScale = 1.0f
        val displayMetrics = resources.displayMetrics
        // to have a nice rendering we have to set margin this wil avoid small rendering of converted mathematic formula
        // as we cannot set the font size, we have to set margin in order to have a nice render
        setEditorMargins(ed, R.dimen.editor_horizontal_margin, R.dimen.editor_vertical_margin)

        // The editor requires a font metrics provider and a view size *before* calling setPart()
        val typefaceMap: Map<String, Typeface> = HashMap()
        ed.setFontMetricsProvider(FontMetricsProvider(displayMetrics, typefaceMap))
        ed.setViewSize(editorView.width, editorView.height)

        ed.addListener(this)

        // wait for view size initialization before setting part
        editorView.post(Runnable() {
            try {
                val contentPart: ContentPart = contentPackage.createPart(partType)

                val configuration: Configuration = ed.configuration
                when (contentPart.type) {
                    "Math" -> {
                        // disable math solver result
                        configuration.setBoolean("math.solver.enable", false)
                        configuration.setString("math.configuration.bundle", "math")
                        configuration.setString("math.configuration.name", mathConfig)
                    }
                    "Text" ->                         // disable text guide lines
                        configuration.setBoolean("text.guides.enable", false)
                    "Diagram" ->{
                        // ADD SOME CONFIGURATION TO TEST
                    }
                    "Drawing" ->{
                        // ADD SOME CONFIGURATION TO TEST
                    }
                    else -> {}
                }

                ed.part = contentPart
            } catch (e: IllegalArgumentException){
                return@Runnable
            }

            editorView.visibility = View.VISIBLE
            editorView.setBackgroundColor(Color.LTGRAY)
        })

    }

    private fun setEditorMargins(editor: Editor, @DimenRes horizontalMarginRes: Int, @DimenRes verticalMarginRes: Int) {
        val displayMetrics = resources.displayMetrics
        with (editor.configuration) {
            val verticalMargin = resources.getDimension(verticalMarginRes)
            val horizontalMargin = resources.getDimension(horizontalMarginRes)
            val verticalMarginMM =  25.4f * verticalMargin / displayMetrics.ydpi
            val horizontalMarginMM = 25.4f * horizontalMargin / displayMetrics.xdpi
            setNumber("text.margin.left", horizontalMarginMM)
            setNumber("text.margin.right", horizontalMarginMM)
            setNumber("math.margin.top", verticalMarginMM)
            setNumber("math.margin.bottom", verticalMarginMM)
            setNumber("math.margin.left", horizontalMarginMM)
            setNumber("math.margin.right", horizontalMarginMM)
        }
    }

    private fun cleanEditorView(edV :EditorView){
        edV.editor?.part?.close()
        edV.editor?.clear()
        edV.editor?.close()
        edV.renderer?.close()
    }
    override fun onDestroy() {
        // this may be called when rotating screen
        // TODO : think about how to keep data when rotating screen
        activeEditorView=null
        answerEditorView1?.let { cleanEditorView(it) }
        answerEditorView1=null
        answerEditorView2?.let { cleanEditorView(it) }
        answerEditorView2=null
        answerEditorView3?.let { cleanEditorView(it) }
        answerEditorView3=null
        answerEditorView4?.let { cleanEditorView(it) }
        answerEditorView4=null
        answerEditorView5?.let { cleanEditorView(it) }
        answerEditorView5=null
        contentPackage!!.close()
        contentPackage=null

        super.onDestroy()
    }
    // region implementations (options menu)
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.activity_main,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        if (menu == null)
            return super.onPrepareOptionsMenu(menu)

        if (activeEditorView == null) {
            //disable menu if no active part
            menu.findItem(R.id.menu_redo).isEnabled = false
            menu.findItem(R.id.menu_undo).isEnabled = false
            menu.findItem(R.id.menu_clear).isEnabled = false
            menu.findItem(R.id.menu_convert).isEnabled = false
            return super.onPrepareOptionsMenu(menu)
        }

        val editor = activeEditorView!!.editor
        menu.findItem(R.id.menu_redo).isEnabled = editor != null && editor.canRedo()
        menu.findItem(R.id.menu_undo).isEnabled = editor != null && editor.canUndo()
        if (editor == null) return super.onPrepareOptionsMenu(menu)
        val contentPart = editor.part
        menu.findItem(R.id.menu_clear).isEnabled = contentPart != null && !contentPart.isClosed
        menu.findItem(R.id.menu_convert).isEnabled =
            contentPart?.type!="Drawing" &&
                    !editor.isEmpty(editor.rootBlock) &&
                            editor.getSupportedTargetConversionStates(editor.rootBlock).isNotEmpty()
        return super.onPrepareOptionsMenu(menu)
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (activeEditorView == null) return super.onOptionsItemSelected(item)
        val editor = activeEditorView!!.editor
        if (editor != null) {
            if (!editor.isIdle) editor.waitForIdle()
            when (item.itemId){
                R.id.menu_clear -> editor.clear()
                R.id.menu_convert -> {
                    val conversionState = editor.getSupportedTargetConversionStates(editor.rootBlock)
                    if (conversionState != null && conversionState.isNotEmpty()) {
                        editor.convert(editor.rootBlock, conversionState.first())
                    }
                }
                R.id.menu_undo -> editor.undo()
                R.id.menu_redo -> editor.redo()
            }
        }

        return super.onOptionsItemSelected(item)
    }
    // end region

    // convenient function to check if a touch is inside a specified zone....
    // and then set an editor as active
    private fun checkIfTouchIsInside(editorView: EditorView?, Xpos : Int, Ypos: Int): Boolean {
        val rec = Rect()
        val location = IntArray(2)
        editorView?.getDrawingRect(rec)
        editorView?.getLocationOnScreen(location)
        rec.offset(location[0],location[1])
        if(rec.contains(Xpos, Ypos)) {
            if(editorView == activeEditorView)
                return true
            if (activeEditorView != null)
                activeEditorView?.setBackgroundColor(Color.LTGRAY)
            activeEditorView = editorView
            activeEditorView?.setBackgroundColor(Color.WHITE)
            invalidateOptionsMenu()
            return true
        }
        return false
    }

    //function to make touch on editor change the background of the editor we are working on
    override fun dispatchTouchEvent(ev: MotionEvent?) : Boolean{
        return when (ev!!.action) {
            MotionEvent.ACTION_DOWN -> {
                val evX = ev.x.toInt();val evY= ev.y.toInt()

                val rectangle = Rect()
                window.decorView.getWindowVisibleDisplayFrame(rectangle)
                val statusBarHeight = rectangle.top
                val contentViewTop: Int = findViewById<View>(Window.ID_ANDROID_CONTENT).top

                if(evY<(contentViewTop+statusBarHeight))
                    return super.dispatchTouchEvent(ev)

                if(activeEditorView!=null && checkIfTouchIsInside(activeEditorView,evX,evY)) {
                    return super.dispatchTouchEvent(ev)
                }
                if(checkIfTouchIsInside(answerEditorView1,evX,evY) ||
                    checkIfTouchIsInside(answerEditorView2,evX,evY) ||
                    checkIfTouchIsInside(answerEditorView3,evX,evY) ||
                    checkIfTouchIsInside(answerEditorView4,evX,evY) ||
                    checkIfTouchIsInside(answerEditorView5,evX,evY)
                ){
                    return super.dispatchTouchEvent(ev)
                }
                super.dispatchTouchEvent(ev)
            }
            else -> super.dispatchTouchEvent(ev)
        }
    }

    // region implementations (IEditorListener)
      /**************************************************/
     /******   IEditor listener implementation   *******/
    /**************************************************/
    override fun partChanging(p0: Editor, p1: ContentPart?, p2: ContentPart?) {
        // no-op
    }

    override fun partChanged(p0: Editor) {
        invalidateOptionsMenu()
    }

    override fun contentChanged(editor: Editor, p1: Array<out String>) {
        invalidateOptionsMenu()
    }

    override fun onError(editor: Editor, blockId: String, err: EditorError, message: String) {
        if (activeEditorView == null) {
            Log.e(
                TAG,
                "Failed to edit block \"$blockId\"$message"
            )
            return
        }
        activeEditorView!!.post {
            Toast.makeText(
                activeEditorView!!.context,
                "$blockId:${err.name}:$message",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun selectionChanged(p0: Editor) {
        // no-op
    }

    override fun activeBlockChanged(p0: Editor, p1: String) {
        // no-op
    }

    // end region
}
