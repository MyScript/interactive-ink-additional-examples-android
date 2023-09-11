/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.writetotype.core.inkcapture;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class FadeoutView extends View implements Animation.AnimationListener
{
  private static final float START_ALPHA = 1.f;
  private static final float END_ALPHA = 0.f;
  private static final int DURATION = 500;

  /** Interface definition for callbacks invoked when fadeout animation ended. */
  public interface OnFadeoutViewListener
  {
    void onStrokeViewFadeoutAnimationEnd(@NonNull FadeoutView v);
  }

  private Path mPath = null;
  private Paint mPaint = null;
  private OnFadeoutViewListener mOnFadeoutViewListener = null;

  // --------------------------------------------------------------------------
  // Constructor

  /** Constructor */
  public FadeoutView(@NonNull Context context)
  {
    this(context, null);
  }

  /** Constructor */
  public FadeoutView(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
  }

  /** Constructor */
  public FadeoutView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
  }

  // --------------------------------------------------------------------------
  // Public methods

  /** Register callbacks invoked when fadeout animation ended. */
  public void setOnFadeoutViewListener(@Nullable OnFadeoutViewListener onFadeoutViewListener)
  {
    mOnFadeoutViewListener = onFadeoutViewListener;
  }

  public void setPath(@NonNull final Path path, @NonNull final Paint paint)
  {
    mPath = path;
    mPaint = paint;
  }

  public void clearPath()
  {
    mPath.reset();
  }

  public Path getPath()
  {
    return mPath;
  }

  public void fadeout()
  {
    final AlphaAnimation animation = new AlphaAnimation(START_ALPHA, END_ALPHA);
    animation.setAnimationListener(this);
    animation.setDuration(DURATION);
    postDelayed(new Runnable()
    {
      @Override
      public void run()
      {
        startAnimation(animation);
      }
    }, 0);
  }

  // --------------------------------------------------------------------------
  // Implementation of View.onDraw to draw a stroke with fadeout

  @Override
  protected void onDraw(Canvas canvas)
  {
    if (mPath != null && !mPath.isEmpty() && mPaint != null)
    {
      canvas.save();
      canvas.drawPath(mPath, mPaint);
      canvas.restore();
    }
  }

  // --------------------------------------------------------------------------
  // Implementation of Animation.AnimationListener

  @Override
  public void onAnimationStart(Animation animation)
  {
    // no operation
  }

  @Override
  public void onAnimationRepeat(Animation animation)
  {
    // no operation
  }

  @Override
  public void onAnimationEnd(Animation animation)
  {
    final FadeoutView strokeView = this;
    post(new Runnable()
    {
      @Override
      public void run()
      {
        if (mOnFadeoutViewListener != null)
        {
          mOnFadeoutViewListener.onStrokeViewFadeoutAnimationEnd(strokeView);
        }
      }
    });
  }
}
