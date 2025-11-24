package com.myscript.draw_stroke_example;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.Image;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.myscript.iink.Editor;
import com.myscript.iink.IRenderTarget;
import com.myscript.iink.PointerEvent;
import com.myscript.iink.PointerEventType;
import com.myscript.iink.PointerType;
import com.myscript.iink.Renderer;
import com.myscript.iink.uireferenceimplementation.ImageLoader;
import com.myscript.iink.uireferenceimplementation.OfflineSurfaceManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import androidx.annotation.Nullable;


class StrokeView extends View {

    private static final String TAG = "StrokeView";
    private final long eventTimeOffset;
    Bitmap bitmap;
    Editor editor;
    Map<Integer, ArrayList<PointerEvent>> pointerEvents;
    @Nullable
    private com.myscript.iink.uireferenceimplementation.Canvas iinkCanvas;
    @Nullable
    private OfflineSurfaceManager offlineSurfaceManager = null;

    public StrokeView(Context context) {
        super(context);
        long rel_t = SystemClock.uptimeMillis();
        long abs_t = System.currentTimeMillis();
        eventTimeOffset = abs_t - rel_t;
        pointerEvents = new HashMap<>();
    }

    public StrokeView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        long rel_t = SystemClock.uptimeMillis();
        long abs_t = System.currentTimeMillis();
        eventTimeOffset = abs_t - rel_t;
        pointerEvents = new HashMap<>();
    }

    public void setEditor(Editor editor) {
        this.editor = editor;
    }

    @Override
    protected void onSizeChanged(int newWidth, int newHeight, int oldWidth, int oldHeight) {
        if (bitmap != null) {
            bitmap.recycle();
        }
        if (newWidth > 0 && newHeight > 0) {
            bitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
            Canvas sysCanvas = new Canvas(bitmap);
            DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
            iinkCanvas = new com.myscript.iink.uireferenceimplementation.Canvas(sysCanvas, new ArrayList<>(), new HashMap<>(), new ImageLoader(editor), offlineSurfaceManager, metrics.xdpi, metrics.ydpi);
        }
        if (editor != null)
            editor.setViewSize(newWidth, newHeight);

        super.onSizeChanged(newWidth, newHeight, oldWidth, oldHeight);
    }

    private boolean handleOnTouchForPointer(MotionEvent event, int actionMask, int pointerIndex) {
        boolean result;
        final int pointerId = event.getPointerId(pointerIndex);

        PointerType iinkPointerType = PointerType.PEN;

        int historySize = event.getHistorySize();

        switch (actionMask) {
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_DOWN:
                pointerEvents.put(pointerId, new ArrayList<>());
                pointerEvents.get(pointerId).add(new PointerEvent(PointerEventType.DOWN, event.getX(pointerIndex), event.getY(pointerIndex), eventTimeOffset + event.getEventTime(), event.getPressure(), iinkPointerType, pointerId));
                result = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (historySize > 0) {
                    for (int i = 0; i < historySize; ++i)
                        pointerEvents.get(pointerId).add(new PointerEvent(PointerEventType.MOVE, event.getHistoricalX(pointerIndex, i), event.getHistoricalY(pointerIndex, i), eventTimeOffset + event.getHistoricalEventTime(i), event.getHistoricalPressure(pointerIndex, i), iinkPointerType, pointerId));
                    pointerEvents.get(pointerId).add(new PointerEvent(PointerEventType.MOVE, event.getX(pointerIndex), event.getY(pointerIndex), eventTimeOffset + event.getEventTime(), event.getPressure(), iinkPointerType, pointerId));
                } else {
                    pointerEvents.get(pointerId).add(new PointerEvent(PointerEventType.MOVE, event.getX(pointerIndex), event.getY(pointerIndex), eventTimeOffset + event.getEventTime(), event.getPressure(), iinkPointerType, pointerId));
                }
                result = true;
                break;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
                if (historySize > 0) {
                    for (int i = 0; i < historySize; ++i) {
                        pointerEvents.get(pointerId).add(new PointerEvent(PointerEventType.MOVE, event.getHistoricalX(pointerIndex, i), event.getHistoricalY(pointerIndex, i), eventTimeOffset + event.getHistoricalEventTime(i), event.getHistoricalPressure(pointerIndex, i), iinkPointerType, pointerId));
                    }
                }
                pointerEvents.get(pointerId).add(new PointerEvent(PointerEventType.UP, event.getX(pointerIndex), event.getY(pointerIndex), eventTimeOffset + event.getEventTime(), event.getPressure(), iinkPointerType, pointerId));
                result = true;
                break;
            case MotionEvent.ACTION_CANCEL:
                pointerEvents.get(pointerId).add(new PointerEvent(PointerEventType.CANCEL, event.getX(pointerIndex), event.getY(pointerIndex), eventTimeOffset + event.getEventTime(), event.getPressure(), iinkPointerType, pointerId));
                result = true;
                break;
            default:
                result = false;
                break;
        }
        int size = pointerEvents.get(pointerId).size();
        PointerEvent lastPoint = pointerEvents.get(pointerId).get(size - 1);
        // Send pointer events directly in editor if pointerId is selected
        if (pointerEvents.get(pointerId).size() > 1) {
            if (lastPoint.eventType == PointerEventType.UP) {
                Log.d(TAG, "LPA_DBG: pointerEvents pen up (" + pointerEvents.get(pointerId).size() + ", [" + lastPoint.x + "," + lastPoint.y + "])");
                editor.pointerEvents(pointerEvents.get(pointerId).toArray(new PointerEvent[0]), false);
            } else {
                Log.d(TAG, "LPA_DBG: drawStroke (" + pointerEvents.get(pointerId).size() + ", [" + lastPoint.x + "," + lastPoint.y + "])");
                editor.drawStroke(pointerEvents.get(pointerId).toArray(new PointerEvent[0]), iinkCanvas);
            }
        }
        return result;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getAction();

        if (editor == null) {
            return false;
        }

        bitmap.eraseColor(((ColorDrawable) getBackground()).getColor());

        int actionMask = action & MotionEvent.ACTION_MASK;
        int actionPointerIndex = -1;
        boolean result = true;
        if (actionMask == MotionEvent.ACTION_POINTER_DOWN || actionMask == MotionEvent.ACTION_POINTER_UP) {
            actionPointerIndex = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
            result = handleOnTouchForPointer(event, actionMask, actionPointerIndex) & result;
            actionMask = MotionEvent.ACTION_MOVE;
        }

        final int pointerCount = event.getPointerCount();
        for (int pointerIndex = 0; pointerIndex < pointerCount; pointerIndex++) {
            if (pointerIndex != actionPointerIndex) {
                result = handleOnTouchForPointer(event, actionMask, pointerIndex) & result;
            }
        }

        invalidate();
        return result;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap != null)
            canvas.drawBitmap(bitmap, 0, 0, null);
    }
}
