/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.writetotype.core.inkcapture;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class InkCaptureView extends FrameLayout implements InkView.OnStrokeDrawListener, FadeoutView.OnFadeoutViewListener
{
  /** Interface definition for callbacks invoked when capturing a stroke. */
  public interface OnStrokeListener
  {
    void onStrokeBegin(@NonNull InkCaptureView inkCaptureView, @NonNull StrokePoint point, int pointerId);
    void onStrokeMove(@NonNull InkCaptureView inkCaptureView, @NonNull StrokePoint point, int pointerId);
    void onStrokeEnd(@NonNull InkCaptureView inkCaptureView, @NonNull StrokePoint point, int pointerId);
    void onStrokeCancel(@NonNull InkCaptureView inkCaptureView);
  }

  private static final int DEFAULT_INK_WIDTH = 5;
  private static final int DEFAULT_INK_COLOR = 0xFF33B5E5;

  private OnStrokeListener mOnStrokeListener = null;

  private Paint mPaint;
  private InkView mInkView;
  private HashMap<Path, FadeoutView> mFadeoutViews;
  private int mPointerId;

  /** Flag to enable 'active stylus input' only mode. If it's set by true, finger input is disabled. */
  private boolean mActiveStylusOnly = false;

  // --------------------------------------------------------------------------
  // Constructor

  /** Constructor */
  public InkCaptureView(@NonNull Context context)
  {
    this(context, null);
  }

  /** Constructor */
  public InkCaptureView(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    init();
  }

  /** Constructor */
  public InkCaptureView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
    init();
  }

  /** Initialize this view */
  private void init()
  {
    mPaint = new Paint();
    mPaint.setAntiAlias(true);
    mPaint.setStyle(Paint.Style.STROKE);
    mPaint.setColor(DEFAULT_INK_COLOR);
    mPaint.setStrokeWidth(DEFAULT_INK_WIDTH);
    mPaint.setStrokeJoin(Paint.Join.ROUND);
    mPaint.setStrokeCap(Paint.Cap.ROUND);

    mFadeoutViews = new HashMap<>();

    mInkView = new InkView(getContext());
    mInkView.setPaint(mPaint);
    mInkView.setOnStrokeDrawListener(this);

    addView(mInkView);
  }

  // --------------------------------------------------------------------------
  // Public methods

  /** Register callbacks to be invoked when capturing a stroke. */
  public void setOnStrokeListener(@Nullable OnStrokeListener onStrokeListener)
  {
    mOnStrokeListener = onStrokeListener;
  }

  public void setActiveStylusOnly(final boolean activeStylusOnly)
  {
    mActiveStylusOnly = activeStylusOnly;
  }

  public void clearStrokes(boolean animated)
  {
    if (!mFadeoutViews.isEmpty())
    {
      if (animated)
      {
        for (FadeoutView fadeoutView : mFadeoutViews.values())
        {
          fadeoutView.fadeout();
        }
      }
      else
      {
        for (FadeoutView fadeoutView : mFadeoutViews.values())
        {
          fadeoutView.setOnFadeoutViewListener(null);
          fadeoutView.clearPath();
          removeView(fadeoutView);
        }
        mFadeoutViews.clear();
      }
    }
  }

  // --------------------------------------------------------------------------
  // Implementation of InkView.OnInkEventListener

  @Override
  public boolean shouldBeginDraw(@NonNull InkView inkView, @NonNull StrokePoint point, int pointerId, boolean isStylus)
  {
    mPointerId = pointerId;

    // the return value MUST be true to draw stroke on canvas
    return !mActiveStylusOnly || isStylus;
  }

  @Override
  public void onStrokeDrawBegin(@NonNull InkView inkView, @NonNull StrokePoint point)
  {
    if (mOnStrokeListener != null)
    {
      mOnStrokeListener.onStrokeBegin(this, point, mPointerId);
    }
  }

  @Override
  public void onStrokeDrawMove(@NonNull InkView inkView, @NonNull StrokePoint point)
  {
    if (mOnStrokeListener != null)
    {
      mOnStrokeListener.onStrokeMove(this, point, mPointerId);
    }
  }

  @Override
  public void onStrokeDrawEnd(@NonNull InkView inkView, @NonNull StrokePoint point, @NonNull Path path)
  {
    if (!mFadeoutViews.containsKey(path))
    {
      FadeoutView fadeoutView = new FadeoutView(getContext());
      fadeoutView.setPath(path, mPaint);
      fadeoutView.setOnFadeoutViewListener(this);
      // When you want to fadeout a stroke when stroke draw end,
      // you can call "fadeoutView.fadeout()" here.
      mFadeoutViews.put(path, fadeoutView);
      addView(fadeoutView);
    }

    if (mOnStrokeListener != null)
    {
      mOnStrokeListener.onStrokeEnd(this, point, mPointerId);
    }

    mInkView.clear();
  }

  @Override
  public void onStrokeDrawCancel(@NonNull InkView inkView)
  {
    if (mOnStrokeListener != null)
    {
      mOnStrokeListener.onStrokeCancel(this);
    }

    mInkView.clear();
  }

  // --------------------------------------------------------------------------
  // Implementation of FadeoutView.OnFadeoutViewListener

  @Override
  public void onStrokeViewFadeoutAnimationEnd(@NonNull final FadeoutView fadeoutView)
  {
    removeStroke(fadeoutView.getPath());
  }

  private void removeStroke(@Nullable final Path path)
  {
    if (path != null && mFadeoutViews.containsKey(path))
    {
      FadeoutView fadeoutView = mFadeoutViews.get(path);
      mFadeoutViews.remove(path);
      if (fadeoutView != null)
      {
        fadeoutView.setOnFadeoutViewListener(null);
        fadeoutView.clearPath();
        removeView(fadeoutView);
      }
    }
  }
}
