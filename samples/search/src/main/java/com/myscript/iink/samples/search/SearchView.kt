/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.search

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.NonNull
import com.google.gson.Gson
import com.myscript.iink.Editor
import com.myscript.iink.MimeType
import java.util.*

class SearchView : View {

    private val DEFAULT_ALPHA = 60
    private val DEFAULT_COLOR = Color.RED

    private var editor: Editor? = null

    private var bitmap: Bitmap? = null
    private var sysCanvas: Canvas? = null

    private var jiixString: String? = null
    private var searchWord: String? = null
    private var searchRects: ArrayList<Rect>? = null

    private var iinkTouchView : View?= null
    private var touchModeWithPen : Boolean = false

    var paint = Paint()

    constructor (context: Context?) : super(context)

    constructor(context: Context?, attrs: AttributeSet?) :
            super(context, attrs)

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context,attrs,defStyleAttr){
        editor = null
        jiixString = null
        searchWord = null
        searchRects = null
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int):
        super(context, attrs, defStyleAttr, defStyleRes){
        editor = null
        jiixString = null
        searchWord = null
        searchRects = null
    }

    override fun onDraw(canvas: Canvas) {
        if (sysCanvas == null || bitmap == null) return
        paint.color = DEFAULT_COLOR
        paint.alpha = DEFAULT_ALPHA
        sysCanvas!!.save()
        sysCanvas!!.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        if (searchRects != null && searchRects!!.size != 0) {
            for (rect in searchRects!!) sysCanvas!!.drawRect(rect, paint)
        }
        sysCanvas!!.restore()
        canvas.drawBitmap(bitmap!!, 0f, 0f, null)
    }

    override fun onSizeChanged(newWidth: Int, newHeight: Int, oldWidth: Int, oldHeight: Int) {
        if (bitmap != null) {
            bitmap!!.recycle()
        }
        bitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
        sysCanvas = Canvas(bitmap!!)
        update()
        super.onSizeChanged(newWidth, newHeight, oldWidth, oldHeight)
    }

    fun setEditor(editor: Editor) {
        this.editor = editor
        jiixString = ""
        searchWord = ""
        searchRects = ArrayList()
    }

    fun clearSearchResult() {
        searchWord = ""
        jiixString = ""
        update()
    }

    fun doSearch(searchWord: String?) {
        checkNotNull(editor) { "Must not be called before setEditor()" }
        this.searchWord = searchWord
        try {
            jiixString = editor!!.export_(editor!!.rootBlock, MimeType.JIIX)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        update()
    }

    private fun update() {
        if (jiixString == null || searchWord == null || searchRects == null) return
        findSearchResultRects()
        postInvalidate()
    }

    private fun findSearchResultRects() {
        checkNotNull(editor) { "Must not be called before setEditor()" }
        searchRects!!.clear()
        if (jiixString == "") return
        try {
            val rawContent = Gson().fromJson(
                jiixString,
                JiixRawContent::class.java
            )
            if (rawContent != null){
                if(rawContent.type == "Raw Content") {
                    for (element in rawContent.elements!!) {
                        parseElements(element)
                    }
                }else if ( rawContent.type == "Container") {
                    for (element in rawContent.textDocumentElements!!) {
                        parseElements(element)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseElements(element : JiixRawContent.Element){
        if (element.type == "Text") {
            for (word in element.words!!) {
                val conf = editor!!.engine.configuration
                if (conf.getBoolean("export.jiix.text.chars")) findRectForPartialWord(
                    element,
                    word
                ) else findRectForFullWord(word)
            }
        }
    }
    private fun findRectForFullWord(word: JiixRawContent.Word) {
        if (word.label!!.lowercase(Locale.getDefault()) == searchWord!!.lowercase(Locale.getDefault())) {
            val rect = getPixelRect(word.boundingBox)
            searchRects!!.add(rect)
        }
    }

    private fun findRectForPartialWord(element: JiixRawContent.Element, word: JiixRawContent.Word) {
        val offset = word.label!!.lowercase(Locale.getDefault()).indexOf(searchWord!!.lowercase(
            Locale.getDefault()
        ))
        if (offset >= 0) {
            val boundingRect = Rect()
            var length = 0
            val firstChar = word.firstChar
            val lastChar = word.lastChar
            for (i in firstChar + offset until lastChar + 1) {
                length += 1
                if (length > searchWord!!.length) break
                val charElement = element.chars!![i]
                val rect = getPixelRect(charElement.boundingBox)
                boundingRect.union(rect)
            }
            searchRects!!.add(boundingRect)
        }
    }

    private fun getPixelRect(boundingBox: JiixRawContent.BoundingBox?): Rect {
        checkNotNull(editor) { "Must not be called before setEditor()" }
        val transform = editor!!.renderer.viewTransform
        val rect = Rect()
        val x = boundingBox!!.x
        val y = boundingBox.y
        val width = boundingBox.width
        val height = boundingBox.height
        val pointStart = transform!!.apply(x, y)
        val pointEnd = transform.apply(x + width, y + height)
        rect.left = pointStart.x.toInt()
        rect.top = pointStart.y.toInt()
        rect.right = pointEnd.x.toInt()
        rect.bottom = pointEnd.y.toInt()
        return rect
    }

    //tis is the part to allow redraw of highlighted solution when moving
    fun setPropagTouchView(@NonNull v:View){
        iinkTouchView=v
    }
    fun forcePenInTouchMode(forced:Boolean){
        touchModeWithPen=forced
    }
    override fun dispatchTouchEvent(@NonNull event: MotionEvent): Boolean {
        if (iinkTouchView!=null){
            if(event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER ||
                (touchModeWithPen && event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS)) {
                iinkTouchView?.dispatchTouchEvent(event)
                if (event.action == MotionEvent.ACTION_DOWN)
                    return true

                if (event.action == MotionEvent.ACTION_MOVE ||
                    event.action == MotionEvent.ACTION_UP
                )
                    update()
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
