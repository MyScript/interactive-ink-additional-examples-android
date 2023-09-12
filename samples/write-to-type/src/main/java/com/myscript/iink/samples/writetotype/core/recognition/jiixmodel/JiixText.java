/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.writetotype.core.recognition.jiixmodel;

import com.google.gson.annotations.SerializedName;
import com.myscript.iink.samples.writetotype.core.recognition.jiixmodel.item.JiixChar;
import com.myscript.iink.samples.writetotype.core.recognition.jiixmodel.item.JiixWord;

import java.util.List;

public class JiixText
{
  @SerializedName("type")
  public String type;

  @SerializedName("label")
  public String label;

  @SerializedName("words")
  public List<JiixWord> words;

  @SerializedName("chars")
  public List<JiixChar> chars;
}
