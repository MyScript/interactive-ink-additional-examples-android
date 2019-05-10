/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.search;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import com.google.gson.Gson;
import com.myscript.iink.Configuration;
import com.myscript.iink.Editor;
import com.myscript.iink.MimeType;
import com.myscript.iink.graphics.Point;
import com.myscript.iink.graphics.Transform;

import java.util.ArrayList;

public class SearchView extends View
{
    private static final int DEFAULT_ALPHA = 60;
    private static final int DEFAULT_COLOR = Color.RED;

    @Nullable
    private Editor editor;

    @Nullable
    private Bitmap bitmap;
    @Nullable
    private android.graphics.Canvas sysCanvas;

    private String jiixString;
    private String searchWord;
    private ArrayList<Rect> searchRects;

    Paint paint = new Paint();

    public SearchView(Context context)
    {
        super(context);
    }

    public SearchView(Context context, @Nullable AttributeSet attrs)
    {
        super(context, attrs);
    }

    public SearchView(Context context, @Nullable AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);

        editor = null;

        jiixString = null;
        searchWord = null;
        searchRects = null;
    }

    @Override
    protected final void onDraw(android.graphics.Canvas canvas)
    {
        if (sysCanvas == null || bitmap == null)
            return;

        paint.setColor(DEFAULT_COLOR);
        paint.setAlpha(DEFAULT_ALPHA);

        sysCanvas.save();
        sysCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        if (searchRects != null && searchRects.size() != 0)
        {
            for (Rect rect : searchRects)
                sysCanvas.drawRect(rect, paint);
        }

        sysCanvas.restore();

        canvas.drawBitmap(bitmap, 0, 0, null);
    }

    @Override
    protected void onSizeChanged(int newWidth, int newHeight, int oldWidth, int oldHeight)
    {
        if (bitmap != null)
        {
            bitmap.recycle();
        }
        bitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
        sysCanvas = new android.graphics.Canvas(bitmap);

        update();

        super.onSizeChanged(newWidth, newHeight, oldWidth, oldHeight);
    }

    public void setEditor(@NonNull Editor editor)
    {
        this.editor = editor;

        jiixString = "";
        searchWord = "";
        searchRects = new ArrayList<>();
    }

    public void clearSearchResult()
    {
        searchWord = "";
        jiixString = "";

        update();
    }

    public void doSearch(String searchWord)
    {
        if (editor == null)
            throw new IllegalStateException("Must not be called before setEditor()");

        this.searchWord = searchWord;

        try
        {
            this.jiixString = editor.export_(editor.getRootBlock(), MimeType.JIIX);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        update();
    }

    public void update()
    {
        if (jiixString == null || searchWord == null || searchRects == null)
            return;

        findSearchResultRects();
        postInvalidate();
    }

    private void findSearchResultRects()
    {
        if (editor == null)
            throw new IllegalStateException("Must not be called before setEditor()");

        searchRects.clear();
        if (jiixString.equals(""))
            return;

        try
        {
            JiixRawContent rawContent = new Gson().fromJson(jiixString, JiixRawContent.class);

            if (rawContent != null && rawContent.type.equals("Raw Content"))
            {
                for (JiixRawContent.Element element : rawContent.elements)
                {
                    if (element.type.equals("Text"))
                    {
                        for (JiixRawContent.Word word : element.words)
                        {
                            Configuration conf = editor.getEngine().getConfiguration();
                            if (conf.getBoolean("export.jiix.text.chars"))
                                findRectForPartialWord(element, word);
                            else
                                findRectForFullWord(word);
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void findRectForFullWord(JiixRawContent.Word word)
    {
        if (word.label.toLowerCase().equals(searchWord.toLowerCase()))
        {
            Rect rect = getPixelRect(word.boundingBox);
            searchRects.add(rect);
        }
    }

    private void findRectForPartialWord(JiixRawContent.Element element, JiixRawContent.Word word)
    {
        int offset = word.label.toLowerCase().indexOf(searchWord.toLowerCase());
        if (offset >= 0)
        {
            Rect boundingRect = new Rect();

            int length = 0;
            int firstChar = word.firstChar;
            int lastChar = word.lastChar;
            for (int i = (firstChar + offset); i < (lastChar + 1); i++)
            {
                length += 1;
                if (length > searchWord.length())
                    break;

                JiixRawContent.Char charElement = element.chars.get(i);
                Rect rect = getPixelRect(charElement.boundingBox);

                boundingRect.union(rect);
            }

            searchRects.add(boundingRect);
        }
    }

    private Rect getPixelRect(JiixRawContent.BoundingBox boundingBox)
    {
        if (editor == null)
            throw new IllegalStateException("Must not be called before setEditor()");

        Transform transform = editor.getRenderer().getViewTransform();
        Rect rect = new Rect();

        float x = boundingBox.x;
        float y = boundingBox.y;
        float width = boundingBox.width;
        float height = boundingBox.height;

        Point pointStart = transform.apply(x, y);
        Point pointEnd = transform.apply(x + width, y + height);

        rect.left = (int)pointStart.x;
        rect.top = (int)pointStart.y;
        rect.right = (int)pointEnd.x;
        rect.bottom = (int)pointEnd.y;

        return rect;
    }
}
