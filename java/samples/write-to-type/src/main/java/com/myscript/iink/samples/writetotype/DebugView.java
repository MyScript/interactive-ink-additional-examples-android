/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.writetotype;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Only for debug purpose.
 * It displaying bounding boxes of view, text in side of view, and touch point.
 */
public class DebugView extends View
{
  private boolean mDebug = false;

  private Paint mPaint;
  private RectF mViewBounds = null;
  private RectF mTextBounds = null;
  private RectF mRecoBounds = null;
  private RectF mTouchPoint = null;

  public DebugView(Context context)
  {
    this(context, null);
  }

  public DebugView(Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    init();
  }

  public DebugView(Context context, @Nullable AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init()
  {
    mPaint = new Paint();
    mPaint.setAntiAlias(true);
    mPaint.setStrokeWidth(1);
    mPaint.setStrokeJoin(Paint.Join.ROUND);
    mPaint.setStrokeCap(Paint.Cap.ROUND);
  }

  public void setDebug(final boolean debug)
  {
    mDebug = debug;
    invalidate();
  }

  public void setViewBounds(final RectF viewBounds)
  {
    mViewBounds = (viewBounds == null) ? null : new RectF(viewBounds);
    invalidate();
  }

  public void setViewBounds(final float left, final float top, final float right, final float bottom)
  {
    mViewBounds = (left == top && top == right && right == bottom && left == -1) ? null : new RectF(left, top, right, bottom);
    invalidate();
  }

  public void setTextBounds(final RectF textBounds)
  {
    mTextBounds = (textBounds == null) ? null : new RectF(textBounds);
    invalidate();
  }

  public void setTextBounds(final float left, final float top, final float right, final float bottom)
  {
    mTextBounds = (left == top && top == right && right == bottom && left == -1) ? null : new RectF(left, top, right, bottom);
    invalidate();
  }

  public void setRecoBounds(final RectF recoBounds)
  {
    mRecoBounds = (recoBounds == null) ? null : new RectF(recoBounds);
    invalidate();
  }

  public void setRecoBounds(final float left, final float top, final float right, final float bottom)
  {
    mRecoBounds = (left == top && top == right && right == bottom && left == -1) ? null : new RectF(left, top, right, bottom);
    invalidate();
  }

  public void setTouchPoint(final float x, final float y)
  {
    mTouchPoint = (x == y && x == -1) ? null : new RectF(x - 5, y - 5, x + 5, y + 5);
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas)
  {
    super.onDraw(canvas);

    if (!mDebug)
    {
      return;
    }

    canvas.save();
    if (mViewBounds != null)
    {
      mPaint.setColor(Color.RED);
      mPaint.setStyle(Paint.Style.STROKE);
      canvas.drawRect(mViewBounds, mPaint);
    }
    if (mTextBounds != null)
    {
      mPaint.setColor(Color.BLUE);
      mPaint.setStyle(Paint.Style.STROKE);
      canvas.drawRect(mTextBounds, mPaint);
    }
    if (mRecoBounds != null)
    {
      mPaint.setColor(Color.MAGENTA);
      mPaint.setStyle(Paint.Style.STROKE);
      canvas.drawRect(mRecoBounds, mPaint);
    }
    if (mTouchPoint != null)
    {
      mPaint.setColor(Color.RED);
      mPaint.setStyle(Paint.Style.FILL);
      canvas.drawRect(mTouchPoint, mPaint);
    }
    canvas.restore();
  }
}
