// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.ink.serialization

data class InkRoot(
    val version: String,
    val type: String,
    val id: String,
    val items: List<InkFormat>
)
