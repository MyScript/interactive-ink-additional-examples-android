/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.writetotype;

import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.myscript.iink.Engine;
import com.myscript.iink.samples.writetotype.core.inkcapture.InkCaptureView;
import com.myscript.iink.samples.writetotype.core.inkcapture.StrokePoint;
import com.myscript.iink.samples.writetotype.core.recognition.RecognitionHandler;
import com.myscript.iink.samples.writetotype.core.recognition.jiixmodel.JiixGesture;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class WriteToTypeManager implements InkCaptureView.OnStrokeListener, RecognitionHandler.OnRecognizedListener
{
  private static final int COMMIT_TIMEOUT = 500;

  /**
   * To store text recognition result by Text Recognizer.
   */
  public static class TextResult
  {
    public String label;
    public List<String> candidates;
    public RectF boundingBox;

    public TextResult()
    {
      label = "";
      candidates = new ArrayList<>();
    }
  }

  /**
   * To store recognition results of both, Text and Gesture Recognizer, and other necessary information.
   */
  public static class RecognitionResult
  {
    /** State of touch event, <code>ture</code> if POINTER_UP, otherwise <code>false</code>. */
    public boolean isPointerUp;
    /** State of recognizer, <code>true</code> if IDLE, otherwise <code>false</code>. */
    public boolean isRecognizerIdle;

    /** Stroke bounding rect from raw path information, not from recognizer. */
    public RectF strokeRect;
    /** Last stroke points of raw path information, it will use to find exact location of vertical stroke. */
    public List<PointF> points;

    /** Result of Gesture Recognizer. */
    public String gestureType;
    /** Result of Text Recognizer. */
    public TextResult textResult;

    public RecognitionResult(final boolean isRecognizerIdle)
    {
      this.isRecognizerIdle = isRecognizerIdle;
    }
  }

  /** Interface definition for callback of recognition results */
  public interface OnWriteToTypeListener
  {
    boolean onText(@NonNull RecognitionResult recognitionResult, boolean isCommitted);
    boolean onTopBottom(@NonNull RecognitionResult recognitionResult, boolean isCommitted);
    boolean onBottomTop(@NonNull RecognitionResult recognitionResult, boolean isCommitted);
    boolean onLeftRight(@NonNull RecognitionResult recognitionResult, boolean isCommitted);
    boolean onRightLeft(@NonNull RecognitionResult recognitionResult, boolean isCommitted);
    boolean onScratch(@NonNull RecognitionResult recognitionResult, boolean isCommitted);
    boolean onSurround(@NonNull RecognitionResult recognitionResult, boolean isCommitted);
    boolean onSingleTap(@NonNull RecognitionResult recognitionResult, boolean isCommitted);
    boolean onDoubleTap(@NonNull RecognitionResult recognitionResult, boolean isCommitted);
    boolean onLongPress(@NonNull RecognitionResult recognitionResult, boolean isCommitted);
  }

  /** Interface definition for callbacks invoked when there is an error, or a debug message */
  public interface OnDebugListener
  {
    void onDebug(@NonNull String message);
    void onError(@NonNull String message);
  }

  private OnWriteToTypeListener mOnWriteToTypeListener = null;
  private OnDebugListener mOnDebugListener = null;

  @NonNull
  private final InkCaptureView mInkCaptureView;
  private RecognitionHandler mRecognitionHandler = null;

  private Timer mCommitTimer = null;
  private int mCommitTimeout = COMMIT_TIMEOUT;

  // --------------------------------------------------------------------------
  // Constructor

  /** Constructor */
  public WriteToTypeManager(@NonNull InkCaptureView inkCaptureView) {
    mInkCaptureView = inkCaptureView;
    mInkCaptureView.setOnStrokeListener(this);
  }

  // --------------------------------------------------------------------------
  // Public methods

  /** Register callbacks to be invoked when . */
  public void setOnWriteToTypeListener(@Nullable OnWriteToTypeListener onWriteToTypeListener)
  {
    mOnWriteToTypeListener = onWriteToTypeListener;
  }

  /** Register callbacks to be invoked when . */
  public void setOnDebugListener(@Nullable OnDebugListener onDebugListener)
  {
    mOnDebugListener = onDebugListener;
  }

  public void setIInkEngine(@NonNull final Engine engine)
  {
    mRecognitionHandler = new RecognitionHandler(engine, mInkCaptureView.getResources().getDisplayMetrics());
    mRecognitionHandler.setOnRecognizedListener(this);
  }

  public void setLanguage(@NonNull final String language)
  {
    if (mRecognitionHandler != null)
    {
      mRecognitionHandler.setLanguage(language);
    }
  }

  public void setCommitTimeout(final int commitTimeout)
  {
    mCommitTimeout = commitTimeout;
  }

  /**
   * A method to set pen input only mode (Pen means an active stylus pen).
   * You are able to use it when you don't want to allow finger input.
   * It is 'false' by default which means both of pen and finger are available.
   * Note: a passive stylus acts as finger input.
   *
   * @param activeStylusOnly only pen input is available if it's true.
   */
  public void setActiveStylusOnly(final boolean activeStylusOnly)
  {
    mInkCaptureView.setActiveStylusOnly(activeStylusOnly);
  }

  public void clearSession()
  {
    if (mRecognitionHandler != null)
    {
      mRecognitionHandler.clearSession();
    }
  }

  public void destroy()
  {
    if (mRecognitionHandler != null)
    {
      mRecognitionHandler.destroy();
    }
  }

  // --------------------------------------------------------------------------
  // Implementation of InkCaptureView.OnInkCapturedListener

  @Override
  public void onStrokeBegin(@NonNull InkCaptureView inkCaptureView, @NonNull StrokePoint point, int pointerId)
  {
    if (mRecognitionHandler != null)
    {
      mRecognitionHandler.pointerDown(point, pointerId);
    }

    killCommitTimer();
  }

  @Override
  public void onStrokeMove(@NonNull InkCaptureView inkCaptureView, @NonNull StrokePoint point, int pointerId)
  {
    if (mRecognitionHandler != null)
    {
      mRecognitionHandler.pointerMove(point, pointerId);
    }
  }

  @Override
  public void onStrokeEnd(@NonNull InkCaptureView inkCaptureView, @NonNull StrokePoint point, int pointerId)
  {
    if (mRecognitionHandler != null)
    {
      mRecognitionHandler.pointerUp(point, pointerId);
    }

    startCommitTimer();
  }

  @Override
  public void onStrokeCancel(@NonNull InkCaptureView inkCaptureView)
  {
    mRecognitionHandler.pointerCancel();
    cancelRecognition();
  }

  // --------------------------------------------------------------------------
  // Implementation of RecognitionHandler.OnRecognizedListener

  /**
   * onRecognitionResult is recognition result callback from {@link RecognitionHandler}.
   *
   * @param recognitionResult A structure of recognition result, see {@link RecognitionResult}.
   * @param isCommitted <code>true</code> when a commit has been taken into account to current recognition result.
   * @param debug JIIX string of Gesture result for debug purpose.
   */
  @Override
  public void onRecognitionResult(@NonNull RecognitionResult recognitionResult, final boolean isCommitted, @NonNull String debug)
  {
    if (mOnWriteToTypeListener != null)
    {
      mInkCaptureView.post(() -> {
        boolean overlapped = false;
        boolean isError = false;

        switch (recognitionResult.gestureType)
        {
          case JiixGesture.GESTURE_TYPE_EMPTY:
          case JiixGesture.GESTURE_TYPE_NONE:
            overlapped = mOnWriteToTypeListener.onText(recognitionResult, isCommitted);
            break;
          case JiixGesture.GESTURE_TYPE_TOP_BOTTOM:
            overlapped = mOnWriteToTypeListener.onTopBottom(recognitionResult, isCommitted);
            break;
          case JiixGesture.GESTURE_TYPE_BOTTOM_TOP:
            overlapped = mOnWriteToTypeListener.onBottomTop(recognitionResult, isCommitted);
            break;
          case JiixGesture.GESTURE_TYPE_LEFT_RIGHT:
            overlapped = mOnWriteToTypeListener.onLeftRight(recognitionResult, isCommitted);
            break;
          case JiixGesture.GESTURE_TYPE_RIGHT_LEFT:
            overlapped = mOnWriteToTypeListener.onRightLeft(recognitionResult, isCommitted);
            break;
          case JiixGesture.GESTURE_TYPE_SCRATCH:
            overlapped = mOnWriteToTypeListener.onScratch(recognitionResult, isCommitted);
            break;
          case JiixGesture.GESTURE_TYPE_SURROUND:
            overlapped = mOnWriteToTypeListener.onSurround(recognitionResult, isCommitted);
            break;
          case JiixGesture.GESTURE_TYPE_TAP:
            overlapped = mOnWriteToTypeListener.onSingleTap(recognitionResult, isCommitted);
            break;
          case JiixGesture.GESTURE_TYPE_DOUBLE_TAP:
            overlapped = mOnWriteToTypeListener.onDoubleTap(recognitionResult, isCommitted);
            break;
          case JiixGesture.GESTURE_TYPE_LONG_PRESS:
            overlapped = mOnWriteToTypeListener.onLongPress(recognitionResult, isCommitted);
            break;
          default:
            isError = true;
            if (mOnDebugListener != null)
            {
              mOnDebugListener.onError("Invalid gesture type\n\n" + debug);
            }
            break;
        }

        if (!isError)
        {
          debugMessage(("Event State: " + (isCommitted ? "COMMITTED" : (recognitionResult.isRecognizerIdle ? "IDLE" : "BUSY"))) + "\n" +
                  ("Overlapped: " + (overlapped ? "YES" : "NO"))  + "\n" +
                  ("Gesture Type: " + recognitionResult.gestureType) + "\n" +
                  ("Text result: " + recognitionResult.textResult.label + ((recognitionResult.textResult.candidates == null || recognitionResult.textResult.candidates.isEmpty()) ? "" : "  " + recognitionResult.textResult.candidates)) + "\n" +
                  ("Stroke rect: " + recognitionResult.strokeRect.toShortString()) + "\n\n" +
                  debug);
        }
      });
    }
  }

  /**
   * onError is invoked when an error happens during processing of recognition at {@link RecognitionHandler}.
   *
   * @param message error message.
   */
  @Override
  public void onError(@NonNull final String message)
  {
    if (mOnDebugListener != null)
    {
      mInkCaptureView.post(() -> mOnDebugListener.onError(message));
    }
  }

  public void cancelRecognition()
  {
    if (mRecognitionHandler != null)
    {
      mRecognitionHandler.clear();
    }

    clearInkCaptureView(false);
    killCommitTimer();
  }

  // --------------------------------------------------------------------------
  // Internal methods of WriteToTypeWidget class

  private void commitRecognition()
  {
    if (mRecognitionHandler != null)
    {
      mRecognitionHandler.commitRecognition();
    }

    clearInkCaptureView(true);
  }

  private void killCommitTimer()
  {
    try
    {
      if (mCommitTimer != null)
      {
        mCommitTimer.cancel();
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
  }

  private void startCommitTimer()
  {
    TimerTask task = new TimerTask()
    {
      @Override
      public void run()
      {
        commitRecognition();
        if (mCommitTimer != null)
        {
          mCommitTimer.purge();
        }
      }
    };

    mCommitTimer = new Timer();
    mCommitTimer.schedule(task, mCommitTimeout);
  }

  private void clearInkCaptureView(final boolean animate)
  {
    mInkCaptureView.post(() -> mInkCaptureView.clearStrokes(animate));
  }

  private void debugMessage(@NonNull final String message)
  {
    if (mOnDebugListener != null)
    {
      mOnDebugListener.onDebug(message);
    }
  }
}
