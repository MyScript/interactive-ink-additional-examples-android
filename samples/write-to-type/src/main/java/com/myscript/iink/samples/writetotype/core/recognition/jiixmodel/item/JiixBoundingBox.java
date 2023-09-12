/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.writetotype.core.recognition.jiixmodel.item;

import com.google.gson.annotations.SerializedName;

public class JiixBoundingBox
{
  @SerializedName("x")
  public float x;

  @SerializedName("y")
  public float y;

  @SerializedName("width")
  public float width;

  @SerializedName("height")
  public float height;
}