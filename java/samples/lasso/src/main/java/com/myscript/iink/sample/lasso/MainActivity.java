// Copyright MyScript. All rights reserved.

package com.myscript.iink.sample.lasso;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.myscript.iink.Configuration;
import com.myscript.iink.ContentPackage;
import com.myscript.iink.ContentPart;
import com.myscript.iink.Editor;
import com.myscript.iink.Engine;
import com.myscript.iink.IEditorListener;
import com.myscript.iink.MimeType;
import com.myscript.iink.PointerEvent;
import com.myscript.iink.PointerEventType;
import com.myscript.iink.PointerType;
import com.myscript.iink.app.common.activities.ErrorActivity;
import com.myscript.iink.graphics.Point;
import com.myscript.iink.graphics.Transform;
import com.myscript.iink.uireferenceimplementation.EditorView;
import com.myscript.iink.uireferenceimplementation.ImageLoader;
import com.myscript.iink.uireferenceimplementation.InputController;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";

    private static final String INPUT_MODE_KEY = "inputMode";

    protected Engine engine;

    protected EditorView drawingView;
    protected EditorView lassoView;

    @NonNull
    private AtomicBoolean isBusy = new AtomicBoolean(false);
    private TextView messageView;

    private Editor drawingEditor;
    private Editor lassoEditor;
    private Editor batchEditor;

    private String textPackageName = "package.iink";
    private String lassoPackageName = "lasso.iink";
    private String batchPackageName = "batch.iink";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ErrorActivity.setExceptionHandler(this);

        engine = IInkApplication.getEngine();

        // configure recognition
        Configuration conf = engine.getConfiguration();
        String confDir = "zip://" + getPackageCodePath() + "!/assets/conf";
        conf.setStringArray("configuration-manager.search-path", new String[]{confDir});
        String tempDir = getFilesDir().getPath() + File.separator + "tmp";
        conf.setString("content-package.temp-folder", tempDir);
        conf.setBoolean("text.guides.enable", false);

        setContentView(R.layout.activity_main);
        messageView = findViewById(R.id.message_field);

        FrameLayout frame = findViewById(R.id.root_frame);

        drawingView = (EditorView) frame.getChildAt(0);
        drawingView.setEngine(engine);
        drawingEditor = drawingView.getEditor();
        drawingEditor.addListener(new IEditorListener() {
            @Override
            public void partChanging(Editor editor, ContentPart oldPart, ContentPart newPart) {
                // no-op
            }

            @Override
            public void partChanged(Editor editor) {
                invalidateOptionsMenu();
                invalidateIconButtons();
            }

            @Override
            public void contentChanged(Editor editor, String[] blockIds) {
                invalidateOptionsMenu();
                invalidateIconButtons();
            }

            @Override
            public void onError(Editor editor, String blockId, String message) {
                Log.e(TAG, "Failed to edit block \"" + blockId + "\"" + message);
            }
        });
        drawingView.setImageLoader(new ImageLoader(drawingEditor, this.getCacheDir()));

        lassoView = (EditorView) frame.getChildAt(1);
        lassoView.setEngine(engine);
        lassoView.setBackgroundColor(android.graphics.Color.argb(0, 0, 0, 0));
        lassoView.setInputMode(InputController.INPUT_MODE_FORCE_PEN);
        lassoEditor = lassoView.getEditor();
        lassoEditor.setTheme("stroke { color: #00FFFFFF; }");
        lassoEditor.addListener(new IEditorListener() {
            @Override
            public void partChanging(Editor editor, ContentPart oldPart, ContentPart newPart) {
                // no-op
            }

            @Override
            public void partChanged(Editor editor) {
                // no-op
            }

            @Override
            public void contentChanged(Editor editor, String[] blockIds) {
                onLasso();
            }

            @Override
            public void onError(Editor editor, String blockId, String message) {
                Log.e(TAG, "Failed to edit block \"" + blockId + "\"" + message);
            }
        });
        lassoView.setImageLoader(new ImageLoader(lassoEditor, this.getCacheDir()));

        int inputMode = InputController.INPUT_MODE_FORCE_PEN; // If using an active pen, put INPUT_MODE_AUTO here
        if (savedInstanceState != null)
            inputMode = savedInstanceState.getInt(INPUT_MODE_KEY, inputMode);
        setInputMode(inputMode);

        // Create a renderer with a null render target
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        // Create the batch editor
        batchEditor = engine.createEditor(engine.createRenderer(displayMetrics.xdpi, displayMetrics.ydpi, null));
        // The editor requires a font metrics provider and a view size *before* calling setPart()
        batchEditor.setFontMetricsProvider(null);
        batchEditor.setViewSize(displayMetrics.widthPixels, displayMetrics.heightPixels);

        // wait for view size initialization before setting part
        drawingView.post(new Runnable() {
            @Override
            public void run() {
                configureEditor(drawingEditor, textPackageName, "Drawing");
                configureEditor(lassoEditor, lassoPackageName, "Drawing");
                configureEditor(batchEditor, batchPackageName, "Text");
                drawingView.setVisibility(View.VISIBLE);
            }
        });

        findViewById(R.id.button_input_mode_forcePen).setOnClickListener(this);
        findViewById(R.id.button_input_mode_forceTouch).setOnClickListener(this);
        findViewById(R.id.button_input_mode_auto).setOnClickListener(this);
        findViewById(R.id.button_lasso).setOnClickListener(this);
        findViewById(R.id.button_undo).setOnClickListener(this);
        findViewById(R.id.button_redo).setOnClickListener(this);
        findViewById(R.id.button_clear).setOnClickListener(this);

        invalidateIconButtons();
    }

    private void configureEditor(Editor editor, String packageName, String partType) {
        // Configure package a new package
        try {
            ContentPackage contentPackage = engine.createPackage(packageName);

            // Create a new part
            ContentPart contentPart = contentPackage.createPart(partType);

            // Associate editor with the new part
            editor.setPart(contentPart);

            contentPart.close();
            contentPackage.close();
        } catch (Exception e) {
            showMessage("Failed to open package " + packageName);
            Log.e(TAG, "Failed to open package " + packageName, e);
        }
    }

    @Override
    protected void onDestroy() {
        drawingView.setOnTouchListener(null);
        drawingView.close();

        lassoView.setOnTouchListener(null);
        lassoView.close();

        batchEditor.setPart(null);
        try {
            engine.deletePackage(textPackageName);
            engine.deletePackage(lassoPackageName);
            engine.deletePackage(batchPackageName);
        } catch (IOException e) {
            showMessage("Failed to remove package ");
            Log.e(TAG, "Failed to remove package ", e);
        }

        // IInkApplication has the ownership, do not close here
        engine = null;

        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_input_mode_forcePen:
                setInputMode(InputController.INPUT_MODE_FORCE_PEN);
                break;
            case R.id.button_input_mode_forceTouch:
                setInputMode(InputController.INPUT_MODE_FORCE_TOUCH);
                break;
            case R.id.button_input_mode_auto:
                setInputMode(InputController.INPUT_MODE_AUTO);
                break;
            case R.id.button_lasso:
                findViewById(R.id.button_lasso).setEnabled(false);
                lassoView.setVisibility(View.VISIBLE);
                break;
            case R.id.button_undo:
                drawingEditor.undo();
                break;
            case R.id.button_redo:
                drawingEditor.redo();
                break;
            case R.id.button_clear:
                drawingEditor.clear();
                break;
            default:
                Log.e(TAG, "Failed to handle click event");
                break;
        }
    }

    private void setInputMode(int inputMode) {
        drawingView.setInputMode(inputMode);
        findViewById(R.id.button_input_mode_forcePen).setEnabled(inputMode != InputController.INPUT_MODE_FORCE_PEN);
        findViewById(R.id.button_input_mode_forceTouch).setEnabled(inputMode != InputController.INPUT_MODE_FORCE_TOUCH);
        findViewById(R.id.button_input_mode_auto).setEnabled(inputMode != InputController.INPUT_MODE_AUTO);
    }

    private void invalidateIconButtons() {
        final boolean canUndo = drawingEditor.canUndo();
        final boolean canRedo = drawingEditor.canRedo();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ImageButton imageButtonUndo = findViewById(R.id.button_undo);
                imageButtonUndo.setEnabled(canUndo);
                ImageButton imageButtonRedo = findViewById(R.id.button_redo);
                imageButtonRedo.setEnabled(canRedo);
                ImageButton imageButtonClear = findViewById(R.id.button_clear);
                imageButtonClear.setEnabled(true);
            }
        });
    }

    private void showMessage(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                messageView.setText(msg);
            }
        });
    }

    private void onLasso() {
        if (isBusy.getAndSet(true) || lassoEditor.isEmpty(null))
            return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JiixStrokeDef.StrokeArray lassoStroke = getDrawingStrokes(lassoEditor);
                    if (lassoStroke == null && lassoStroke.items == null && lassoStroke.items.length == 0) {
                        showMessage("No lasso stroke");
                        return;
                    }

                    showMessage("Started batch on lasso analyse, Please wait...");
                    // get lasso stroke
                    Transform viewTransform = drawingEditor.getRenderer().getViewTransform();
                    Transform offsetTransform = new Transform(viewTransform);
                    offsetTransform.invert();
                    JiixStrokeDef.Stroke lasso = lassoStroke.items[0];
                    lasso.offset(offsetTransform.apply(0, 0));

                    int polyCorners = lasso.X.length;
                    int i, j = polyCorners - 1;
                    float constant[] = new float[polyCorners];
                    float multiple[] = new float[polyCorners];

                    for (i = 0; i < polyCorners; i++) {
                        if (lasso.Y[j] == lasso.Y[i]) {
                            constant[i] = lasso.X[i];
                            multiple[i] = 0;
                        } else {
                            constant[i] = lasso.X[i] - (lasso.Y[i] * lasso.X[j]) / (lasso.Y[j] - lasso.Y[i]) + (lasso.Y[i] * lasso.X[i]) / (lasso.Y[j] - lasso.Y[i]);
                            multiple[i] = (lasso.X[j] - lasso.X[i]) / (lasso.Y[j] - lasso.Y[i]);
                        }
                        j = i;
                    }
                    List<PointerEvent> events = new ArrayList<>();

                    JiixStrokeDef.StrokeArray strokes = getDrawingStrokes(drawingEditor);
                    if (strokes != null && strokes.items != null) {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS", Locale.US);
                        for (JiixStrokeDef.Stroke stroke : strokes.items) {
                            if (strokeInLasso(stroke, lasso, constant, multiple))
                                addToPtEvent(events, stroke, viewTransform, dateFormat);
                        }
                    }

                    PointerEvent[] eventsArray = new PointerEvent[events.size()];
                    showMessage("Result: " + batchReco(events.toArray(eventsArray)));

                    lassoEditor.clear();
                    lassoEditor.waitForIdle();
                    MainActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            findViewById(R.id.button_lasso).setEnabled(true);
                            lassoView.setVisibility(View.INVISIBLE);
                        }
                    });
                } finally {
                    isBusy.set(false);
                }
            }
        }).start();
    }

    private JiixStrokeDef.StrokeArray getDrawingStrokes(Editor editor) {
        String jiixString;
        try {
            jiixString = editor.export_(null, MimeType.JIIX);
        } catch (Exception e) {
            return null; // when processing is ongoing, export may fail: ignore
        }
        JiixStrokeDef.StrokeArray strokes = null;
        try {
            Gson gson = new Gson();
            strokes = gson.fromJson(jiixString, JiixStrokeDef.StrokeArray.class);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Failed to parse jiix string as json words: " + e.toString());
        }

        return strokes;
    }

    private boolean strokeInLasso(JiixStrokeDef.Stroke stroke, JiixStrokeDef.Stroke lasso, float[] constant, float[] multiple) {
        int strokeLen = stroke.X.length;
        int lassoLen = lasso.X.length;

        for (int pt = 0; pt < strokeLen; pt++) {
            int j = lassoLen - 1;
            boolean oddNodes = false;
            for (int i = 0; i < lassoLen; i++) {
                if ((lasso.Y[i] < stroke.Y[pt] && lasso.Y[j] >= stroke.Y[pt]
                        || lasso.Y[j] < stroke.Y[pt] && lasso.Y[i] >= stroke.Y[pt])) {
                    oddNodes ^= (stroke.Y[pt] * multiple[i] + constant[i] < stroke.X[pt]);
                }
                j = i;
            }
            if (oddNodes)
                return true;
        }
        return false;
    }

    private void addToPtEvent(List<PointerEvent> events, JiixStrokeDef.Stroke stroke, Transform viewTransform, SimpleDateFormat dateFormat) {
        int nbPt = stroke.X.length;
        long time = 0;
        for (int i = 0; i < nbPt; i++) {
            Point pt = viewTransform.apply(stroke.X[i], stroke.Y[i]);
            if (i == 0) {
                try {
                    Date date = dateFormat.parse(stroke.timestamp);
                    time = date.getTime();
                } catch (java.text.ParseException e) {
                    Log.e(TAG, e.toString());
                }
                events.add(new PointerEvent(PointerEventType.DOWN, pt.x, pt.y, time + stroke.T[i], stroke.F[i], PointerType.PEN, 0));
            }

            if (i == nbPt - 1)
                events.add(new PointerEvent(PointerEventType.UP, pt.x, pt.y, time + stroke.T[i], stroke.F[i], PointerType.PEN, 0));

            if (i != 0 && i != nbPt - 1)
                events.add(new PointerEvent(PointerEventType.MOVE, pt.x, pt.y, time + stroke.T[i], stroke.F[i], PointerType.PEN, 0));
        }
    }

    private String batchReco(PointerEvent[] pointerEvents) {
        String recoResult = "";
        // Feed the editor
        batchEditor.pointerEvents(pointerEvents, false);
        batchEditor.waitForIdle();
        try {
            recoResult = batchEditor.export_(null, MimeType.TEXT);
        } catch (Exception e) {
            Log.e(TAG, "Failed to export recognition", e);
        }
        batchEditor.clear();
        return recoResult;
    }
}
