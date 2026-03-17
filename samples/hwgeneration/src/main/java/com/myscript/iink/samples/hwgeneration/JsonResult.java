package com.myscript.iink.samples.hwgeneration;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class JsonResult {

    public class BoundingBox {
        @SerializedName("x")
        public Float x;
        @SerializedName("y")
        public Float y;
        @SerializedName("width")
        public Float width;
        @SerializedName("height")
        public Float height;
    }

    public class Word {
        @SerializedName("label")
        public String label;

        @SerializedName("items")
        public List<Item> items;

        @SerializedName("bounding-box")
        public BoundingBox boundingBox;
    }

    public class RawContent {
        @SerializedName("items")
        public List<Item> items;
    }

    public class Item {
        @SerializedName("X")
        public List<Float> x;
        @SerializedName("Y")
        public List<Float> y;
        @SerializedName("type")
        public String type;
        @SerializedName("id")
        public String id;
    }

    public class Element {
        @SerializedName("words")
        public List<Word> words;
        @SerializedName("items")
        public List<Item> items;
        @SerializedName("lines")
        public List<Line> lines;
    }

    public class Line {
        @SerializedName("x-height")
        public Float xHeight;
    }

    @SerializedName("type")
    public String type;

    @SerializedName("elements")
    public List<Element> elements;

    @SerializedName("version")
    public String version;

    @SerializedName("words")
    public List<Word> words;

    @SerializedName("lines")
    public List<Line> lines;
}
