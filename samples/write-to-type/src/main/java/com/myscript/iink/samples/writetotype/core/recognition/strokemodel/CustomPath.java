/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.writetotype.core.recognition.strokemodel;

import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

public class CustomPath extends Path
{
  final private List<PointF> mPoints = new ArrayList<>();

  @Override
  public void moveTo(float x, float y)
  {
    super.moveTo(x, y);
    mPoints.add(new PointF(x, y));
  }

  @Override
  public void lineTo(float x, float y)
  {
    super.lineTo(x, y);
    mPoints.add(new PointF(x, y));
  }

  @Override
  public void reset()
  {
    super.reset();
    mPoints.clear();
  }

  public RectF getBoundingRect()
  {
    RectF rect = new RectF();
    this.computeBounds(rect, true);

    return rect;
  }

  @NonNull
  public List<PointF> getPoints()
  {
    return new ArrayList<>(this.mPoints);
  }
}
