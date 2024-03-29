// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.ink.serialization

data class InkFormat(
    val timestamp: String,
    val X: List<Float>,
    val Y: List<Float>,
    val T: List<Long>,
    val F: List<Float>,
    val type: String,
    val id: String
)
