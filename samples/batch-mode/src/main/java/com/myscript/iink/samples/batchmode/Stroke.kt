/*
 * Copyright (c) MyScript. All rights reserved.
 */
package com.myscript.iink.samples.batchmode

import com.myscript.iink.PointerType
import java.util.*

data class Stroke(
    val pointerType: PointerType,
    val pointerId: Int = 0,
    val x: FloatArray,
    val y: FloatArray,
    val t: LongArray,
    val p: FloatArray
) {
    override fun toString(): String = "{" +
            "\"pointerType\":\"$pointerType\"," +
            "\"pointerId\":$pointerId," +
            "\"x\":${x.contentToString()}," +
            "\"y\":${y.contentToString()}," +
            "\"t\":${t.contentToString()}," +
            "\"p\":${p.contentToString()}" +
            "}"
}