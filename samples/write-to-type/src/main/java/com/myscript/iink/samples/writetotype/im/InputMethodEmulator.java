/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.writetotype.im;

import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.EditText;

import com.myscript.iink.samples.writetotype.CustomViewGroup;
import com.myscript.iink.samples.writetotype.DebugView;
import com.myscript.iink.samples.writetotype.WriteToTypeManager;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class InputMethodEmulator implements WriteToTypeManager.OnWriteToTypeListener, CustomViewGroup.OnChangedListener
{
  private static final float OVERLAP_RATIO = 0.4f;
  private static final float BIGGER_HEIGHT_FACTOR = 3.0f;
  private static final float UNDERLINE_DISTANCE_IN_MILLIMETER = 5;
  private static final int SCRATCH_COLOR = 0xffcccccc; // it's light gray.

  private enum LineType {
    LINE_TYPE_VERTICAL,
    LINE_TYPE_HORIZONTAL
  }

  private final WriteToTypeManager mWriteToTypeManager;
  private final CustomViewGroup mViewGroup;
  private DebugView mDebugView = null;

  private EditText mEditText = null;
  private int mSelectionStart = -1;
  private int mSelectionEnd = -1;
  private int mDefaultHighlightColor;

  private final float mExtraDistanceInDpi;

  private final Vibrator mVibrator;
  private boolean mIsVibrating = false;

  private boolean mDebug;

  // --------------------------------------------------------------------------
  // Constructor

  /** Constructor. */
  public InputMethodEmulator(@NonNull final WriteToTypeManager writeToTypeManager, @NonNull final CustomViewGroup viewGroup, final float scaleY, @NonNull final Vibrator vibrator)
  {
    mWriteToTypeManager = writeToTypeManager;
    mWriteToTypeManager.setOnWriteToTypeListener(this);

    mViewGroup = viewGroup;
    mViewGroup.initIndexForEditText();
    mViewGroup.setOnChangedListener(this);

    mExtraDistanceInDpi = UNDERLINE_DISTANCE_IN_MILLIMETER / scaleY;
    mVibrator = vibrator;

    mDebug = false;
  }

  public void setDebugView(DebugView debugView)
  {
    mDebugView = debugView;
  }

  public void setDebug(final boolean debug)
  {
    mDebug = debug;
    mDebugView.setDebug(mDebug);
  }

  public boolean isDebug()
  {
    return mDebug;
  }

  public void setDefaultEditText(final int index)
  {
    EditText editText = mViewGroup.setFocus(index);
    if (editText != null)
    {
      mEditText = editText;
      mDefaultHighlightColor = mEditText.getHighlightColor();
      debugMethod(-1, -1, null, editText, null);
    }
  }

  public void resetTextForEditText()
  {
    mViewGroup.resetTextForEditText();
  }

  // --------------------------------------------------------------------------
  // Implementation of WriteToTypeWidget.OnWriteToTypeListener

  /**
   * onText is invoked when "NONE" gesture is recognized by Gesture Recognizer.
   * IMPLEMENTATION CHOICE:
   *     Gesture result "NONE" means no gesture detected.
   *     Currently, a behavior of onText is the exactly same as <code>onScratch</code>,
   *     but the action is postponed to pointer-up as "NONE" most probably mean Text input.
   *
   * @param recognitionResult A structure of recognition result, see {@link WriteToTypeManager.RecognitionResult}
   * @param isCommitted <code>true</code> when a commit has been taken into account to current recognition result.
   * @return <code>true</code> if Stroke bounding box is overlapped on existing text of EditText, <code>false</code> otherwise.
   */
  @Override
  public boolean onText(@NonNull final WriteToTypeManager.RecognitionResult recognitionResult, final boolean isCommitted)
  {
    if (recognitionResult.isPointerUp)
    {
      return onScratch(recognitionResult, isCommitted);
    }

    return false;
  }

  /**
   * onTopBottom is invoked when "TOP-BOTTOM" gesture is recognized by Gesture Recognizer.
   * IMPLEMENTATION CHOICE:
   *     Gesture result "TOP-BOTTOM" means a vertical line to bottom direction is detected.
   *     - if "top to bottom" stroke pass through existing text of EditText, it adds a space in stroke location
   *       (multiple spaces are not available),
   *     - otherwise, it adds recognized text in current cursor location.
   *
   * @param recognitionResult A structure of recognition result, see {@link WriteToTypeManager.RecognitionResult}
   * @param isCommitted <code>true</code> when a commit has been taken into account to current recognition result.
   * @return <code>true</code> if Stroke bounding box is overlapped on existing text of EditText, <code>false</code> otherwise.
   */
  public boolean onTopBottom(@NonNull final WriteToTypeManager.RecognitionResult recognitionResult, final boolean isCommitted)
  {
    boolean overlapped = false;

    if (recognitionResult.isPointerUp)
    {
      final RectF strokeRect = recognitionResult.strokeRect;
      checkFocusChangedWithExtraDistance(strokeRect, LineType.LINE_TYPE_VERTICAL);

      if (mEditText != null)
      {
        final float x = strokeRect.centerX();
        final float y = strokeRect.centerY();

        RectF intersect = new RectF(strokeRect);
        RectF textBounds = getTextBounds(x, y, mEditText);

        if (textBounds != null && mEditText.getText().length() != 0 &&
            ((intersect.intersect(textBounds) && isPassedThrough(strokeRect, textBounds)) || (isInDistanceOf(strokeRect, textBounds, LineType.LINE_TYPE_VERTICAL) && isCommitted)))
        {
          mWriteToTypeManager.cancelRecognition();
          final PointF center = getCenterOfLine(recognitionResult.points, textBounds);
          if (center.x != -1 && center.y != -1)
          {
            mViewGroup.setSpace(mEditText, center.x, center.y);
          }
          else
          {
            final float centerX = (strokeRect.left < textBounds.left) ? textBounds.left : textBounds.right;
            final float centerY = textBounds.centerY();
            mViewGroup.setSpace(mEditText, centerX, centerY);
          }

          overlapped = true;
        }
        else
        {
          setText(recognitionResult, isCommitted);
        }

        debugMethod(-1, -1, textBounds, mEditText, strokeRect);
      }
    }

    return overlapped;
  }

  /**
   * onBottomTop is invoked when "BOTTOM-TOP" gesture is recognized by Gesture Recognizer.
   * IMPLEMENTATION CHOICE:
   *     Gesture result "BOTTOM-TOP" means a vertical line to top direction is detected.
   *     - if "bottom to top" stroke pass through a space over text of EditText, it deletes the space of the stroke location,
   *     - otherwise, nothing happens.
   *
   * @param recognitionResult A structure of recognition result, see {@link WriteToTypeManager.RecognitionResult}
   * @param isCommitted <code>true</code> when a commit has been taken into account to current recognition result.
   * @return <code>true</code> if Stroke bounding box is overlapped on existing text of EditText, <code>false</code> otherwise.
   */
  @Override
  public boolean onBottomTop(@NonNull final WriteToTypeManager.RecognitionResult recognitionResult, final boolean isCommitted)
  {
    boolean overlapped = false;

    if (recognitionResult.isPointerUp)
    {
      final RectF strokeRect = recognitionResult.strokeRect;
      checkFocusChangedWithExtraDistance(strokeRect, LineType.LINE_TYPE_VERTICAL);

      if (mEditText != null)
      {
        final float x = strokeRect.centerX();
        final float y = strokeRect.centerY();

        RectF intersect = new RectF(strokeRect);
        RectF textBounds = getTextBounds(x, y, mEditText);

        if (textBounds != null && ((intersect.intersect(textBounds) && isPassedThrough(strokeRect, textBounds)) || isInDistanceOf(strokeRect, textBounds, LineType.LINE_TYPE_VERTICAL)))
        {
          mWriteToTypeManager.cancelRecognition();
          final PointF center = getCenterOfLine(recognitionResult.points, textBounds);
          if (center.x != -1 && center.y != -1)
          {
            mViewGroup.eraseSpace(mEditText, center.x, center.y);
          }
          else
          {
            final float centerX = (strokeRect.left < textBounds.left) ? textBounds.left : textBounds.right;
            final float centerY = textBounds.centerY();
            mViewGroup.eraseSpace(mEditText, centerX, centerY);
          }

          overlapped = true;
        }

        debugMethod(-1, -1, textBounds, mEditText, strokeRect);
      }
    }

    return overlapped;
  }

  /**
   * onLeftRight is invoked when "LEFT-RIGHT" gesture is recognized by Gesture Recognizer.
   * IMPLEMENTATION CHOICE:
   *     Gesture result "LEFT-RIGHT" means a horizontal line to right direction is detected.
   *     - if "left to right" stroke over an existing text of EditText, it selects characters of stroke area,
   *     - otherwise, it moves one character forward cursor from current cursor location.
   *
   * @param recognitionResult A structure of recognition result, see {@link WriteToTypeManager.RecognitionResult}
   * @param isCommitted <code>true</code> when a commit has been taken into account to current recognition result.
   * @return <code>true</code> if Stroke bounding box is overlapped on existing text of EditText, <code>false</code> otherwise.
   */
  @Override
  public boolean onLeftRight(@NonNull final WriteToTypeManager.RecognitionResult recognitionResult, final boolean isCommitted)
  {
    final RectF strokeRect = recognitionResult.strokeRect;
    checkFocusChangedWithExtraDistance(strokeRect, LineType.LINE_TYPE_HORIZONTAL);

    boolean overlapped = false;

    if (mEditText != null)
    {
      final float x = strokeRect.centerX();
      final float y = strokeRect.centerY();

      RectF intersect = new RectF(strokeRect);
      RectF textBounds = getTextBounds(x, y, mEditText);

      if (textBounds != null && ((intersect.intersect(textBounds) && isOverlappedEnough(strokeRect, intersect)) || isInDistanceOf(strokeRect, textBounds, LineType.LINE_TYPE_HORIZONTAL)))
      {
        if (recognitionResult.isPointerUp)
        {
          mWriteToTypeManager.cancelRecognition();
        }
        mViewGroup.setSelection(mEditText, intersect, mDefaultHighlightColor);

        overlapped = true;
      }
      else
      {
        if (recognitionResult.isRecognizerIdle)
        {
          mWriteToTypeManager.cancelRecognition();
          mViewGroup.forwardCursor(mEditText);
        }
      }

      debugMethod(-1, -1, textBounds, mEditText, strokeRect);
    }

    return overlapped;
  }

  /**
   * onRightLeft is invoked when "RIGHT-LEFT" gesture is recognized by Gesture Recognizer.
   * IMPLEMENTATION CHOICE:
   *     Gesture result "RIGHT-LEFT" means a horizontal line to left direction is detected.
   *     - if "right to left" stroke over an existing text of EditText, it selects characters of stroke area,
   *     - otherwise, it deletes one character backward from current cursor location.
   *
   * @param recognitionResult A structure of recognition result, see {@link WriteToTypeManager.RecognitionResult}
   * @param isCommitted <code>true</code> when a commit has been taken into account to current recognition result.
   * @return <code>true</code> if Stroke bounding box is overlapped on existing text of EditText, <code>false</code> otherwise.
   */
  @Override
  public boolean onRightLeft(@NonNull final WriteToTypeManager.RecognitionResult recognitionResult, final boolean isCommitted)
  {
    final RectF strokeRect = recognitionResult.strokeRect;
    checkFocusChanged(strokeRect);

    boolean overlapped = false;

    if (mEditText != null)
    {
      final float x = strokeRect.centerX();
      final float y = strokeRect.centerY();

      RectF intersect = new RectF(strokeRect);
      RectF textBounds = getTextBounds(x, y, mEditText);

      if (textBounds != null && intersect.intersect(textBounds) && isOverlappedEnough(strokeRect, intersect))
      {
        if (recognitionResult.isPointerUp)
        {
          mWriteToTypeManager.cancelRecognition();
        }
        mViewGroup.setSelection(mEditText, intersect, mDefaultHighlightColor);

        overlapped = true;
      }
      else
      {
        if (recognitionResult.isRecognizerIdle)
        {
          mWriteToTypeManager.cancelRecognition();
          mViewGroup.backwardDelete(mEditText);
        }
      }

      debugMethod(-1, -1, textBounds, mEditText, strokeRect);
    }

    return overlapped;
  }

  /**
   * onScratch is invoked when "SCRATCH" gesture is recognized by Gesture Recognizer.
   * IMPLEMENTATION CHOICE:
   *     - if "scratch" are drawn over an existing text of EditText, it deletes characters of scratched area,
   *     - otherwise, it adds recognized text in current cursor location.
   *     Note that, real text writing could be recognized as "SCRATCH" by Gesture Recognizer.
   *
   * @param recognitionResult A structure of recognition result, see {@link WriteToTypeManager.RecognitionResult}
   * @param isCommitted <code>true</code> when a commit has been taken into account to current recognition result.
   * @return <code>true</code> if Stroke bounding box is overlapped on existing text of EditText, <code>false</code> otherwise.
   */
  @Override
  public boolean onScratch(@NonNull WriteToTypeManager.RecognitionResult recognitionResult, boolean isCommitted)
  {
    final RectF strokeRect = recognitionResult.strokeRect;
    checkFocusChanged(strokeRect);

    boolean overlapped = false;

    if (mEditText != null)
    {
      final float x = strokeRect.centerX();
      final float y = strokeRect.centerY();

      RectF intersect = new RectF(strokeRect);
      RectF textBounds = getTextBounds(x, y, mEditText);

      if (textBounds != null && intersect.intersect(textBounds) && isOverlappedEnough(strokeRect, intersect))
      {
        if (recognitionResult.isPointerUp)
        {
          mViewGroup.backwardDelete(mEditText);
          mWriteToTypeManager.cancelRecognition();
        }
        else
        {
          mViewGroup.setSelection(mEditText, strokeRect, SCRATCH_COLOR);
        }

        overlapped = true;
      }
      else
      {
        setText(recognitionResult, isCommitted);
      }

      debugMethod(-1, -1, textBounds, mEditText, strokeRect);
    }

    return overlapped;
  }

  /**
   * onSurround is invoked when "SURROUND" gesture is recognized by Gesture Recognizer.
   * IMPLEMENTATION CHOICE:
   *     Gesture result "SURROUND" means a circle, an oval or a rectangle is detected.
   *     - if "surround" are drawn over an existing text of EditText, it selects surrounded area,
   *     - otherwise, it adds recognized text in current cursor location.
   *
   * @param recognitionResult A structure of recognition result, see {@link WriteToTypeManager.RecognitionResult}
   * @param isCommitted <code>true</code> when a commit has been taken into account to current recognition result.
   * @return <code>true</code> if Stroke bounding box is overlapped on existing text of EditText, <code>false</code> otherwise.
   */
  @Override
  public boolean onSurround(@NonNull final WriteToTypeManager.RecognitionResult recognitionResult, final boolean isCommitted)
  {
    final RectF strokeRect = recognitionResult.strokeRect;
    checkFocusChanged(strokeRect);

    boolean overlapped = false;

    if (mEditText != null)
    {
      final float x = strokeRect.centerX();
      final float y = strokeRect.centerY();

      RectF intersect = new RectF(strokeRect);
      RectF textBounds = getTextBounds(x, y, mEditText);

      if (textBounds != null && intersect.intersect(textBounds) && textBounds.contains(x, y))
      {
        if (recognitionResult.isPointerUp)
        {
          mWriteToTypeManager.cancelRecognition();
        }
        mViewGroup.setSelection(mEditText, strokeRect, mDefaultHighlightColor);

        overlapped = true;
      }
      else
      {
        setText(recognitionResult, isCommitted);
      }

      debugMethod(-1, -1, textBounds, mEditText, strokeRect);
    }

    return overlapped;
  }

  /**
   * onSingleTap is invoked when "TAP" gesture is recognized by Gesture Recognizer.
   * IMPLEMENTATION CHOICE:
   *     - if "single tap" over an existing text of EditText, it changes a cursor position to the tapped location
   *       (nothing happens if single tap on a selection area),
   *     - otherwise:
   *         - if "single tap" over empty area of other EditText than current focus, it changes a focus of EditText,
   *         - if "single tap" over empty area of current focus EditText, it adds recognized text in current cursor location.
   *
   * @param recognitionResult A structure of recognition result, see {@link WriteToTypeManager.RecognitionResult}
   * @param isCommitted <code>true</code> when a commit has been taken into account to current recognition result.
   * @return <code>true</code> if Stroke bounding box is overlapped on existing text of EditText, <code>false</code> otherwise.
   */
  @Override
  public boolean onSingleTap(@NonNull final WriteToTypeManager.RecognitionResult recognitionResult, final boolean isCommitted)
  {
    boolean overlapped = false;

    if (recognitionResult.isRecognizerIdle)
    {
      final RectF strokeRect = recognitionResult.strokeRect;
      final float x = strokeRect.centerX();
      final float y = strokeRect.centerY();

      EditText editText = mViewGroup.findViewByPosition(x, y);
      if (editText != null)
      {
        RectF textBounds = getTextBounds(x, y, editText);

        if (editText != mEditText)
        {
          mWriteToTypeManager.cancelRecognition();

          mEditText = editText;

          mViewGroup.setFocus(editText);
          mViewGroup.setSelection(editText, x, y, false, mDefaultHighlightColor);

          overlapped = true;

          debugMethod(x, y, textBounds, mEditText, strokeRect);
        }
        else
        {
          if (textBounds != null && textBounds.contains(x, y))
          {
            int cursor = mViewGroup.findCursorByPosition(editText, x, y);
            if ((mSelectionStart != mSelectionEnd) || (mSelectionStart != cursor))
            {
              mWriteToTypeManager.cancelRecognition();

              mViewGroup.setSelection(editText, x, y, false, mDefaultHighlightColor);
            }

            overlapped = true;

            debugMethod(x, y, textBounds, mEditText, strokeRect);
          }
          else
          {
            setText(recognitionResult, isCommitted);
            debugMethod(-1, -1, textBounds, mEditText, strokeRect);
          }
        }
      }
      else
      {
        setText(recognitionResult, isCommitted);
        debugMethod(-1, -1, null, mEditText, strokeRect);
      }
    }

    return overlapped;
  }

  /**
   * onDoubleTap is invoked when "DOUBLE-TAP" gesture is recognized by Gesture Recognizer.
   * IMPLEMENTATION CHOICE:
   *     - if "double tap" over an existing text of EditText, it selects a word of the double tapped location,
   *     - otherwise, nothing happens.
   *
   * @param recognitionResult A structure of recognition result, see {@link WriteToTypeManager.RecognitionResult}
   * @param isCommitted <code>true</code> when a commit has been taken into account to current recognition result.
   * @return <code>true</code> if Stroke bounding box is overlapped on existing text of EditText, <code>false</code> otherwise.
   */
  @Override
  public boolean onDoubleTap(@NonNull final WriteToTypeManager.RecognitionResult recognitionResult, final boolean isCommitted)
  {
    final RectF strokeRect = recognitionResult.strokeRect;
    checkFocusChanged(strokeRect);

    boolean overlapped = false;

    if (mEditText != null)
    {
      final float x = strokeRect.centerX();
      final float y = strokeRect.centerY();

      RectF intersect = new RectF(strokeRect);
      RectF textBounds = getTextBounds(x, y, mEditText);

      if (textBounds != null && intersect.intersect(textBounds) && textBounds.contains(x, y))
      {
        mViewGroup.setSelection(mEditText, x, y, true, mDefaultHighlightColor);
        overlapped = true;
      }

      debugMethod(x, y, textBounds, mEditText, null);
    }

    mWriteToTypeManager.cancelRecognition();

    return overlapped;
  }

  /**
   * onLongPress is invoked when "LONG-PRESS" gesture is recognized by Gesture Recognizer.
   * IMPLEMENTATION CHOICE:
   *     - if "long press" over an existing text of EditText, it selects a word of the long pressed location,
   *     - otherwise, nothing happens.
   *
   * @param recognitionResult A structure of recognition result, see {@link WriteToTypeManager.RecognitionResult}
   * @param isCommitted <code>true</code> when a commit has been taken into account to current recognition result.
   * @return <code>true</code> if Stroke bounding box is overlapped on existing text of EditText, <code>false</code> otherwise.
   */
  @Override
  public boolean onLongPress(@NonNull final WriteToTypeManager.RecognitionResult recognitionResult, final boolean isCommitted)
  {
    final RectF strokeRect = recognitionResult.strokeRect;
    checkFocusChanged(strokeRect);

    boolean overlapped = false;

    if (mEditText != null)
    {
      final float x = strokeRect.centerX();
      final float y = strokeRect.centerY();

      RectF intersect = new RectF(strokeRect);
      RectF textBounds = getTextBounds(x, y, mEditText);

      if (recognitionResult.isPointerUp)
      {
        mWriteToTypeManager.cancelRecognition();
        mIsVibrating = false;

        if (textBounds != null && intersect.intersect(textBounds) && textBounds.contains(x, y))
        {
          overlapped = true;
        }
      }
      else
      {
        if (textBounds != null && intersect.intersect(textBounds) && textBounds.contains(x, y))
        {
          if (!mIsVibrating)
          {
            mIsVibrating = true;
            vibrate();
          }
          mViewGroup.setSelection(mEditText, x, y, true, mDefaultHighlightColor);
          overlapped = true;
        }
      }

      debugMethod(x, y, textBounds, mEditText, null);
    }

    return overlapped;
  }

  // --------------------------------------------------------------------------
  // Implementation of CustomViewGroup.OnChangedListener

  @Override
  public void onFocusChanged(EditText editText)
  {
    if (editText != mEditText)
    {
      mEditText = editText;
      mSelectionStart = -1;
      mSelectionEnd = -1;

      mWriteToTypeManager.clearSession();
    }
  }

  @Override
  public void onSelectionChanged(EditText editText, int selectionStart, int selectionEnd)
  {
    mEditText = editText;
    mSelectionStart = selectionStart;
    mSelectionEnd = selectionEnd;
  }

  // --------------------------------------------------------------------------
  // Internal methods of InputMethodEmulator class

  private void setText(@NonNull WriteToTypeManager.RecognitionResult recognitionResult, boolean isCommitted)
  {
    if (isCommitted && !recognitionResult.textResult.label.isEmpty())
    {
      mViewGroup.setText(mEditText, recognitionResult.textResult.label);
    }
  }

  private void debugMethod(float x, float y, RectF textBounds, @NonNull EditText editText, RectF recoBounds)
  {
    mDebugView.setTouchPoint(x, y);

    mDebugView.setTextBounds(textBounds);
    mDebugView.setViewBounds(editText.getX(), editText.getY(), editText.getX() + editText.getWidth(), editText.getY() + editText.getHeight());

    mDebugView.setRecoBounds(recoBounds);
  }

  /** Compute text bounding box for the first text line inside of EditText. */
  @Nullable
  private RectF getTextBounds(final float x, final float y, @NonNull final EditText editText)
  {
    RectF textBounds = null;

    final int count = editText.getLineCount();
    for (int i = 0; i < count; i++)
    {
      Rect bounds = new Rect();
      // Get the absolute positions of text bounding box.
      // Note that it will retrieve a full space of given line what you can input text.
      editText.getLineBounds(i, bounds);

      // Compute the relative positions inside of EditText.
      bounds.offset((int) editText.getX(), (int) editText.getY());

      if (bounds.contains((int) x, (int) y) || (i == (count - 1)))
      {
        textBounds = new RectF(bounds);

        // Layout.getLineRight(int line) gets the rightmost position
        // that should be exposed for horizontal scrolling on the specified line.
        // It is used for computing actual text end position.
        textBounds.right = textBounds.left + editText.getLayout().getLineRight(i) + (textBounds.height() / 4);

        break;
      }
    }

    return textBounds;
  }

  /** Check if recognition area is really overlapped with existing text or it was just happen by chance. */
  private boolean isOverlappedEnough(@NonNull final RectF original, @NonNull final RectF intersect)
  {
    final float originalArea = original.width() * original.height();
    final float intersectArea = intersect.width() * intersect.height();

    return ((originalArea * OVERLAP_RATIO) < intersectArea);
  }

  /** Check if the given line is in the extra distance or not. */
  private boolean isInDistanceOf(@NonNull final RectF lineRect, @NonNull final RectF textRect, final LineType lineType)
  {
    if (lineType == LineType.LINE_TYPE_HORIZONTAL)
    {
      final float lineCenter = lineRect.left + (lineRect.width() / 2);

      return (((textRect.left <= lineCenter) && (lineCenter <= textRect.right)) &&
          ((lineRect.top > textRect.top) && (lineRect.top < (textRect.bottom + mExtraDistanceInDpi))));
    }
    else
    {
      return (((lineRect.top < textRect.top) && (textRect.bottom < lineRect.bottom)) &&
          ((lineRect.left > textRect.left) && (lineRect.left < (textRect.right + mExtraDistanceInDpi))));
    }
  }

  /** Check if the given line is passed through up/down the text or not. */
  private boolean isPassedThrough(@NonNull final RectF lineRect, @NonNull final RectF textRect)
  {
    return (((textRect.left <= lineRect.left) && (lineRect.right <= textRect.right)) &&
        ((lineRect.top < textRect.top) && (textRect.bottom < lineRect.bottom)));
  }

  /** Get center point of the line in the text bounds. */
  @NonNull
  private PointF getCenterOfLine(@NonNull final List<PointF> points, @NonNull final RectF textBounds)
  {
    assert (points.size() > 0) : "The size of points must be bigger than 0.";

    List<PointF> intersectPoints = new ArrayList<>();
    for (PointF point : points)
    {
      if (textBounds.contains(point.x, point.y))
      {
        intersectPoints.add(point);
      }
    }

    float centerX = -1;
    float centerY = -1;

    if (intersectPoints.size() > 2)
    {
      final int lastIndex = intersectPoints.size() - 1;
      centerX = (intersectPoints.get(0).x + intersectPoints.get(lastIndex).x) / 2;
      centerY = (intersectPoints.get(0).y + intersectPoints.get(lastIndex).y) / 2;
    }

    return new PointF(centerX, centerY);
  }

  private void changeFocusTo(EditText editText, final float centerX, final float centerY)
  {
    if (editText == null)
    {
      editText = mViewGroup.findViewByPosition(centerX, centerY);
    }

    if (editText != null && editText != mEditText)
    {
      mEditText = editText;

      mViewGroup.setFocus(editText);
      mViewGroup.setSelection(editText, centerX, centerY, false, mDefaultHighlightColor);
    }
  }

  /**
   * Check whether the written area (boundingRect) is on the other EditText field or not.
   * If yes, the focus is changed to that field.
   * */
  private void checkFocusChanged(@NonNull final RectF boundingRect)
  {
    if (mEditText != null && mEditText.getHeight() * BIGGER_HEIGHT_FACTOR < boundingRect.height())
    {
      return;
    }

    changeFocusTo(null, boundingRect.centerX(), boundingRect.centerY());
  }

  /**
   * Check whether the written area (boundingRect) with extra distance is belong to other EditText field or not.
   * If yes, the focus is changed to that field.
   */
  private void checkFocusChangedWithExtraDistance(@NonNull final RectF boundingRect, final LineType lineType)
  {
    if (mEditText != null && mEditText.getHeight() * BIGGER_HEIGHT_FACTOR < boundingRect.height())
    {
      return;
    }

    float centerX = boundingRect.centerX();
    float centerY = boundingRect.centerY();

    EditText editText = mViewGroup.findViewByPosition(centerX, centerY);
    if (editText != null)
    {
      changeFocusTo(editText, centerX, centerY);
    }
    else
    {
      if (lineType == LineType.LINE_TYPE_HORIZONTAL)
      {
        centerY = boundingRect.top - mExtraDistanceInDpi;
        changeFocusTo(null, centerX, centerY);
      }
      else if (lineType == LineType.LINE_TYPE_VERTICAL)
      {
        centerX = boundingRect.left - mExtraDistanceInDpi;
        editText = mViewGroup.findViewByPosition(centerX, centerY);
        if (editText != null && mViewGroup.isMultiLine(editText))
        {
          changeFocusTo(editText, centerX, centerY);
        }
      }
    }
  }

  private void vibrate()
  {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
    {
      mVibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
    }
    else
    {
      mVibrator.vibrate(50);
    }
  }
}
