/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.writetotype.core.inkcapture;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class InkView extends View
{
  /** Interface definition for callbacks invoked when drawing a stroke. */
  public interface OnStrokeDrawListener
  {
    boolean shouldBeginDraw(@NonNull InkView inkView, @NonNull StrokePoint point, int pointerId, boolean isStylus);
    void onStrokeDrawBegin(@NonNull InkView inkView, @NonNull StrokePoint point);
    void onStrokeDrawMove(@NonNull InkView inkView, @NonNull StrokePoint point);
    void onStrokeDrawEnd(@NonNull InkView inkView, @NonNull StrokePoint point, @NonNull Path path);
    void onStrokeDrawCancel(@NonNull InkView inkView);
  }

  private OnStrokeDrawListener mOnStrokeDrawListener = null;

  private Paint mPaint = null;
  private Path mPath;

  private int mActivePointerId = -1;
  private float mLastPointX;
  private float mLastPointY;

  // --------------------------------------------------------------------------
  // Constructor

  /** Constructor */
  public InkView(@NonNull Context context)
  {
    this(context, null);
  }

  /** Constructor */
  public InkView(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    init();
  }

  /** Constructor */
  public InkView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
    init();
  }

  /** Initialize this view */
  private void init()
  {
    mPath = new Path();
  }

  // --------------------------------------------------------------------------
  // Public methods to configure this view

  /** Register callbacks invoked when drawing a stroke. */
  public void setOnStrokeDrawListener(@Nullable OnStrokeDrawListener onStrokeDrawListener)
  {
    mOnStrokeDrawListener = onStrokeDrawListener;
  }

  public void setPaint(final Paint paint)
  {
    mPaint = paint;
  }

  public void clear()
  {
    mPath.reset();
    invalidate();
  }

  // --------------------------------------------------------------------------
  // Implementation of View.onDraw to draw a stroke

  @Override
  protected void onDraw(Canvas canvas)
  {
    super.onDraw(canvas);

    if (!mPath.isEmpty() && mPaint != null)
    {
      canvas.save();
      canvas.drawPath(mPath, mPaint);
      canvas.restore();
    }
  }

  // --------------------------------------------------------------------------
  // Mouse event handling to draw a stroke and detect necessary gestures

  @Override
  public boolean dispatchTouchEvent(MotionEvent event)
  {
    final int actionMasked = event.getActionMasked();
    switch (actionMasked)
    {
      case MotionEvent.ACTION_POINTER_DOWN:
      case MotionEvent.ACTION_DOWN:
        processActionDownEvent(event);
        break;

      case MotionEvent.ACTION_MOVE:
        processActionMoveEvent(event);
        break;

      case MotionEvent.ACTION_CANCEL:
        if (isStylusButtonPressed(event))
        {
          processActionUpEvent();
        }
        else
        {
          processActionCancelEvent(event);
        }
        break;

      case MotionEvent.ACTION_POINTER_UP:
      case MotionEvent.ACTION_UP:
        processActionUpEvent(event);
        break;

      default:
        // ignore unhandled motion event
        break;
    }

    /*
     * Must return "true" here to not pass MotionEvents to bottom layers.
     */
    return true;
  }

  private void processActionDownEvent(@NonNull final MotionEvent event)
  {
    if (mActivePointerId == -1)
    {
      final boolean isStylus = isStylusMotionEvent(event);

      final int pointerIndex = event.getActionIndex();
      final int pointerId = event.getPointerId(pointerIndex);

      final float x = event.getX(pointerIndex);
      final float y = event.getY(pointerIndex);
      final float p = event.getPressure(pointerIndex);
      final long t = event.getEventTime();

      StrokePoint point = new StrokePoint(x, y, p, t, StrokePoint.EventType.EVENT_TYPE_DOWN);
      if (mOnStrokeDrawListener != null && mOnStrokeDrawListener.shouldBeginDraw(this, point, pointerId, isStylus))
      {
        mActivePointerId = pointerId;

        mPath.moveTo(x, y);
        mLastPointX = x;
        mLastPointY = y;

        mOnStrokeDrawListener.onStrokeDrawBegin(this, point);
        invalidate();
      }
    }
  }

  private void processActionMoveEvent(@NonNull final MotionEvent event)
  {
    final int pointerIndex = event.findPointerIndex(mActivePointerId);
    if (pointerIndex != -1)
    {
      final int size = event.getHistorySize();
      for (int i = 0; i < size; i++)
      {
        final float hx = event.getHistoricalX(pointerIndex, i);
        final float hy = event.getHistoricalY(pointerIndex, i);
        final float hp = event.getHistoricalPressure(pointerIndex, i);
        final long ht = event.getHistoricalEventTime(i);
        strokeDrawMove(hx, hy, hp, ht);
      }

      final float x = event.getX(pointerIndex);
      final float y = event.getY(pointerIndex);
      final float p = event.getPressure(pointerIndex);
      final long t = event.getEventTime();
      strokeDrawMove(x, y, p, t);

      invalidate();
    }
  }

  private void strokeDrawMove(final float x, final float y, final float p, final long t)
  {
    if (mOnStrokeDrawListener != null)
    {
      mPath.quadTo((mLastPointX + x) / 2.f, (mLastPointY + y) / 2.f, x, y);
      mLastPointX = x;
      mLastPointY = y;

      StrokePoint point = new StrokePoint(x, y, p, t, StrokePoint.EventType.EVENT_TYPE_MOVE);
      mOnStrokeDrawListener.onStrokeDrawMove(this, point);
    }
  }

  private void processActionUpEvent(@NonNull final MotionEvent event)
  {
    final int pointerIndex = event.getActionIndex();
    final int pointerId = event.getPointerId(pointerIndex);

    if (pointerId == mActivePointerId)
    {
      final float x = event.getX(pointerIndex);
      final float y = event.getY(pointerIndex);
      final float p = event.getPressure(pointerIndex);
      final long t = event.getEventTime();

      if (mOnStrokeDrawListener != null)
      {
        mPath.quadTo((mLastPointX + x) / 2.f, (mLastPointY + y) / 2.f, x, y);
        mLastPointX = x;
        mLastPointY = y;

        Path path = new Path(mPath);
        StrokePoint point = new StrokePoint(x, y, p, t, StrokePoint.EventType.EVENT_TYPE_UP);
        mOnStrokeDrawListener.onStrokeDrawEnd(this, point, path);
        invalidate();
      }

      mActivePointerId = -1;
    }
  }

  private void processActionUpEvent()
  {
    if (mOnStrokeDrawListener != null)
    {
      mOnStrokeDrawListener.onStrokeDrawCancel(this);
    }

    invalidate();
    mActivePointerId = -1;
  }

  private void processActionCancelEvent(@NonNull final MotionEvent event)
  {
    final int pointerIndex = event.getActionIndex();
    final int pointerId = event.getPointerId(pointerIndex);

    if (pointerId == mActivePointerId)
    {
      if (mOnStrokeDrawListener != null)
      {
        mOnStrokeDrawListener.onStrokeDrawCancel(this);
      }

      clear();
      mActivePointerId = -1;
    }
  }

  // --------------------------------------------------------------------------
  // Stylus detection

  private boolean isStylusMotionEvent(@NonNull final MotionEvent event)
  {
    final int pointerIndex = event.getActionIndex();
    final int toolType = event.getToolType(pointerIndex);
    return toolType == MotionEvent.TOOL_TYPE_STYLUS;
  }

  private boolean isStylusButtonPressed(@NonNull final MotionEvent event)
  {
    final int buttonState = event.getButtonState();
    return ((buttonState & (MotionEvent.BUTTON_SECONDARY | MotionEvent.BUTTON_TERTIARY)) != 0);
  }
}
