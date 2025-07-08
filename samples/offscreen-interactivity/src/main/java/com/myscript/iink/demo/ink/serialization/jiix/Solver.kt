// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.ink.serialization.jiix

import com.google.gson.annotations.SerializedName

data class ItemSolver(
    @SerializedName("timestamp")
    val timestamp: String? = null,
    @SerializedName("X")
    val X: List<Float>? = null,
    @SerializedName("Y")
    val Y: List<Float>? = null,
)

data class OperandSolver(
    @SerializedName("label")
    val label: String? = null,
    @SerializedName("bounding-box")
    val boundingBox: BoundingBox? = null,
    @SerializedName("solver-output")
    val solverOutput: Boolean = false,
    @SerializedName("items")
    val items: List<ItemSolver>? = null
)

data class ExpressionSolver(
    @SerializedName("operands")
    val operands: List<OperandSolver>? = null
)

data class SolverRoot(
    @SerializedName("type")
    val type: String,
    @SerializedName("expressions")
    val expressions: List<ExpressionSolver>? = null
)
