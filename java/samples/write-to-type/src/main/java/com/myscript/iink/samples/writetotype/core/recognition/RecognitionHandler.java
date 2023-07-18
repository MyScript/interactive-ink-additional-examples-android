/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.writetotype.core.recognition;

import android.content.Context;
import android.graphics.RectF;
import android.util.DisplayMetrics;

import com.google.gson.Gson;
import com.myscript.iink.Configuration;
import com.myscript.iink.Engine;
import com.myscript.iink.MimeType;
import com.myscript.iink.Recognizer;
import com.myscript.iink.graphics.Point;
import com.myscript.iink.samples.writetotype.core.WriteToTypeWidget.RecognitionResult;
import com.myscript.iink.samples.writetotype.core.WriteToTypeWidget.TextResult;
import com.myscript.iink.samples.writetotype.core.inkcapture.StrokePoint;
import com.myscript.iink.samples.writetotype.core.recognition.jiixmodel.JiixGesture;
import com.myscript.iink.samples.writetotype.core.recognition.jiixmodel.item.JiixBoundingBox;
import com.myscript.iink.samples.writetotype.core.recognition.jiixmodel.JiixText;
import com.myscript.iink.samples.writetotype.core.recognition.jiixmodel.item.JiixWord;
import com.myscript.iink.samples.writetotype.core.recognition.strokemodel.CustomPath;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class RecognitionHandler
{
  // Constants for the recognizer type
  private static final String RECOGNIZER_TYPE_TEXT = "Text";
  private static final String RECOGNIZER_TYPE_GESTURE = "Gesture";

  private static final float INCH_IN_MILLIMETER = 25.4f;

  private static final float COMMA_HEIGHT_RATIO = 0.5f;
  private static final String CHARACTER_DOT = ".";
  private static final String CHARACTER_COMMA = ",";

  /** Interface definition for callbacks invoked when recognizing input strokes. */
  public interface OnRecognizedListener
  {
    void onRecognitionResult(@NonNull RecognitionResult recognitionResult, boolean isCommitted, @NonNull String debug);

    void onError(@NonNull String message);
  }

  private OnRecognizedListener mOnRecognizedListener = null;

  private final Engine mEngine;
  private Recognizer mTextRecognizer;
  private Recognizer mGestureRecognizer;

  private final CustomPath mPath = new CustomPath();

  private JiixBoundingBox mWordBoundingBox = new JiixBoundingBox();

  private final float mScaleX;
  private final float mScaleY;

  private Thread mRecognitionThread;

  // Flags for control recognition thread
  private boolean mIsRecognitionThreadAlive;
  private boolean mIsResultAvailable;
  private boolean mShouldCommit;
  private boolean mShouldClear;

  private int mCurrentPointerId = -1;
  private StrokePoint.EventType mCurrentPointerEvent;

  // Variables to control feeding strokes to Gesture/Text recognizer
  private int mStrokeCount = 0;
  private String mLastGestureType = JiixGesture.GESTURE_TYPE_EMPTY;

  // --------------------------------------------------------------------------
  // Constructor

  /** Constructor */
  public RecognitionHandler(@NonNull Context context, @NonNull final Engine engine)
  {
    mEngine = engine;

    DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
    mScaleX = INCH_IN_MILLIMETER / displayMetrics.xdpi;
    mScaleY = INCH_IN_MILLIMETER / displayMetrics.ydpi;

    mTextRecognizer = mEngine.createRecognizer(mScaleX, mScaleY, RECOGNIZER_TYPE_TEXT);
    mGestureRecognizer = mEngine.createRecognizer(mScaleX, mScaleY, RECOGNIZER_TYPE_GESTURE);

    startRecognitionThread();
  }

  // --------------------------------------------------------------------------
  // Public methods

  /** Register callbacks to be invoked when recognizing input strokes. */
  public void setOnRecognizedListener(@Nullable OnRecognizedListener onRecognizedListener)
  {
    mOnRecognizedListener = onRecognizedListener;
  }

  /** Configure recognition with this method, otherwise, 'en_US' will be set by default. */
  public void setLanguage(@NonNull final String language)
  {
    if (mEngine != null)
    {
      Configuration conf = mEngine.getConfiguration();
      assert (conf != null) : "Not able to retrieve configuration from mEngine.";

      conf.setString("lang", language);

      destroy();
      mTextRecognizer = mEngine.createRecognizer(mScaleX, mScaleY, RECOGNIZER_TYPE_TEXT);
      mGestureRecognizer = mEngine.createRecognizer(mScaleX, mScaleY, RECOGNIZER_TYPE_GESTURE);

      startRecognitionThread();
    }
  }

  //
  // For following event registering methods - pointerDown, pointerMove and pointerUp.
  //
  // Variables <code>mStrokeCount</code> and <code>mLastGestureType</code> are used for stroke injection management.
  // - Text Recognizer:
  //   All pointers will be injected to the Text Recognizer.
  // - Gesture Recognizer:
  //   As soon as the first pointer down, mStrokeCount became 1, and it is increased for every pointer down.
  //   Pointers will be injected to Gesture Recognizer when mStrokeCount < 2 (which mean only for the first stroke).
  //   There is an exception, <code>double-tap</code> gesture requires 2 strokes, and it is always preceded by <code>tap</code> gesture.
  //   So, as an exception, if the preceded gesture is <code>tap</code> (even if mStrokeCount >= 2), pointers are injected to Gesture Recognizer.
  //

  /** Register pointer down event to iink recognizer. */
  public void pointerDown(@NonNull StrokePoint point, int pointerId)
  {
    try
    {
      if (mCurrentPointerId == -1 && !mShouldClear)
      {
        mStrokeCount++;
        mPath.moveTo(point.x, point.y);

        mTextRecognizer.pointerDown(point.x, point.y, point.t, point.p);
        if (mStrokeCount < 2 || mLastGestureType.equals(JiixGesture.GESTURE_TYPE_TAP))
        {
          mGestureRecognizer.pointerDown(point.x, point.y, point.t, point.p);
        }

        mIsResultAvailable = true;

        mCurrentPointerEvent = point.eventType;

        mCurrentPointerId = pointerId;
      }
    }
    catch (Exception e)
    {
      // ignore spurious invalid touch events
    }
  }

  /** Register pointer move event to iink recognizer. */
  public void pointerMove(@NonNull StrokePoint point, int pointerId)
  {
    try
    {
      if (mCurrentPointerId == pointerId)
      {
        mPath.lineTo(point.x, point.y);

        mTextRecognizer.pointerMove(point.x, point.y, point.t, point.p);
        if (mStrokeCount < 2 || mLastGestureType.equals(JiixGesture.GESTURE_TYPE_TAP))
        {
          mGestureRecognizer.pointerMove(point.x, point.y, point.t, point.p);
        }

        mCurrentPointerEvent = point.eventType;
      }
    }
    catch (Exception e)
    {
      // ignore spurious invalid touch events
    }
  }

  /** Register pointer up event to iink recognizer. */
  public void pointerUp(@NonNull StrokePoint point, int pointerId)
  {
    try
    {
      if (mCurrentPointerId == pointerId)
      {
        mPath.lineTo(point.x, point.y);

        mTextRecognizer.pointerUp(point.x, point.y, point.t, point.p);
        if (mStrokeCount < 2 || mLastGestureType.equals(JiixGesture.GESTURE_TYPE_TAP))
        {
          mGestureRecognizer.pointerUp(point.x, point.y, point.t, point.p);
        }

        mCurrentPointerEvent = point.eventType;

        mCurrentPointerId = -1;
      }
    }
    catch (Exception e)
    {
      // ignore spurious invalid touch events
    }
  }

  /** Commit timeout expired, so that, make recognition result to take it into account. */
  public void commitRecognition()
  {
    mShouldCommit = true;
    mIsResultAvailable = false;
  }

  /**
   *  Clear current session of recognition.
   *  It's usually called when Input focus changed or to reset
   *  previous text bounding box to determine dot recognition.
   **/
  public void clearSession()
  {
    mWordBoundingBox = new JiixBoundingBox();
  }

  /** Clear iink recognizers. */
  public void clear()
  {
    mShouldClear = true;
    mIsResultAvailable = false;
  }

  /** Destroy iink recognizers. */
  public void destroy()
  {
    stopRecognitionThread();

    if (mTextRecognizer != null)
    {
      mTextRecognizer.close();
      mTextRecognizer = null;
    }

    if (mGestureRecognizer != null)
    {
      mGestureRecognizer.close();
      mGestureRecognizer = null;
    }
  }

  // --------------------------------------------------------------------------
  // Private methods used in this class

  private void startRecognitionThread()
  {
    mIsRecognitionThreadAlive = true;

    mIsResultAvailable = false;
    mShouldCommit = false;
    mShouldClear = false;

    mRecognitionThread = new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        while (mIsRecognitionThreadAlive)
        {
          try
          {
            Thread.sleep(10);

            if (mShouldCommit && !mShouldClear)
            {
              mTextRecognizer.waitForIdle();
              mGestureRecognizer.waitForIdle();
              doRecognition(true);

              clearRecognizers();
              mShouldCommit = false;
            }

            if (mIsResultAvailable && !mShouldClear)
            {
              doRecognition(false);
              mIsResultAvailable = !(mTextRecognizer.isIdle() && mGestureRecognizer.isIdle());
            }

            if (mShouldClear)
            {
              clearRecognizers();
              mShouldClear = false;
            }
          }
          catch (Exception e)
          {
            e.printStackTrace();
          }
        }
      }
    });

    mRecognitionThread.setPriority(Thread.MAX_PRIORITY);
    mRecognitionThread.start();
  }

  private void stopRecognitionThread()
  {
    try
    {
      clearRecognizers();

      mIsRecognitionThreadAlive = false;
      mRecognitionThread.join();
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
  }

  private void doRecognition(final boolean isCommitted)
  {
    if (mOnRecognizedListener != null)
    {
      boolean isRecognizerIdle = mTextRecognizer.isIdle() && mGestureRecognizer.isIdle();

      String gestureJiix = "";
      String gestureType = JiixGesture.GESTURE_TYPE_EMPTY;

      if (mStrokeCount < 2 || mLastGestureType.equals(JiixGesture.GESTURE_TYPE_TAP))
      {
        gestureJiix = mGestureRecognizer.getResult(MimeType.JIIX);
        gestureType = parseGestureJiix(gestureJiix);
      }

      TextResult textResult = parseTextJiix(mTextRecognizer.getResult(MimeType.JIIX));

      if (gestureType != null)
      {
        RecognitionResult result = combineTextAndGestureResult(isRecognizerIdle, textResult, gestureType);
        mOnRecognizedListener.onRecognitionResult(result, isCommitted, gestureJiix);
        mLastGestureType = gestureType;
      }
    }
  }

  /**
   *  Following codes will parse the exported JIIX of Text Recognizer
   **/
  @Nullable
  private TextResult parseTextJiix(@Nullable final String jiixString)
  {
    TextResult result = new TextResult();

    if (notifyError(jiixString == null, "ERROR: No exported result from TEXT."))
    {
      clear();
      return null;
    }

    try
    {
      JiixText textResult = new Gson().fromJson(jiixString, JiixText.class);

      if (textResult != null && textResult.type.equals(RECOGNIZER_TYPE_TEXT) && textResult.words.size() > 0)
      {
        final String label = textResult.label;

        if (label.length() == 1)
        {
          JiixWord word = textResult.words.get(0);
          List<String> candidates = word.candidates;
          result.candidates = candidates;

          if (candidates.get(0).equals(CHARACTER_DOT))
          {
            result.label = candidates.get(0);
          }
          else
          {
            JiixBoundingBox boundingBox = word.boundingBox;
            if (candidates.contains(CHARACTER_COMMA) &&
                (boundingBox.height <= (mWordBoundingBox.height * COMMA_HEIGHT_RATIO)))
            {
              result.label = CHARACTER_COMMA;
            }
            else
            {
              result.label = candidates.get(0);
            }
          }

          result.boundingBox = getRectFromBoundingBox(word.boundingBox);
        }
        else
        {
          result.label = label;
          JiixWord word = getLongestWord(textResult.words);

          if ((word != null) && (word.boundingBox != null))
          {
            JiixBoundingBox boundingBox = word.boundingBox;
            mWordBoundingBox.x = boundingBox.x;
            mWordBoundingBox.y = boundingBox.y;
            mWordBoundingBox.width = boundingBox.width;
            mWordBoundingBox.height = boundingBox.height;

            result.boundingBox = getRectFromBoundingBox(boundingBox);
          }
        }
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }

    return result;
  }

  /**
   *  Following codes will parse the exported JIIX of Gesture Recognizer.
   **/
  @Nullable
  private String parseGestureJiix(@Nullable final String jiixString)
  {
    if (notifyError(jiixString == null, "ERROR: No exported result from GESTURE."))
    {
      return null;
    }

    String gestureType = null;

    try
    {
      JiixGesture gestureResult = new Gson().fromJson(jiixString, JiixGesture.class);

      if (gestureResult != null && gestureResult.type.equals(RECOGNIZER_TYPE_GESTURE) && gestureResult.gestures.size() > 0)
      {
        JiixGesture.Gesture gesture = gestureResult.gestures.get(0);
        gestureType = gesture.type;
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }

    return gestureType;
  }

  @NonNull
  private RecognitionResult combineTextAndGestureResult(final boolean isRecognizerIdle, TextResult textResult, String gestureType)
  {
    RecognitionResult recognitionResult = new RecognitionResult(isRecognizerIdle);
    recognitionResult.isPointerUp = (mCurrentPointerEvent == StrokePoint.EventType.EVENT_TYPE_UP);
    recognitionResult.strokeRect = mPath.getBoundingRect();

    recognitionResult.gestureType = gestureType;
    recognitionResult.points = mPath.getPoints();

    recognitionResult.textResult = textResult;

    return recognitionResult;
  }

  @NonNull
  private RectF getRectFromBoundingBox(@NonNull JiixBoundingBox boundingBox)
  {
    final float x = boundingBox.x / mScaleX;
    final float y = boundingBox.y / mScaleY;
    final float width = boundingBox.width / mScaleX;
    final float height = boundingBox.height / mScaleY;

    final Point topLeft = new Point(x, y);
    final Point bottomRight = new Point(x + width, y + height);

    return new RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y);
  }

  @Nullable
  private JiixWord getLongestWord(@NonNull List<JiixWord> words)
  {
    JiixWord longestWord = null;

    for (JiixWord word : words)
    {
      if (longestWord == null || longestWord.label.length() < word.label.length())
      {
        longestWord = word;
      }
    }

    return longestWord;
  }

  private boolean notifyError(final boolean error, @NonNull final String message)
  {
    if (error && mOnRecognizedListener != null)
    {
      mOnRecognizedListener.onError(message);
    }

    return error;
  }

  private void clearRecognizers()
  {
    mPath.reset();
    mStrokeCount = 0;
    mLastGestureType = JiixGesture.GESTURE_TYPE_EMPTY;
    mCurrentPointerId = -1;

    try
    {
      mTextRecognizer.clear();
      mGestureRecognizer.clear();

      mTextRecognizer.waitForIdle();
      mGestureRecognizer.waitForIdle();
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
  }
}
