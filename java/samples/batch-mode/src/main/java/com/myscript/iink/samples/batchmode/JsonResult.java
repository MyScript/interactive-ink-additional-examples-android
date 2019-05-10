/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.batchmode;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class JsonResult {
    @SerializedName("events")
    private ArrayList<Stroke> strokes;

    JsonResult(ArrayList<Stroke> strokes) {
        this.strokes = strokes;
    }

    ArrayList<Stroke> getStrokes() {
        return strokes;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("{\"events\":[");

        for (int i = 0; i < this.strokes.size(); i++) {
            result.append(this.strokes.get(i).toString()).append(i == this.strokes.size() - 1 ? "" : ",");
        }

        result.append("]}");

        return result.toString();
    }
}
