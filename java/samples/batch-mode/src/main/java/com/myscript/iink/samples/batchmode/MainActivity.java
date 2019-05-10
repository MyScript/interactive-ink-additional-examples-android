/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.batchmode;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;

import com.google.gson.Gson;
import com.myscript.iink.Configuration;
import com.myscript.iink.ContentPackage;
import com.myscript.iink.ContentPart;
import com.myscript.iink.Editor;
import com.myscript.iink.Engine;
import com.myscript.iink.MimeType;
import com.myscript.iink.PointerEvent;
import com.myscript.iink.PointerEventType;
import com.myscript.iink.Renderer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    // To enable text recognition for a specific language,
    // see also: https://developer.myscript.com/support/recognition-assets
    private static final String language = "en_US";
    private static final String packageName = "package.iink";
    private static final String exportFileName = "export";
    // Choose type of content ("Text", "Math", "Diagram", "Raw Content")
    private static String partType = "Text";
    private Engine engine;
    private Editor editor;
    private DisplayMetrics displayMetrics;
    private ContentPackage contentPackage;
    private ContentPart contentPart;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        engine = IInkApplication.getEngine();

        // Configure recognition
        Configuration conf = engine.getConfiguration();
        String confDir = "zip://" + getPackageCodePath() + "!/assets/conf";

        conf.setStringArray("configuration-manager.search-path", new String[]{confDir});
        conf.setString("content-package.temp-folder", getFilesDir().getPath() + File.separator + "tmp");
        conf.setString("lang", language);

        // Configure the engine to disable guides (recommended)
        conf.setBoolean("text.guides.enable", false);

        // Create a renderer with a null render target
        displayMetrics = getResources().getDisplayMetrics();
        Renderer renderer = engine.createRenderer(displayMetrics.xdpi, displayMetrics.ydpi, null);

        // Create the editor
        editor = engine.createEditor(renderer);

        // The editor requires a font metrics provider and a view size *before* calling setPart()
        editor.setFontMetricsProvider(null);
        editor.setViewSize(displayMetrics.widthPixels, displayMetrics.heightPixels);

        // Load the pointerEvents from the right .json file depending of the part type
        PointerEvent[] pointerEvents = loadPointerEvents();

        try {
            // Create a new package
            contentPackage = engine.createPackage(packageName);

            // Create a new part
            contentPart = contentPackage.createPart(partType);
        } catch (IOException e) {
            Log.e(TAG, "Failed to open package \"" + packageName + "\"", e);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to open package \"" + packageName + "\"", e);
        }

        // Associate editor with the new part
        editor.setPart(contentPart);

        // Feed the editor
        editor.pointerEvents(pointerEvents, false);

        // Export the result of the recognition into a file
        export();

        finish();
    }

    @Override
    protected void onDestroy() {
        // IInkApplication has the ownership, do not close here
        engine = null;

        super.onDestroy();
    }

    void export() {
        MimeType mimeType = MimeType.TEXT;

        switch (partType) {
            case "Math":
                mimeType = MimeType.LATEX;
                break;

            case "Diagram":
                mimeType = MimeType.SVG;
                break;

            case "Raw Content":
                mimeType = MimeType.JIIX;
                break;

            default:
                break;
        }

        // Exported file is stored in the Virtual SD Card : "Android/data/com.myscript.iink.batchmode/files"
        File file = new File(getExternalFilesDir(null), File.separator + exportFileName + mimeType.getFileExtensions());
        editor.waitForIdle();

        try {
            editor.export_(null, file.getAbsolutePath(), mimeType, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        editor.setPart(null);
        contentPart.close();
        contentPackage.close();

        try {
            engine.deletePackage(packageName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private PointerEvent[] loadPointerEvents() {
        PointerEvent[] pointerEvents = null;
        String pointerEventsPath = "conf/pointerEvents/" + partType.toLowerCase() + "/pointerEvents.json";

        if (partType.equals("Text")) {
            pointerEventsPath = "conf/pointerEvents/" + partType.toLowerCase() + "/" + language + "/pointerEvents.json";
        }

        try {
            // Loading the content of the pointerEvents JSON file
            InputStream inputStream = getResources().getAssets().open(pointerEventsPath);

            // Mapping the content into a JsonResult class
            JsonResult jsonResult = new Gson().fromJson(new InputStreamReader(inputStream), JsonResult.class);

            int pointerEventsCount = 0;

            // Calculating the number of pointerEvents
            for (Stroke stroke : jsonResult.getStrokes()) {
                pointerEventsCount += stroke.getX().length;
            }

            // Allocating the size of the array of pointerEvents
            pointerEvents = new PointerEvent[pointerEventsCount];

            int i = 0;

            for (Stroke stroke : jsonResult.getStrokes()) {
                float[] strokeX = stroke.getX();
                float[] strokeY = stroke.getY();
                long[] strokeT = stroke.getT();
                float[] strokeP = stroke.getP();
                int length = stroke.getX().length;

                for (int j = 0; j < length; j++) {
                    PointerEvent pointerEvent = new PointerEvent();
                    pointerEvent.pointerType = stroke.getPointerType();
                    pointerEvent.pointerId = stroke.getPointerId();

                    if (j == 0) {
                        pointerEvent.eventType = PointerEventType.DOWN;
                    } else if (j == length - 1) {
                        pointerEvent.eventType = PointerEventType.UP;
                    } else {
                        pointerEvent.eventType = PointerEventType.MOVE;
                    }

                    // Transform the x and y coordinates of the stroke from mm to px
                    // This is needed to be adaptive for each device
                    pointerEvent.x = (strokeX[j] / 25.4f) * displayMetrics.xdpi;
                    pointerEvent.y = (strokeY[j] / 25.4f) * displayMetrics.ydpi;

                    pointerEvent.t = strokeT[j];
                    pointerEvent.f = strokeP[j];
                    pointerEvents[i++] = pointerEvent;
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return pointerEvents;
    }
}
