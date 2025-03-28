// Copyright @ MyScript. All rights reserved.
package com.myscript.iink.samples.hwgeneration

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.coroutineScope
import com.google.android.material.slider.Slider
import com.myscript.iink.Engine
import com.myscript.iink.PredefinedHandwritingProfileId
import com.myscript.iink.samples.hwgeneration.databinding.ActivityMainBinding
import com.myscript.iink.uireferenceimplementation.EditorView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.util.UUID


class MainActivity : AppCompatActivity() {

    private var engine: Engine? = null

    private var editorView: EditorView? = null

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private val editorViewModel: EditorViewModel by viewModels {
        EditorViewModelFactory(application)
    }

    private val generationViewModel: GenerationViewModel by viewModels {
        GenerationViewModelFactory(application, requireNotNull(engine))
    }

    private val profileBuildingProgress by lazy {
        @Suppress("DEPRECATION")
        ProgressDialog(this).apply {
            setMessage("Building Handwriting Profile...")
            setCancelable(false)
        }
    }

    private var actionMode: ActionMode? = null
    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menuInflater.inflate(R.menu.action_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.findItem(R.id.action_menu_build_profile).isEnabled = generationViewModel.isProfileBuilding.value != true
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_menu_build_profile -> {
                    editorViewModel.selection.value?.let {
                        generationViewModel.buildProfile(it)
                    }
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            editorViewModel.setSelectionMode(false)
        }
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
            return
        }

        // configure recognition
        engine?.configuration?.let { configuration ->
            val confDir = "zip://$packageCodePath!/assets/conf"
            val hwResDir = "zip://$packageCodePath!/assets/resources/handwriting_generation"
            configuration.setStringArray("configuration-manager.search-path", arrayOf(confDir, hwResDir))
            configuration.setString("content-package.temp-folder", File(filesDir, "tmp").absolutePath)
        }

        setContentView(binding.root)

        supportActionBar?.title = getString(R.string.app_name)

        editorView = findViewById(com.myscript.iink.uireferenceimplementation.R.id.editor_view)

        engine?.let { engine ->
            editorView?.let { editorView ->
                editorViewModel.openEditor(engine, editorView)
            }
        }

        editorViewModel.isWriting.observe(this) { isWriting ->
            invalidateOptionsMenu()
            binding.readOnlyLayer.visibility = if (isWriting) View.VISIBLE else View.GONE
        }

        editorViewModel.partHistoryState.observe(this) {
            invalidateOptionsMenu()
        }

        editorViewModel.onLongPress.observe(this) { (happened, x, y) ->
            if (happened) {
                editorViewModel.onLongPressConsummed()
                displayInput(x, y)
            }
        }

        editorViewModel.isSelectionMode.observe(this) { isSelectionMode ->
            if (isSelectionMode) {
                actionMode = startActionMode(actionModeCallback)
            } else {
                if (actionMode != null) {
                    actionMode?.finish()
                    actionMode = null
                }
            }
        }

