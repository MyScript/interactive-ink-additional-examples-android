// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.ink.serialization.jiix

import com.google.gson.annotations.SerializedName

data class BoundingBox(
    @SerializedName("x")
    val x: Float,
    @SerializedName("y")
    val y: Float,
    @SerializedName("width")
    val width: Float,
    @SerializedName("height")
    val height: Float
)