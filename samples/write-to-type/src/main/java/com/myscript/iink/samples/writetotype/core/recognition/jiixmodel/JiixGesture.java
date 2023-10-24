package com.myscript.iink.samples.writetotype.core.recognition.jiixmodel;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class JiixGesture
{
  public static final String GESTURE_TYPE_EMPTY = "";

  // Gestures what are available by Gesture Recognizer
  // (Gesture Recognizer means Recognizer with `RECOGNIZER_TYPE_GESTURE` parameter)
  public static final String GESTURE_TYPE_NONE = "none";
  public static final String GESTURE_TYPE_TOP_BOTTOM = "top-bottom";
  public static final String GESTURE_TYPE_BOTTOM_TOP = "bottom-top";
  public static final String GESTURE_TYPE_LEFT_RIGHT = "left-right";
  public static final String GESTURE_TYPE_RIGHT_LEFT = "right-left";
  public static final String GESTURE_TYPE_SCRATCH = "scratch";
  public static final String GESTURE_TYPE_SURROUND = "surround";
  public static final String GESTURE_TYPE_TAP = "tap";
  public static final String GESTURE_TYPE_DOUBLE_TAP = "double-tap";
  public static final String GESTURE_TYPE_LONG_PRESS = "long-press";

  @SerializedName("type")
  public String type;

  @SerializedName("gestures")
  public List<Gesture> gestures;

  public static class Gesture
  {
    @SerializedName("type")
    public String type;
  }
}
