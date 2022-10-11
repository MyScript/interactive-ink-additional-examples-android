package com.myscript.iink.app.common.utils

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.myscript.iink.app.common.R

class CustomProgressDialog(ctx:Context) : Dialog(ctx){
    init{
        val wlmp: WindowManager.LayoutParams = window!!.attributes

        wlmp.gravity = Gravity.CENTER_HORIZONTAL
        window!!.attributes = wlmp
        setTitle(null)
        setCancelable(false)
        setOnCancelListener(null)
        val view: View = LayoutInflater.from(context).inflate(
            R.layout.custom_progress_dlg, null
        )
        setContentView(view)
    }
}