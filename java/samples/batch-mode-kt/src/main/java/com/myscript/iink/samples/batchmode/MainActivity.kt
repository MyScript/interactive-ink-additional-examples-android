package com.myscript.iink.samples.batchmode

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.res.ResourcesCompat
import com.google.gson.Gson
import com.myscript.iink.ConversionState
import com.myscript.iink.Editor
import com.myscript.iink.MimeType
import com.myscript.iink.PointerEvent
import com.myscript.iink.PointerEventType
import com.myscript.iink.PointerType
import com.myscript.iink.Renderer
import com.myscript.iink.uireferenceimplementation.FontMetricsProvider
import com.myscript.iink.uireferenceimplementation.FontUtils
import com.myscript.iink.uireferenceimplementation.ImageLoader
import com.myscript.iink.uireferenceimplementation.ImagePainter
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.file.Files

class MainActivity : AppCompatActivity() {

    private lateinit var renderer : Renderer
    private lateinit var editor: Editor
    private lateinit var imagePainter: ImagePainter

    private var useCustomInputFile = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

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
            val typefaceMap = mutableMapOf<String, Typeface>()
            setFontMetricsProvider(FontMetricsProvider(displayMetrics, typefaceMap))
            setViewSize(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }

        // Create a image painter to render in png
        imagePainter = ImagePainter().apply {
            setImageLoader(ImageLoader(editor))
            // load fonts
            val typefaceMap = provideTypefaces()
            setTypefaceMap(typefaceMap)
            editor.setFontMetricsProvider(FontMetricsProvider(applicationContext.resources.displayMetrics, typefaceMap))
            editor.theme = (".math {font-family: STIX;}")
        }

        useCustomInputFile = false

        findViewById<AppCompatButton>(R.id.batch_sample_pick_file).setOnClickListener { _ -> pickFile() }

        findViewById<AppCompatButton>(R.id.batch_sample_remove_file).setOnClickListener(this::removeFile)

