// Copyright MyScript. All rights reserved.

package com.myscript.iink.sample.lasso;

import com.myscript.iink.graphics.Point;

/**
 * Class definition used for Gson parsing
 */
public class JiixStrokeDef {
    public static class Stroke {
        public String timestamp;
        public float[] X;
        public float[] Y;
        public float[] F;
        public int[] T;

        public void offset(Point offset) {
            for (int i = 0; i < X.length; i++) {
                X[i] += offset.x;
            }
            for (int i = 0; i < Y.length; i++) {
                Y[i] += offset.y;
            }
        }
    }

    public static class StrokeArray {
        public Stroke[] items;
    }

}
