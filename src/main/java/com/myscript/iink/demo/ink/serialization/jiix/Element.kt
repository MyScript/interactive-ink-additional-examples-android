package com.myscript.iink.demo.ink.serialization.jiix

import com.google.gson.annotations.SerializedName

data class Element(
    @SerializedName("id")
    val id: String,
    @SerializedName("type")
    val type: String,
    @SerializedName("label")
    val label: String,
    @SerializedName("bounding-box")
    val boundingBox: BoundingBox,
    @SerializedName("words")
    val words: List<Word>? = null
)
