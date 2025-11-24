package com.myscript.draw_stroke_example;

import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.myscript.iink.Configuration;
import com.myscript.iink.ContentPackage;
import com.myscript.iink.ContentPart;
import com.myscript.iink.Editor;
import com.myscript.iink.Engine;
import com.myscript.iink.Renderer;
import com.myscript.iink.uireferenceimplementation.EditorBinding;
import com.myscript.iink.uireferenceimplementation.EditorData;
import com.myscript.iink.uireferenceimplementation.EditorView;
import com.myscript.iink.uireferenceimplementation.FontUtils;
import com.myscript.iink.uireferenceimplementation.ImageLoader;
import com.myscript.iink.uireferenceimplementation.InputController;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    Engine engine = IInkApplication.getEngine();

    Configuration conf = engine.getConfiguration();
    String confDir = "zip://" + getPackageCodePath() + "!/assets/conf";
    conf.setStringArray("configuration-manager.search-path", new String[] { confDir });
    String tempDir = getExternalFilesDir(null).getPath() + File.separator + "tmp";
    conf.setString("content-package.temp-folder", tempDir);

    EditorView editorView = findViewById(com.myscript.iink.uireferenceimplementation.R.id.editor_view);

    AssetManager assetManager = getApplicationContext().getAssets();
    Map<String, Typeface> typefaceMap = FontUtils.loadFontsFromAssets(assetManager);
    editorView.setTypefaces(typefaceMap);

    EditorBinding editorBinding = new EditorBinding(engine, typefaceMap);
    EditorData editorData = editorBinding.openEditor(editorView);

    Editor editor = editorView.getEditor();

    editorView.setImageLoader(new ImageLoader(editor));
    editorData.getInputController().setInputMode(InputController.INPUT_MODE_FORCE_PEN);

    StrokeView strokeView = findViewById(R.id.stroke_view);
    strokeView.setBackgroundColor(0x00ff0044);
    strokeView.setEditor(editor);

    File file = new File(getFilesDir(), "drawStroke.iink");
    file.delete();
    ContentPart contentPart;
    try {
        ContentPackage contentPackage = engine.createPackage(file);
        contentPart = contentPackage.createPart("Raw Content");
    } catch (IOException e) {
        throw new RuntimeException(e);
    }

    editorView.post(() -> {
      Renderer renderer = editorView.getRenderer();
      if (renderer != null)
      {
        renderer.setViewOffset(0, 0);
        editorView.getRenderer().setViewScale(1);
        editorView.setVisibility(View.VISIBLE);
        editor.setPart(contentPart);
      }
    });
  }
}
