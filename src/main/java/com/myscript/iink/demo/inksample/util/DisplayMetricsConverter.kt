package com.myscript.iink.demo.inksample.util

import android.util.DisplayMetrics

class DisplayMetricsConverter(private val displayMetrics: DisplayMetrics) {

    fun x_mm2px(mm: Float) = (mm / INCH_TO_MM) * displayMetrics.xdpi

    fun y_mm2px(mm: Float) = (mm / INCH_TO_MM) * displayMetrics.ydpi

    fun x_px2mm(px: Float) = INCH_TO_MM * (px / displayMetrics.xdpi)

    fun y_px2mm(px: Float) = INCH_TO_MM * (px / displayMetrics.ydpi)

    companion object {
        private const val INCH_TO_MM = 25.4f
    }
}