/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.assessment.utils

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ScrollView

class LockableScrollView : ScrollView {
    // true if we can scroll (not locked) false if we cannot scroll (locked)
    private var mInterceptScrollable = false
    private var mScrollableType = MotionEvent.TOOL_TYPE_FINGER

    constructor (context: Context?) : super(context)

    constructor(context: Context?, attrs: AttributeSet?) :
            super(context, attrs)

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context,attrs,defStyleAttr)

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
            super(context,attrs,defStyleAttr,defStyleRes)


    fun lockScrolling() {
        mScrollableType = -1
    }

    fun setScrollingEnabled(androidToolType: Int) {
        when(androidToolType) {
            MotionEvent.TOOL_TYPE_UNKNOWN -> mScrollableType = MotionEvent.TOOL_TYPE_UNKNOWN
            MotionEvent.TOOL_TYPE_FINGER -> mScrollableType = MotionEvent.TOOL_TYPE_FINGER
            MotionEvent.TOOL_TYPE_MOUSE -> mScrollableType = MotionEvent.TOOL_TYPE_MOUSE
            MotionEvent.TOOL_TYPE_STYLUS -> mScrollableType = MotionEvent.TOOL_TYPE_STYLUS
            MotionEvent.TOOL_TYPE_ERASER -> mScrollableType = MotionEvent.TOOL_TYPE_ERASER
            else -> mScrollableType =-1

        }
    }
    fun isScrollable(): Boolean {
        return mScrollableType!=-1
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                // scroll if hand!
                if (ev.getToolType(0) == mScrollableType)
                    super.onTouchEvent(ev)
                else false
                // if we can scroll pass the event to the superclass
            }
            else -> super.onTouchEvent(ev)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        // Don't do anything with intercepted touch events if
        // we are not scrollable
        return mInterceptScrollable && super.onInterceptTouchEvent(ev)
    }
}