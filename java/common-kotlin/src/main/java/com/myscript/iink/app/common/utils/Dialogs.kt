// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.app.common.utils

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.EditText
import androidx.annotation.StringRes
import com.myscript.iink.app.common.R

fun Context.launchSingleChoiceDialog(
    @StringRes titleRes: Int,
    items: List<String>,
    selectedIndex: Int = -1,
    onItemSelected: (selected: Int) -> Unit,
    onCancelSelected: DialogInterface.OnClickListener? =null
){
    var newIndex = selectedIndex
    AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setSingleChoiceItems(
                    items.toTypedArray(),
                    selectedIndex
            ) { _, which ->
                newIndex = which
            }
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                if (newIndex in items.indices) {
                    onItemSelected(newIndex)
                }
            }
            .setNegativeButton(R.string.dialog_cancel, onCancelSelected)
            .show()
}

fun Context.launchActionChoiceDialog(
    @StringRes titleRes: Int,
    items: List<String>,
    onItemSelected: (selected: Int) -> Unit
)
{
    AlertDialog.Builder(this)
        .setTitle(titleRes)
        .setItems(items.toTypedArray()) { _, which ->
            if (which in items.indices) {
                onItemSelected(which)
            }
        }
        .show()
}
fun Context.launchActionChoiceDialog(
        items: List<String>,
        onItemSelected: (selected: Int) -> Unit
) {
    AlertDialog.Builder(this)
            .setItems(items.toTypedArray()) { _, which ->
                if (which in items.indices) {
                    onItemSelected(which)
                }
            }
            .show()
}

fun Context.launchTextBlockInputDialog(onInputDone: (text: String) -> Unit) {
    val editTextLayout = LayoutInflater.from(this).inflate(R.layout.editor_text_input_layout, null)
    val editText = editTextLayout.findViewById<EditText>(R.id.editor_text_input)
    val builder = AlertDialog.Builder(this)
            .setView(editTextLayout)
            .setTitle(R.string.editor_dialog_insert_text_title)
            .setPositiveButton(R.string.editor_dialog_insert_text_action) { _, _ ->
                val text = editText.text.toString()
                if (text.isNotBlank()) {
                    onInputDone(text)
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
    editText.requestFocus()
    builder.show()
}