// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.ink.serialization.jiix

import com.google.gson.annotations.SerializedName

data class Expression(
    @SerializedName("bounding-box")
    val boundingBox: BoundingBox? = null
)
