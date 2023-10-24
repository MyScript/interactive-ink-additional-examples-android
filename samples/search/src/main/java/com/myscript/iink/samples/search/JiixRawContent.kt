/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.search

class JiixRawContent {
    @com.google.gson.annotations.SerializedName("type")
    var type: String? = null

    @com.google.gson.annotations.SerializedName("elements")
    var elements: List<Element>? = null

    @com.google.gson.annotations.SerializedName("children")
    var textDocumentElements: List<Element>? = null

    inner class Element {
        @com.google.gson.annotations.SerializedName("type")
        var type: String? = null

        @com.google.gson.annotations.SerializedName("words")
        var words: List<Word>? = null

        @com.google.gson.annotations.SerializedName("chars")
        var chars: List<Char>? = null
    }

    inner class Word {
        @com.google.gson.annotations.SerializedName("label")
        var label: String? = null

        @com.google.gson.annotations.SerializedName("first-char")
        var firstChar = 0

        @com.google.gson.annotations.SerializedName("last-char")
        var lastChar = 0

        @com.google.gson.annotations.SerializedName("bounding-box")
        var boundingBox: BoundingBox? = null
    }

    inner class Char {
        @com.google.gson.annotations.SerializedName("label")
        var label: String? = null

        @com.google.gson.annotations.SerializedName("word")
        var word = 0

        @com.google.gson.annotations.SerializedName("bounding-box")
        var boundingBox: BoundingBox? = null
    }

    inner class BoundingBox {
        @com.google.gson.annotations.SerializedName("x")
        var x = 0f

        @com.google.gson.annotations.SerializedName("y")
        var y = 0f

        @com.google.gson.annotations.SerializedName("width")
        var width = 0f

        @com.google.gson.annotations.SerializedName("height")
        var height = 0f
    }
}
