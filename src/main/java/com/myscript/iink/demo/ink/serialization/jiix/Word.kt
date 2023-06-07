package com.myscript.iink.demo.ink.serialization.jiix

import com.google.gson.annotations.SerializedName

data class Word(
    @SerializedName("label")
    val label: String? = null,
    @SerializedName("candidates")
    val candidates: List<String>? = null,
    @SerializedName("bounding-box")
    val boundingBox: BoundingBox? = null
)
