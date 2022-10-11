package com.myscript.iink.samples.batchmode

import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.myscript.iink.*
import com.myscript.iink.app.common.utils.autoCloseable
import com.myscript.iink.app.common.utils.launchSingleChoiceDialog
import com.myscript.iink.uireferenceimplementation.FontMetricsProvider
import com.myscript.iink.uireferenceimplementation.FontUtils
import com.myscript.iink.uireferenceimplementation.ImageLoader
import com.myscript.iink.uireferenceimplementation.ImagePainter
import java.io.*


class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    /**/
    private var renderer by autoCloseable<Renderer>(null)
    private var editor by autoCloseable<Editor>(null)
    private var contentPackage : ContentPackage? =null
    private var contentPart : ContentPart? = null

    /**/
    //warning use the real myscript name of part as this string will be use for part creation
    private val typeOfPart = listOf("Text", "Math", "Diagram", "Raw Content")
    private val iinkPackageName = "package.iink"
    private val exportFileName = "export"
    private val language = "en_US"
    private val incremental = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Note: could be managed by domain layer and handled through observable error channel
        // but kept simple as is to avoid adding too much complexity for this special (unrecoverable) error case
        if (IInkApplication.getEngine() == null) {
            // the certificate provided in `BatchModule.provideEngine` is most likely incorrect
            AlertDialog.Builder(this)
                .setTitle( getString(R.string.app_error_invalid_certificate_title))
                .setMessage( getString(R.string.app_error_invalid_certificate_message))
                .setPositiveButton(R.string.dialog_ok, null)
                .show()
            finishAndRemoveTask() // be sure to end the application
            return
        }

        // at creation we have to pre initialise the editor with dpi and screen size
        intialConfiguration()

        //Small dialog to ask user which type of Part he/she wants to proceed
        launchSingleChoiceDialog(R.string.dialog_Part_choice_Title,
                typeOfPart,
                0,
           {
                // this is the function where we process exteranl output and export it
                // add true if you want to export in png
                offScreenProcess(typeOfPart[it])

                //close the application
                Thread(Runnable {
                    Thread.sleep(2000)// just ot let time to display the toast (2sec)
                    finishAndRemoveTask() // be sure to end the application
                }).start()
            },
            DialogInterface.OnClickListener { _, _ -> finishAndRemoveTask() })
    }

    private fun intialConfiguration(){
        val displayMetrics = resources.displayMetrics

        // configure recognition
        IInkApplication.getEngine()?.apply {
            configuration.let { conf ->
                val confDir = "zip://${application.packageCodePath}!/assets/conf"
                conf.setStringArray("configuration-manager.search-path", arrayOf(confDir))
                val tempDir = File(application.cacheDir, "tmp")
                conf.setString("content-package.temp-folder", tempDir.absolutePath)

                // To enable text recognition for a specific language,
                conf.setString("lang", language);
                // Configure the engine to disable guides (recommended in batch mode)
                conf.setBoolean("text.guides.enable", false);
            }
        }

        // Create a renderer with a null render target
        renderer = IInkApplication.getEngine()?.createRenderer(displayMetrics.xdpi, displayMetrics.ydpi, null)
        renderer?.setViewOffset(0.0f, 0.0f)
        renderer?.viewScale = 1.0f

        // Create the editor
        editor = IInkApplication.getEngine()?.createEditor(renderer!!, IInkApplication.getEngine()!!.createToolController())

        // The editor requires a font metrics provider and a view size *before* calling setPart()
        val typefaceMap: Map<String, Typeface> = HashMap()
        editor!!.setFontMetricsProvider(FontMetricsProvider(displayMetrics, typefaceMap))
        editor!!.setViewSize(displayMetrics.widthPixels, displayMetrics.heightPixels);
    }

    private fun offScreenProcess(partType: String, renderToPNG : Boolean = false){
        try {
            // Create a new package
            contentPackage = IInkApplication.getEngine()?.createPackage(iinkPackageName)!!

            // Create a new part
            contentPart = contentPackage!!.createPart(partType)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to open package \"$iinkPackageName\"", e)
            AlertDialog.Builder(this)
                .setTitle( "Package Creation IllegalArgumentException")
                .setMessage( "Failed to open package \"$iinkPackageName\"")
                .setPositiveButton(R.string.dialog_ok, null)
                .show()
            return;

        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to open type of part \"$partType\"", e)
            AlertDialog.Builder(this)
                .setTitle( "Part Creation IllegalArgumentException")
                .setMessage( "Failed to open type of part \"$partType\"")
                .setPositiveButton(R.string.dialog_ok, null)
                .show()
            return;
        }

        // Associate editor with the new part
        editor!!.part = contentPart

        // now we can process pointer events
        // and we feed the editor with an array of Pointer Events loaded for the right json file
        // incremntal way or not
        loadAndFeedPointerEvents(incremental, editor!!, partType, resources.displayMetrics)


        // choose the right mimeType to export according to the partType we choose
        var mimeType = MimeType.PNG
        if(!renderToPNG) {
            when (partType) {
                typeOfPart[0] -> mimeType = MimeType.TEXT  // Text
                typeOfPart[1] -> mimeType = MimeType.LATEX // Math
                typeOfPart[2] -> mimeType = MimeType.SVG   // Diagram
                typeOfPart[3] -> mimeType = MimeType.JIIX  //Raw Content
                else -> {}
            }
        }

        // Exported file is stored in the Virtual SD Card : "Android/data/com.myscript.iink.samples.batchmode/files"
        val file = File(
            getExternalFilesDir(null),
            File.separator + exportFileName.toString() + mimeType.fileExtensions
        )
        editor!!.waitForIdle()
        try {
            var imagePainter : ImagePainter? = null
            if(mimeType.isImage) {
                //we have to create a image painter to render in png
                imagePainter = ImagePainter().apply {
                    setImageLoader(ImageLoader(editor!!))
                    // load fonts

                    // load fonts
                    val assetManager = applicationContext.assets
                    val typefaceMap = FontUtils.loadFontsFromAssets(assetManager)
                    setTypefaceMap(typefaceMap)
                }
            }
            editor!!.export_(null, file.getAbsolutePath(), mimeType, imagePainter)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        //quick reminder display of where the data has been exported
        Toast.makeText(applicationContext, "File exported in : ${file.path}", Toast.LENGTH_SHORT).show()

        //clean the elements
        editor!!.part = null
        contentPart?.close()
        contentPackage?.close()
        try {
            IInkApplication.getEngine()?.deletePackage(packageName)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        editor!!.close()
        renderer!!.close()

    }

    private fun loadAndFeedPointerEvents(incremental : Boolean, editor: Editor, partType: String, displayMetrics: DisplayMetrics){
        var pointerEventsPath =
                "conf/pointerEvents/${partType.lowercase()}/pointerEvents.json"
        if (partType.lowercase().equals("text")) {
            pointerEventsPath = "conf/pointerEvents/${partType.lowercase()}/$language/pointerEvents.json"
        }

        try {
            // Loading the content of the pointerEvents JSON file
            val inputStream: InputStream = resources.assets.open(pointerEventsPath)

            // Mapping the content into a JsonResult class
            val jsonResult: JsonResult =
                    Gson().fromJson(InputStreamReader(inputStream), JsonResult::class.java)

            // add each element to a list
            var pointerEventsList = mutableListOf<PointerEvent>()

            for (stroke in jsonResult.getStrokes()!!) {
                val strokeX: FloatArray = stroke.x
                val strokeY: FloatArray = stroke.y
                val strokeT: LongArray = stroke.t
                val strokeP: FloatArray = stroke.p
                val length: Int = stroke.x.size
                for (j in 0 until length) {
                    if(incremental){
                        //in incremental mode we send data direct to the editor
                        if (j == 0) {
                            editor.pointerDown(strokeX[j] / 25.4f * displayMetrics.xdpi,
                                strokeY[j] / 25.4f * displayMetrics.ydpi,
                                strokeT[j],
                                strokeP[j],
                                PointerType.PEN,
                                1)
                        } else if (j == length - 1) {
                            editor.pointerUp(strokeX[j] / 25.4f * displayMetrics.xdpi,
                                strokeY[j] / 25.4f * displayMetrics.ydpi,
                                strokeT[j],
                                strokeP[j],
                                PointerType.PEN,
                                1)
                        } else {
                            editor.pointerMove(strokeX[j] / 25.4f * displayMetrics.xdpi,
                                strokeY[j] / 25.4f * displayMetrics.ydpi,
                                strokeT[j],
                                strokeP[j],
                                PointerType.PEN,
                                stroke.pointerId)
                        }
                    }else {
                        //in batch mode we keep data in a array
                        val pointerEvent = PointerEvent()
                        pointerEvent.pointerType = stroke.pointerType!!
                        pointerEvent.pointerId = stroke.pointerId
                        if (j == 0) {
                            pointerEvent.eventType = PointerEventType.DOWN
                        } else if (j == length - 1) {
                            pointerEvent.eventType = PointerEventType.UP
                        } else {
                            pointerEvent.eventType = PointerEventType.MOVE
                        }

                        // Transform the x and y coordinates of the stroke from mm to px
                        // This is needed to be adaptive for each device
                        pointerEvent.x = strokeX[j] / 25.4f * displayMetrics.xdpi
                        pointerEvent.y = strokeY[j] / 25.4f * displayMetrics.ydpi
                        pointerEvent.t = strokeT[j]
                        pointerEvent.f = strokeP[j]
                        //add it to the list
                        pointerEventsList += pointerEvent
                    }
                }
            }
            if(!incremental){
                editor.pointerEvents(pointerEventsList.toTypedArray(), false)
            }
        } catch (e: FileNotFoundException) {
            AlertDialog.Builder(this)
                    .setTitle( "file not found")
                    .setMessage( "no file to parse found : $pointerEventsPath")
                    .setPositiveButton(R.string.dialog_ok, null)
                    .show()
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}