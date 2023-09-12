/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.writetotype.core.recognition.jiixmodel.item;

import com.google.gson.annotations.SerializedName;

public class JiixChar
{
  @SerializedName("label")
  public String label;

  @SerializedName("word")
  public int word;

  @SerializedName("bounding-box")
  public JiixBoundingBox boundingBox;
}
