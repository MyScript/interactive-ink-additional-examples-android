package com.myscript.iink.samples.batchmode

import com.google.gson.annotations.SerializedName


class JsonResult {

    @SerializedName("events")
    private var strokes: ArrayList<Stroke>? = null

    fun JsonResult(strokes: ArrayList<Stroke>?) {
        this.strokes = strokes
    }

    fun getStrokes(): ArrayList<Stroke>? {
        return strokes
    }

    override fun toString(): String {
        val result = StringBuilder("{\"events\":[")
        for (i in 0 until strokes!!.size) {
            result.append(strokes!![i].toString())
                .append(if (i == strokes!!.size - 1) "" else ",")
        }
        result.append("]}")
        return result.toString()
    }
}