        findViewById<AppCompatButton>(R.id.batch_sample_execute).setOnClickListener(this::execute)
    }

    private fun provideTypefaces(): Map<String, Typeface> {
        val typefaces = FontUtils.loadFontsFromAssets(application.assets) ?: mutableMapOf()
        // Map key must be aligned with the font-family used in theme.css
        val myscriptInterFont = ResourcesCompat.getFont(application, R.font.myscriptinter)
        if (myscriptInterFont != null) {
            typefaces["MyScriptInter"] = myscriptInterFont
        }
        val stixFont = ResourcesCompat.getFont(application, R.font.stix)
        if (stixFont != null) {
            typefaces["STIX"] = stixFont
        }
        return typefaces
    }

    private fun pickFile() {
        pickFileLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        })
    }

    private fun removeFile(removeButton : View) {
        useCustomInputFile = false
        removeButton.visibility = View.GONE
        findViewById<AppCompatButton>(R.id.batch_sample_pick_file).visibility = View.VISIBLE
        findViewById<TextView>(R.id.batch_sample_file_description).visibility = View.VISIBLE
    }

    private val pickFileLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            it.data?.data?.also { uri ->
                val contentResolver = applicationContext.contentResolver
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val workingFile = File(applicationContext.cacheDir, customInputFileName)
                    workingFile.delete()
                    Files.copy(inputStream, workingFile.toPath())
                }

                useCustomInputFile = true
                findViewById<AppCompatButton>(R.id.batch_sample_remove_file).visibility = View.VISIBLE
                findViewById<AppCompatButton>(R.id.batch_sample_pick_file).visibility = View.GONE
                findViewById<TextView>(R.id.batch_sample_file_description).visibility = View.GONE
            }
        }

    @OptIn(DelicateCoroutinesApi::class)
    private fun execute(executeButton: View) {
        val partTypeGroup = findViewById<RadioGroup>(R.id.batch_sample_part_type_group)
        val partType = when(partTypeGroup.checkedRadioButtonId) {
            R.id.batch_sample_part_type_math -> "Math"
            R.id.batch_sample_part_type_diagram -> "Diagram"
            R.id.batch_sample_part_type_raw -> "Raw Content"
            else -> "Text"
        }

        val modeGroup = findViewById<RadioGroup>(R.id.batch_sample_mode_group)
        val incremental = when(modeGroup.checkedRadioButtonId) {
            R.id.batch_sample_mode_incremental -> true
            else -> false
        }

        val outputGroup = findViewById<RadioGroup>(R.id.batch_sample_output_group)
        val pngOutput = when(outputGroup.checkedRadioButtonId) {
            R.id.batch_sample_output_png -> true
            else -> false
        }

        val outputStyleGroup = findViewById<RadioGroup>(R.id.batch_sample_output_style_group)
        val convert = when(outputStyleGroup.checkedRadioButtonId) {
            R.id.batch_sample_output_style_converted -> true
            else -> false
        }

        val progress = findViewById<ProgressBar>(R.id.batch_sample_progress)

        progress.visibility = View.VISIBLE
        executeButton.isEnabled = false
        partTypeGroup.isEnabled = false
        modeGroup.isEnabled = false
        outputGroup.isEnabled = false
        outputStyleGroup.isEnabled = false

        GlobalScope.launch(Dispatchers.Main) {
            val outputFile = withContext(Dispatchers.IO) {
                process(partType, incremental, pngOutput, convert)
            }

            progress.visibility = View.GONE
            executeButton.isEnabled = true
            partTypeGroup.isEnabled = true
            modeGroup.isEnabled = true
            outputGroup.isEnabled = true
            outputStyleGroup.isEnabled = true

            if (!outputFile.exists()) {
                Toast.makeText(applicationContext, "An error occurred when exporting", Toast.LENGTH_LONG).show()
                return@launch
            }

            exportedFilePath = outputFile.absolutePath

            saveFileLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "application/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_TITLE, outputFile.name)
            })
        }
    }

    private var exportedFilePath: String? = null
    private val saveFileLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            exportedFilePath?.let { exportedFilePath ->
                val exportedFile = File(exportedFilePath)
                if (exportedFile.exists()) {
                    it.data?.data?.also { uri ->
                        val contentResolver = applicationContext.contentResolver
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            Files.copy(exportedFile.toPath(), outputStream)
                            this.exportedFilePath = null
                        }
                    }
                }
            }
        }

    override fun onDestroy() {
        super.onDestroy()

        editor.close()
        renderer.close()
    }

    private fun process(partType: String, incremental: Boolean, renderToPNG: Boolean, convert: Boolean): File {
        val engine = requireNotNull(IInkApplication.getEngine())

        // Create a new package
        val contentPackage =  engine.createPackage(iinkPackageName)
        // Create a new part
        val contentPart = contentPackage.createPart(partType)
        // Associate editor with the new part
        editor.part = contentPart

        // Now we can process pointer events and feed the editor with an array of Pointer Events loaded from the json file
        loadAndFeedPointerEvents(partType, incremental)

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

        editor.waitForIdle()

        if (convert) {
            editor.convert(editor.rootBlock, ConversionState.DIGITAL_EDIT)
        }
        val outputFile = File(applicationContext.filesDir, "$exportFileName${mimeType.fileExtensions}")
        editor.export_(editor.rootBlock, outputFile.absolutePath, mimeType, if (mimeType.isImage) imagePainter else null)

        // Closing after using
        editor.part = null
        contentPart.close()
        contentPackage.close()
        engine.deletePackage(packageName)

        return outputFile
    }

    private fun loadAndFeedPointerEvents(partType: String, incremental: Boolean = false) {
        // Loading the content of the pointerEvents JSON file
        val customInputFile = File(applicationContext.cacheDir, customInputFileName)
        val inputStream =  if (useCustomInputFile && customInputFile.exists()) {
            FileInputStream(customInputFile)
        } else {
            val partTypeLowercase = partType.lowercase()
            val pointerEventsPath = if (partTypeLowercase == "text") {
                "conf/pointerEvents/$partTypeLowercase/$language/pointerEvents.json"
            } else {
                "conf/pointerEvents/$partTypeLowercase/pointerEvents.json"
            }

            resources.assets.open(pointerEventsPath)
        }

        // Mapping the content into a JsonResult class
        val jsonResult = Gson().fromJson(InputStreamReader(inputStream), JsonResult::class.java)

        // Add each element to a list
        val pointerEventsList = mutableListOf<PointerEvent>()

        val xdpi = resources.displayMetrics.xdpi
        val ydpi = resources.displayMetrics.ydpi

        jsonResult.getStrokes()?.forEach { stroke ->
            val strokeX = stroke.x
            val strokeY = stroke.y
            val strokeT = stroke.t
            val strokeP = stroke.p
            val length = stroke.x.size

            for (index in 0 until length) {
                // Transform the x and y coordinates of the stroke from mm to px
                // This is needed to be adaptive for each device
                val x = strokeX[index] / 25.4f * xdpi
                val y = strokeY[index] / 25.4f * ydpi

                if (incremental) {
                    // In incremental mode we send data direct to the editor
                    when (index) {
                        0 -> editor.pointerDown(x, y, strokeT[index], strokeP[index], PointerType.PEN, 1)
                        length - 1 -> editor.pointerUp(x, y, strokeT[index], strokeP[index], PointerType.PEN, 1)
                        else -> editor.pointerMove(x, y, strokeT[index], strokeP[index], PointerType.PEN, stroke.pointerId)
                    }
                } else {
                    // In batch mode we keep data in a array
                    pointerEventsList += PointerEvent().apply {
                        pointerType = stroke.pointerType
                        pointerId = stroke.pointerId
                        eventType = when (index) {
                            0 -> PointerEventType.DOWN
                            length - 1 -> PointerEventType.UP
                            else -> PointerEventType.MOVE
                        }
                        this.x = x
                        this.y = y
                        t = strokeT[index]
                        f = strokeP[index]
                    }
                }
            }
        }
        if (!incremental) {
            editor.pointerEvents(pointerEventsList.toTypedArray(), false)
        }
    }

    companion object {
        private const val TAG = "MainActivity"

        private const val customInputFileName = "customInputFile"

        // /!\ Warning use the real MyScript name of part as this string will be used for part creation
        private val typeOfPart = listOf("Text", "Math", "Diagram", "Raw Content")
        private const val iinkPackageName = "package.iink"
        private const val exportFileName = "export"
        private const val language = "en_US"
    }
}