        try {
            // Dummy check to verify handwriting generation resource and proper certificate.
            val generationViewModel = this.generationViewModel
        } catch (e: IllegalStateException) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.app_error_missing_resource_title))
                .setMessage(Html.fromHtml(getString(R.string.app_error_missing_resource_message)))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        generationViewModel.hwrResults.observe(this) { hwrResults ->
            if (hwrResults.isNotEmpty()) {
                editorViewModel.write(hwrResults.last().events)
            }
        }

        generationViewModel.isProfileBuilding.observe(this) { isProfileBuilding ->
            if (isProfileBuilding) {
                if (!profileBuildingProgress.isShowing) {
                    profileBuildingProgress.show()
                }

            } else {
                if (profileBuildingProgress.isShowing) {
                    profileBuildingProgress.dismiss()
                }
                actionMode?.finish()
            }
            invalidateOptionsMenu()
            actionMode?.invalidate()
            binding.readOnlyLayer.visibility = if (isProfileBuilding) View.VISIBLE else View.GONE
        }

        generationViewModel.message.observe(this) { message ->
            if (message == null) {
                return@observe
            }
            Log.d("MainActivity", message.toString(), message.exception)
            when (message.type) {
                Message.Type.ERROR ->
                    AlertDialog.Builder(this)
                        .setMessage(message.message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                else -> Toast.makeText(applicationContext, message.message, Toast.LENGTH_LONG).show()
            }
            generationViewModel.dismissMessage(message)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val canUndo = editorViewModel.partHistoryState.value?.canUndo ?: false
        val canRedo = editorViewModel.partHistoryState.value?.canRedo ?: false
        val isWriting = editorViewModel.isWriting.value ?: false
        val isProfileBuilding = try { generationViewModel.isProfileBuilding.value } catch (e: Exception) { false } ?: false
        val isEditorEmpty = editorViewModel.isEmpty()

        menu?.findItem(R.id.editor_menu_undo)?.isEnabled = !isProfileBuilding && !isWriting && canUndo
        menu?.findItem(R.id.editor_menu_redo)?.isEnabled = !isProfileBuilding && !isWriting && canRedo
        menu?.findItem(R.id.editor_menu_clear)?.isEnabled = !isProfileBuilding && !isWriting && !isEditorEmpty

        menu?.findItem(R.id.editor_menu_save_as)?.isEnabled = !isProfileBuilding && !isWriting && !isEditorEmpty
        menu?.findItem(R.id.editor_menu_go)?.isEnabled = !isProfileBuilding && !isWriting

        menu?.findItem(R.id.editor_menu_build_profile_from_file)?.isEnabled = !isProfileBuilding && !isWriting
        menu?.findItem(R.id.editor_menu_build_profile)?.isEnabled = !isProfileBuilding && !isWriting && !isEditorEmpty

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.editor_menu_undo -> {
                editorViewModel.undo()
                true
            }
            R.id.editor_menu_redo -> {
                editorViewModel.redo()
                true
            }
            R.id.editor_menu_clear -> {
                editorViewModel.clear()
                true
            }
            R.id.editor_menu_go -> {
                displayInput()
                true
            }
            R.id.editor_menu_build_profile_from_file -> {
                importRequest.launch("*/*")
                true
            }
            R.id.editor_menu_save_as -> {
                saveAs()
                true
            }
            R.id.editor_menu_build_profile -> {
                editorViewModel.setSelectionMode(true)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        generationViewModel.cancel()
        editorViewModel.closeEditor()

        editorView?.let {
            it.setOnTouchListener(null)
            it.editor = null
        }

        // IInkApplication has the ownership, do not close here
        engine = null

        super.onDestroy()
    }

    private fun saveAs() {
        val exportedFile = File(cacheDir, EXPORTED_FILE_NAME)
        editorViewModel.saveAs(exportedFile)

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
                   generationViewModel.buildProfile(file)
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

    private fun displayInput(x: Float = X_OFFSET_DEFAULT_PX, y: Float = Y_OFFSET_DEFAULT_PX) {
        val dialogBuilder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_input, null)
        dialogBuilder.setView(dialogView)
        dialogBuilder.setTitle("Handwriting Generation Input")

        val inputText = dialogView.findViewById<EditText>(R.id.text_input)

        val spinner = dialogView.findViewById<Spinner>(R.id.profile_dropdown)
        val adapter = StyleAdapter(this@MainActivity, generationViewModel.getProfiles())
        spinner.setAdapter(adapter)

        val inputTextSize = dialogView.findViewById<Slider>(R.id.text_size)

        dialogBuilder.setPositiveButton(android.R.string.ok) { _, _ ->
            val textSize = inputTextSize.value
            val profile = spinner.selectedItem as GenerationProfile
            generationViewModel.generateHandwriting(inputText.text.toString().trim(), profile, textSize, x, y, editorView?.width?.toFloat() ?: 0f, editorViewModel.transform())
        }
        dialogBuilder.setNegativeButton(android.R.string.cancel, null)

        val alertDialog = dialogBuilder.create()
        alertDialog.show()
    }

    companion object {
        private const val EXPORTED_FILE_NAME = "export.iink"

        private const val X_OFFSET_DEFAULT_PX = 50f
        private const val Y_OFFSET_DEFAULT_PX = 200f
    }
}

class StyleAdapter(private val context: Context, private val profiles: List<GenerationProfile>) : BaseAdapter() {

    override fun getCount(): Int {
        return profiles.size
    }

    override fun getItem(position: Int): GenerationProfile {
        return profiles[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.style_spinner_item, parent, false)

        val generationProfile = getItem(position)

        val profileImage = view.findViewById<ImageView>(R.id.profile_image)
        val profileText = view.findViewById<TextView>(R.id.profile_text)

        if (generationProfile.profilePath != null) {
            profileImage.visibility = View.GONE
            profileText.visibility = View.VISIBLE
            profileText.text = File(generationProfile.profilePath).nameWithoutExtension
        } else {
            profileImage.setImageResource(when (generationProfile.id) {
                PredefinedHandwritingProfileId.DEFAULT -> R.drawable.generation_0
                PredefinedHandwritingProfileId.PROFILE_1 -> R.drawable.generation_1
                PredefinedHandwritingProfileId.PROFILE_2 -> R.drawable.generation_2
                PredefinedHandwritingProfileId.PROFILE_3 -> R.drawable.generation_3
                PredefinedHandwritingProfileId.PROFILE_4 -> R.drawable.generation_4
                PredefinedHandwritingProfileId.PROFILE_5 -> R.drawable.generation_5
                PredefinedHandwritingProfileId.PROFILE_6 -> R.drawable.generation_6
                PredefinedHandwritingProfileId.PROFILE_7 -> R.drawable.generation_7
                PredefinedHandwritingProfileId.PROFILE_8 -> R.drawable.generation_8
                PredefinedHandwritingProfileId.PROFILE_9 -> R.drawable.generation_9
                PredefinedHandwritingProfileId.PROFILE_10 -> R.drawable.generation_10
                PredefinedHandwritingProfileId.PROFILE_11 -> R.drawable.generation_11
                PredefinedHandwritingProfileId.PROFILE_12 -> R.drawable.generation_12
                PredefinedHandwritingProfileId.PROFILE_13 -> R.drawable.generation_13
                PredefinedHandwritingProfileId.PROFILE_14 -> R.drawable.generation_14
            })
            profileImage.visibility = View.VISIBLE
            profileText.visibility = View.GONE
        }

        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
        return getView(position, convertView, parent)
    }
}