package com.myscript.iink.demo.ink.serialization.jiix

import com.google.gson.annotations.SerializedName

data class RecognitionRoot(
    @SerializedName("type")
    val type: String,
    @SerializedName("bounding-box")
    val boundingBox: BoundingBox,
    @SerializedName("elements")
    val elements: List<Element>?
)
