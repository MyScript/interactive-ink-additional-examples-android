// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.ink.serialization.jiix

import com.myscript.iink.PointerEvent
import com.myscript.iink.demo.inksample.util.DisplayMetricsConverter

fun PointerEvent.convertPointerEvent(converter: DisplayMetricsConverter?): PointerEvent {
    return PointerEvent(eventType, converter?.x_px2mm(x) ?: x, converter?.y_px2mm(y) ?: y, t, f, pointerType, pointerId)
}

fun Word.toScreenCoordinates(converter: DisplayMetricsConverter?): Word {
    return if (this.boundingBox != null) {
        this.copy(
            boundingBox = BoundingBox(
                x = converter?.x_mm2px(this.boundingBox.x) ?: this.boundingBox.x,
                y = converter?.y_mm2px(this.boundingBox.y) ?: this.boundingBox.y,
                width = converter?.x_mm2px(this.boundingBox.width) ?: this.boundingBox.width,
                height = converter?.y_mm2px(this.boundingBox.height) ?: this.boundingBox.height
            )
        )
    } else {
        this
    }
}

fun BoundingBox.toScreenCoordinates(converter: DisplayMetricsConverter?): BoundingBox {
    return BoundingBox(
        x = converter?.x_mm2px(this.x) ?: this.x,
        y = converter?.y_mm2px(this.y) ?: this.y,
        width = converter?.x_mm2px(this.width) ?: this.width,
        height = converter?.y_mm2px(this.height) ?: this.height
    )
}