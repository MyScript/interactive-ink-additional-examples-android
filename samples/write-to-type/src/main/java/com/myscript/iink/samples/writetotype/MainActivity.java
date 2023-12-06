/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.writetotype;

import android.content.Context;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.myscript.iink.Configuration;
import com.myscript.iink.Engine;
import com.myscript.iink.samples.writetotype.core.inkcapture.InkCaptureView;
import com.myscript.iink.samples.writetotype.im.InputMethodEmulator;
import com.myscript.iink.samples.writetotype.utils.ErrorActivity;

public class MainActivity extends AppCompatActivity implements WriteToTypeManager.OnDebugListener
{
  private static final float INCH_IN_MILLIMETER = 25.4f;

  private CustomViewGroup mViewGroup;
  private WriteToTypeManager mWriteToTypeManager;
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
    conf.setStringArray("recognizer.configuration-manager.search-path", new String[]{ confDir });
    setSuperimposed(true);

    mLogView = findViewById(R.id.text_view_log);
    mLogView.setMovementMethod(new ScrollingMovementMethod());

    mViewGroup = findViewById(R.id.custom_view_group);
    InkCaptureView inkCaptureView = findViewById(R.id.ink_capture_view);
    mWriteToTypeManager = new WriteToTypeManager(inkCaptureView);
    mWriteToTypeManager.setOnDebugListener(this);
    mWriteToTypeManager.setActiveStylusOnly(false);

    // Configuring iink in post runnable may not need.
    // It will be replaced by just calling of 'setIInkEngine()' and 'setLanguage' later on.
    inkCaptureView.post(() -> {
      mWriteToTypeManager.setIInkEngine(mEngine);
      mWriteToTypeManager.setLanguage("en_US");
      mWriteToTypeManager.setCommitTimeout(500);

      final float scaleY = INCH_IN_MILLIMETER / getResources().getDisplayMetrics().ydpi;
      Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
      mInputMethod = new InputMethodEmulator(mWriteToTypeManager, mViewGroup, scaleY, vibrator);

      mInputMethod.setDebugView((DebugView) findViewById(R.id.debug_view));  // DEBUG ONLY
      mInputMethod.setDefaultEditText(0);
    });
  }

  private void setSuperimposed(boolean enable)
  {
    mEngine.getConfiguration().setString("recognizer.text.configuration.name", enable ? "text-superimposed" : "text");
    if (mWriteToTypeManager != null)
    {
      mWriteToTypeManager.resetTextRecognizer();
    }
  }

  private boolean isSuperimposed()
  {
    return "text-superimposed".equals(mEngine.getConfiguration().getString("recognizer.text.configuration.name"));
  }

  @Override
  protected void onDestroy()
  {
    mWriteToTypeManager.destroy();

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
      boolean isDebug = !mInputMethod.isDebug();
      item.setTitle(isDebug ? R.string.menu_debug_on : R.string.menu_debug_off);
      mInputMethod.setDebug(isDebug);
    }
    else if (item.getItemId() == R.id.action_toggle_recognizer)
    {
      boolean isSuperimposed = !isSuperimposed();
      item.setTitle(isSuperimposed ? R.string.menu_recognizer_superimposed_on : R.string.menu_recognizer_superimposed_off);
      setSuperimposed(isSuperimposed);
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