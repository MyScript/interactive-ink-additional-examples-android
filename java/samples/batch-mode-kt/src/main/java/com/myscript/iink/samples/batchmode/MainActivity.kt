package com.myscript.iink.samples.batchmode

import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.myscript.iink.ContentPackage
import com.myscript.iink.ContentPart
import com.myscript.iink.Editor
import com.myscript.iink.MimeType
import com.myscript.iink.PointerEvent
import com.myscript.iink.PointerEventType
import com.myscript.iink.Renderer
import com.myscript.iink.app.common.utils.launchSingleChoiceDialog
import com.myscript.iink.uireferenceimplementation.FontMetricsProvider
import com.myscript.iink.uireferenceimplementation.FontUtils
import com.myscript.iink.uireferenceimplementation.ImageLoader
import com.myscript.iink.uireferenceimplementation.ImagePainter
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var renderer : Renderer
    private lateinit var editor: Editor

    private var contentPackage : ContentPackage? = null
    private var contentPart : ContentPart? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val engine = IInkApplication.getEngine()

        if (engine == null) {
            // The certificate provided is incorrect, you need to use the one provided by MyScript
            AlertDialog.Builder(this)
                .setTitle( getString(R.string.app_error_invalid_certificate_title))
                .setMessage( getString(R.string.app_error_invalid_certificate_message))
                .setPositiveButton(com.myscript.iink.app.common.R.string.dialog_ok, null)
                .show()
            finishAndRemoveTask() // be sure to end the application
            return
        }

        // Configure recognition
        engine.apply {
            configuration.apply {
                val confDir = "zip://${application.packageCodePath}!/assets/conf"
                setStringArray("configuration-manager.search-path", arrayOf(confDir))
                setString("content-package.temp-folder", application.cacheDir.absolutePath)
                // To enable text recognition for a specific language,
                setString("lang", language);
                // Configure the engine to disable guides (recommended in batch mode)
                setBoolean("text.guides.enable", false);
            }
        }

        // At creation we have to pre initialise the editor with dpi and screen size

        // Create a renderer with a null render target
        val displayMetrics = resources.displayMetrics
        renderer = engine.createRenderer(displayMetrics.xdpi, displayMetrics.ydpi, null).apply {
            setViewOffset(0.0f, 0.0f)
            viewScale = 1.0f
        }

        // Create the editor
        editor = engine.createEditor(renderer, engine.createToolController()).apply {
            // The editor requires a font metrics provider and a view size *before* calling setPart()
            val typefaceMap: Map<String, Typeface> = HashMap()
            setFontMetricsProvider(FontMetricsProvider(displayMetrics, typefaceMap))
            setViewSize(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    }

    override fun onStart() {
        super.onStart()
        // Dialog to ask user which part type to process
        launchSingleChoiceDialog(R.string.dialog_Part_choice_Title,
            typeOfPart,
            0,
            { choiceIndex ->
                // this is the function where we process exteranl output and export it
                // add true if you want to export in png
                process(typeOfPart[choiceIndex])

                // Close the application
                Thread {
                    Thread.sleep(2000)// just ot let time to display the toast (2sec)
                    finishAndRemoveTask() // be sure to end the application
                }.start()
            },
            { _, _ -> finishAndRemoveTask() })
    }

    private fun process(partType: String, renderToPNG : Boolean = false) {
        // Create a new package
        contentPackage = try {
             IInkApplication.getEngine()?.createPackage(iinkPackageName)!!
        } catch (e: IOException) {
            Log.e(TAG, "Failed to open package \"$iinkPackageName\"", e)
            AlertDialog.Builder(this)
                .setTitle( "Package Creation IllegalArgumentException")
                .setMessage( "Failed to open package \"$iinkPackageName\"")
                .setPositiveButton(com.myscript.iink.app.common.R.string.dialog_ok, null)
                .show()
            return
        }

        // Create a new part
        contentPart = try {
            contentPackage?.createPart(partType)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to open type of part \"$partType\"", e)
            AlertDialog.Builder(this)
                .setTitle( "Part Creation IllegalArgumentException")
                .setMessage( "Failed to open type of part \"$partType\"")
                .setPositiveButton(com.myscript.iink.app.common.R.string.dialog_ok, null)
                .show()
            return
        }

        // Associate editor with the new part
        editor.part = contentPart

        // Now we can process pointer events and feed the editor with an array of Pointer Events loaded from the json file
        loadAndFeedPointerEvents(partType)

        // Choose the right mimeType to export according to the partType chosen
        var mimeType = MimeType.PNG
        if(!renderToPNG) {
            when (partType) {
                typeOfPart[0] -> mimeType = MimeType.TEXT  // Text
                typeOfPart[1] -> mimeType = MimeType.LATEX // Math
                typeOfPart[2] -> mimeType = MimeType.SVG   // Diagram
                typeOfPart[3] -> mimeType = MimeType.JIIX  // Raw Content
                else -> {}
            }
        }

        // Exported file will be stored in your app's private files directory
        val file = File(applicationContext.filesDir, "$exportFileName${mimeType.fileExtensions}")

        editor.waitForIdle()

        val imagePainter = if (mimeType.isImage) {
            //we have to create a image painter to render in png
             ImagePainter().apply {
                setImageLoader(ImageLoader(editor))
                // load fonts
                val assetManager = applicationContext.assets
                val typefaceMap = FontUtils.loadFontsFromAssets(assetManager)
                setTypefaceMap(typefaceMap)
            }
        } else null

        editor.export_(null, file.absolutePath, mimeType, imagePainter)

        // Quick display of the path where the data has been exported
        Toast.makeText(applicationContext, "File exported in : ${file.path}", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Close everything
        editor.part = null
        contentPart?.close()
        contentPackage?.close()
        IInkApplication.getEngine()?.deletePackage(packageName)
        editor.close()
        renderer.close()
    }

    private fun loadAndFeedPointerEvents(partType: String) {
        val partTypeLowercase = partType.lowercase()
        val pointerEventsPath = if (partTypeLowercase == "text") {
            "conf/pointerEvents/$partTypeLowercase/$language/pointerEvents.json"
        } else {
            "conf/pointerEvents/$partTypeLowercase/pointerEvents.json"
        }

        // Loading the content of the pointerEvents JSON file
        val inputStream = resources.assets.open(pointerEventsPath)

        // Mapping the content into a JsonResult class
        val jsonResult = Gson().fromJson(InputStreamReader(inputStream), JsonResult::class.java)

        // Add each element to a list
        val pointerEventsList = mutableListOf<PointerEvent>()

        val xdpi = resources.displayMetrics.xdpi
        val ydpi = resources.displayMetrics.ydpi

        jsonResult.getStrokes()?.forEach { stroke ->

            val strokeX: FloatArray = stroke.x
            val strokeY: FloatArray = stroke.y
            val strokeT: LongArray = stroke.t
            val strokeP: FloatArray = stroke.p
            val length: Int = stroke.x.size

            for (index in 0 until length) {
                // In batch mode we keep data in a array
                val pointerEvent = PointerEvent()
                pointerEvent.pointerType = stroke.pointerType!!
                pointerEvent.pointerId = stroke.pointerId
                when (index) {
                    0 -> pointerEvent.eventType = PointerEventType.DOWN
                    length - 1 -> pointerEvent.eventType = PointerEventType.UP
                    else -> pointerEvent.eventType = PointerEventType.MOVE
                }

                // Transform the x and y coordinates of the stroke from mm to px
                // This is needed to be adaptive for each device
                pointerEvent.x = strokeX[index] / 25.4f * xdpi
                pointerEvent.y = strokeY[index] / 25.4f * ydpi
                pointerEvent.t = strokeT[index]
                pointerEvent.f = strokeP[index]
                // Add it to the list
                pointerEventsList += pointerEvent
            }
        }
        editor.pointerEvents(pointerEventsList.toTypedArray(), false)
    }

    companion object {
        private const val TAG = "MainActivity"

        // /!\ Warning use the real MyScript name of part as this string will be used for part creation
        private val typeOfPart = listOf("Text", "Math", "Diagram", "Raw Content")
        private const val iinkPackageName = "package.iink"
        private const val exportFileName = "export"
        private const val language = "en_US"
    }
}