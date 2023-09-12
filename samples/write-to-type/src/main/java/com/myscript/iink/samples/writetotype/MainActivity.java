/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.writetotype;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;

import com.myscript.iink.Configuration;
import com.myscript.iink.Engine;
import com.myscript.iink.samples.writetotype.core.WriteToTypeWidget;
import com.myscript.iink.samples.writetotype.im.InputMethodEmulator;
import com.myscript.iink.samples.writetotype.utils.ErrorActivity;

import java.io.File;

public class MainActivity extends AppCompatActivity implements WriteToTypeWidget.OnDebugListener
{
  private static final float INCH_IN_MILLIMETER = 25.4f;

  private CustomViewGroup mViewGroup;
  private WriteToTypeWidget mWriteToTypeWidget;
  private InputMethodEmulator mInputMethod;
  private TextView mLogView;

  protected Engine mEngine;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    ErrorActivity.setExceptionHandler(getApplicationContext());

    // To disable popping-up soft-keyboard
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM, WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

    mEngine = IInkApplication.getEngine();

    // configure recognition
    Configuration conf = mEngine.getConfiguration();
    String confDir = "zip://" + getPackageCodePath() + "!/assets/conf";
    conf.setStringArray("configuration-manager.search-path", new String[]{ confDir });
    String tempDir = getFilesDir().getPath() + File.separator + "tmp";
    conf.setString("content-package.temp-folder", tempDir);

    mLogView = findViewById(R.id.text_view_log);
    mLogView.setMovementMethod(new ScrollingMovementMethod());

    mViewGroup = findViewById(R.id.custom_view_group);
    mWriteToTypeWidget = findViewById(R.id.writetotype);
    mWriteToTypeWidget.setOnDebugListener(this);
    mWriteToTypeWidget.setActiveStylusOnly(false);

    // Configuring iink in post runnable may not need.
    // It will be replaced by just calling of 'setIInkEngine()' and 'setLanguage' later on.
    mWriteToTypeWidget.post(new Runnable()
    {
      @Override
      public void run()
      {
        mWriteToTypeWidget.setIInkEngine(mEngine);
        mWriteToTypeWidget.setLanguage("en_US");
        mWriteToTypeWidget.setCommitTimeout(500);

        final float scaleY = INCH_IN_MILLIMETER / getResources().getDisplayMetrics().ydpi;
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mInputMethod = new InputMethodEmulator(mWriteToTypeWidget, mViewGroup, scaleY, vibrator);

        mInputMethod.setDebugView((DebugView) findViewById(R.id.debug_view));  // DEBUG ONLY
        mInputMethod.setDefaultEditText(0);
      }
    });
  }

  @Override
  protected void onDestroy()
  {
    mWriteToTypeWidget.destroy();

    // IInkApplication has the ownership, do not close here
    mEngine = null;
    super.onDestroy();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.activity_main, menu);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item)
  {
    if (item.getItemId() == R.id.action_reset)
    {
      mInputMethod.resetTextForEditText();
      mInputMethod.setDefaultEditText(0);
      mLogView.setText("");
    }

    if (item.getItemId() == R.id.action_debug_view)
    {
      item.setChecked(!item.isChecked());
      mInputMethod.setDebug(item.isChecked());
    }

    return super.onOptionsItemSelected(item);
  }

  // --------------------------------------------------------------------------
  // Implementation of WriteToTypeWidget.OnDebugListener

  @Override
  public void onDebug(@NonNull final String message)
  {
    mLogView.setText(message);
  }

  @Override
  public void onError(@NonNull final String message)
  {
    mLogView.setText(message);
  }
}