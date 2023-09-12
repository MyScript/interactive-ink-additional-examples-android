/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.assessment.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.myscript.iink.samples.assessment.R
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * This activity displays an error message when an uncaught exception is thrown within an activity
 * that installed the associated exception handler. Since this application targets developers it's
 * better to clearly explain what happened. The code is inspired by:
 * https://trivedihardik.wordpress.com/2011/08/20/how-to-avoid-force-close-error-in-android/
 */

/* important do not forget to add the activity in you  manifest
<activity android:name="com.myscript.iink.app.common.activities.ErrorActivity"/>
 */

class ErrorActivity : AppCompatActivity() {
    companion object {
        private val TAG = ErrorActivity::class.java.toString()
        private val ERR_TITLE = "err_title@$TAG"
        private val ERR_MESSAGE = "err_message@$TAG"

        @JvmStatic
        fun setExceptionHandler(context: Context) {
            Thread.setDefaultUncaughtExceptionHandler(ExceptionHandler(context))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_error)
        val tvErrorTitle = findViewById<TextView>(R.id.tv_error_title)
        tvErrorTitle.text = intent.getStringExtra(ERR_TITLE)

        val tvErrorMessage = findViewById<TextView>(R.id.tv_error_message)
        tvErrorMessage.text = intent.getStringExtra(ERR_MESSAGE)
        tvErrorMessage.movementMethod = ScrollingMovementMethod()

        findViewById<Button>(R.id.exit_button).setOnClickListener {
            finishAffinity()
            finishAndRemoveTask()
            moveTaskToBack(true)
            exitProcess(-1)
        }
    }

    private class ExceptionHandler(private val context: Context) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            // get message from the root cause.
            var root: Throwable? = e
            while (root!!.cause != null) {
                root = root.cause
            }
            val message = root.message

            // print stack trace.
            val writer = StringWriter()
            e.printStackTrace(PrintWriter(writer))
            val trace = writer.toString()

            // launch the error activity.
            val intent = Intent(context, ErrorActivity::class.java)
            intent.putExtra(ERR_TITLE, message)
            intent.putExtra(ERR_MESSAGE, trace)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK;
            context.startActivity(intent)
            // kill the current activity.
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }
}