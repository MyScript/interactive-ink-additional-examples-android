/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.writetotype.core.recognition.jiixmodel.item;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class JiixWord
{
  @SerializedName("label")
  public String label;

  @SerializedName("candidates")
  public List<String> candidates;

  @SerializedName("first-char")
  public int firstChar;

  @SerializedName("last-char")
  public int lastChar;

  @SerializedName("bounding-box")
  public JiixBoundingBox boundingBox;
}
