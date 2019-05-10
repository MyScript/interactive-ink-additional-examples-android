/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.search;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class JiixRawContent
{
    @SerializedName("type")
    public String type;

    @SerializedName("elements")
    public List<Element> elements;

    public class Element
    {
        @SerializedName("type")
        public String type;

        @SerializedName("words")
        public List<Word> words;

        @SerializedName("chars")
        public List<Char> chars;
    }

    public class Word
    {
        @SerializedName("label")
        public String label;

        @SerializedName("first-char")
        public int firstChar;

        @SerializedName("last-char")
        public int lastChar;

        @SerializedName("bounding-box")
        public BoundingBox boundingBox;
    }

    public class Char
    {
        @SerializedName("label")
        public String label;

        @SerializedName("word")
        public int word;

        @SerializedName("bounding-box")
        public BoundingBox boundingBox;
    }

    public class BoundingBox
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
}
