/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.writetotype.core.inkcapture;

import androidx.annotation.NonNull;

public class StrokePoint
{
  public enum EventType
  {
    EVENT_TYPE_DOWN,
    EVENT_TYPE_MOVE,
    EVENT_TYPE_UP
  }

  public final float x;
  public final float y;
  public final float p;
  public final long t;
  public final EventType eventType;

  public StrokePoint(final float x, final float y, final float p, final long t, @NonNull final EventType eventType)
  {
    this.x = x;
    this.y = y;
    this.p = p;
    this.t = t;
    this.eventType = eventType;
  }
}
