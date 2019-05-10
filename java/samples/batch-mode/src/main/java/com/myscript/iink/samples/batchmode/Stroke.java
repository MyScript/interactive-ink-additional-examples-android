/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.batchmode;

import com.myscript.iink.PointerType;

import java.util.Arrays;

public class Stroke {
    private PointerType pointerType;
    private int pointerId;
    private float[] x;
    private float[] y;
    private long[] t;
    private float[] p;

    PointerType getPointerType() {
        return pointerType;
    }

    void setPointerType(PointerType pointerType) {
        this.pointerType = pointerType;
    }

    int getPointerId() {
        return pointerId;
    }

    void setPointerId(int pointerId) {
        this.pointerId = pointerId;
    }

    float[] getX() {
        return x;
    }

    void setX(float[] x) {
        this.x = x;
    }

    float[] getY() {
        return y;
    }

    void setY(float[] y) {
        this.y = y;
    }

    long[] getT() {
        return t;
    }

    void setT(long[] t) {
        this.t = t;
    }

    float[] getP() {
        return p;
    }

    void setP(float[] p) {
        this.p = p;
    }

    @Override
    public String toString() {
        String result = "{";

        result += "\"pointerType\":" + "\"" + this.getPointerType() + "\",";
        result += "\"pointerId\":" + this.getPointerId() + ",";
        result += "\"x\":" + Arrays.toString(this.getX()) + ",";
        result += "\"y\":" + Arrays.toString(this.getY()) + ",";
        result += "\"t\":" + Arrays.toString(this.getT()) + ",";
        result += "\"p\":" + Arrays.toString(this.getP());

        result += "}";

        return result;
    }
}
