/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.app.common.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

import com.myscript.iink.app.common.R;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * This activity displays an error message when an uncaught exception is thrown within an activity
 * that installed the associated exception handler. Since this application targets developers it's
 * better to clearly explain what happened. The code is inspired by:
 * https://trivedihardik.wordpress.com/2011/08/20/how-to-avoid-force-close-error-in-android/
 */
public class ErrorActivity extends AppCompatActivity {

    private static final String TAG = ErrorActivity.class.toString();
    private static final String ERR_TITLE = "err_title@" + TAG;
    private static final String ERR_MESSAGE = "err_message@" + TAG;

    public static void setExceptionHandler(Context context) {
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(context));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_error);

        TextView tvErrorTitle = findViewById(R.id.tv_error_title);
        tvErrorTitle.setText(getIntent().getStringExtra(ERR_TITLE));
        TextView tvErrorMessage = findViewById(R.id.tv_error_message);
        tvErrorMessage.setText(getIntent().getStringExtra(ERR_MESSAGE));
        tvErrorMessage.setMovementMethod(new ScrollingMovementMethod());
    }

    private static class ExceptionHandler implements Thread.UncaughtExceptionHandler {

        private final Context context;

        ExceptionHandler(Context context) {
            this.context = context;
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            // get message from the root cause.
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            String message = root.getMessage();

            // print stack trace.
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            String trace = writer.toString();

            // launch the error activity.
            Intent intent = new Intent(context, ErrorActivity.class);
            intent.putExtra(ERR_TITLE, message);
            intent.putExtra(ERR_MESSAGE, trace);
            context.startActivity(intent);

            // kill the current activity.
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        }
    }
}
