/*
 * Copyright (c) MyScript. All rights reserved.
 */
package com.myscript.iink.samples.batchmode

import com.myscript.iink.PointerType
import java.util.*

data class Stroke(
    var pointerType: PointerType? = null,
    var pointerId: Int = 0,
    var x: FloatArray,
    var y: FloatArray,
    var t: LongArray,
    var p: FloatArray
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