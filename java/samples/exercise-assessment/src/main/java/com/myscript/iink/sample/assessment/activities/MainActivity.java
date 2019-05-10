/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.sample.assessment.activities;

import android.app.Application;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.myscript.iink.Configuration;
import com.myscript.iink.ContentPackage;
import com.myscript.iink.ContentPart;
import com.myscript.iink.ConversionState;
import com.myscript.iink.Editor;
import com.myscript.iink.Engine;
import com.myscript.iink.IEditorListener;
import com.myscript.iink.app.common.IInteractiveInkApplication;
import com.myscript.iink.app.common.activities.ErrorActivity;
import com.myscript.iink.sample.assessment.R;
import com.myscript.iink.uireferenceimplementation.EditorView;
import com.myscript.iink.uireferenceimplementation.FontUtils;
import com.myscript.iink.uireferenceimplementation.InputController;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements IEditorListener {

    private static final String IINK_PACKAGE_NAME = "my_iink_package";

    private Engine engine;
    private ContentPackage contentPackage;
    private ContentPart answerContentPart1;
    private ContentPart answerContentPart2;
    private ContentPart scoreContentPart1;
    private ContentPart scoreContentPart2;
    private EditorView answerEditorView1;
    private EditorView answerEditorView2;
    private EditorView scoreEditorView1;
    private EditorView scoreEditorView2;
    private EditorView activeEditorView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ErrorActivity.setExceptionHandler(this);
        Application application = getApplication();
        if (!(application instanceof IInteractiveInkApplication)) return;
        engine = ((IInteractiveInkApplication) application).getEngine();
        File myPackageFile = new File(getFilesDir(), IINK_PACKAGE_NAME);
        try {
            contentPackage = engine.createPackage(myPackageFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (contentPackage == null) return;
        initWith(contentPackage);
        View problemSolver1 = findViewById(R.id.problemSolver1);
        View problemSolver2 = findViewById(R.id.problemSolver2);
        answerEditorView1 =
                problemSolver1.findViewById(R.id.answerEditor).findViewById(R.id.editor_view);
        initWith(answerEditorView1);
        answerEditorView2 =
                problemSolver2.findViewById(R.id.answerEditor).findViewById(R.id.editor_view);
        initWith(answerEditorView2);
        scoreEditorView1 =
                problemSolver1.findViewById(R.id.scoreEditor).findViewById(R.id.editor_view);
        initWith(scoreEditorView1);
        scoreEditorView2 =
                problemSolver2.findViewById(R.id.scoreEditor).findViewById(R.id.editor_view);
        initWith(scoreEditorView2);
    }

    private void initWith(@NonNull final EditorView editorView) {
        editorView.setEngine(engine);
        final Editor editor = editorView.getEditor();
        if (editor == null) return;
        editor.addListener(this);
        // TODO: try different input modes:
        // - InputController.INPUT_MODE_AUTO
        // - InputController.INPUT_MODE_NONE
        // - InputController.INPUT_MODE_FORCE_PEN
        // - InputController.INPUT_MODE_FORCE_TOUCH
        editorView.setInputMode(InputController.INPUT_MODE_FORCE_PEN);
        editorView.setTypefaces(FontUtils.loadFontsFromAssets(getApplicationContext().getAssets()));
        // attach content part to the editor.
        editorView.post(new Runnable() {
            @Override
            public void run() {
                final ContentPart contentPart;
                if (editorView == answerEditorView1) contentPart = answerContentPart1;
                else if (editorView == answerEditorView2) contentPart = answerContentPart2;
                else if (editorView == scoreEditorView1) contentPart = scoreContentPart1;
                else if (editorView == scoreEditorView2) contentPart = scoreContentPart2;
                else return;
                Configuration configuration = editor.getConfiguration();
                switch (contentPart.getType()) {
                    case "Math":
                        // disable math solver result
                        configuration.setBoolean("math.solver.enable", false);
                        break;
                    case "Text":
                        // disable text guide lines
                        configuration.setBoolean("text.guides.enable", false);
                        break;
                    default:
                        break;
                }
                editor.setPart(contentPart);
                editorView.setVisibility(View.VISIBLE);
            }
        });
    }

    private void initWith(@NonNull ContentPackage contentPackage) {
        // TODO: try different part types: Diagram, Drawing, Math, Text, Text Document.
        answerContentPart1 = contentPackage.createPart("Math");
        answerContentPart2 = contentPackage.createPart("Text");
        scoreContentPart1 = contentPackage.createPart("Text");
        scoreContentPart2 = contentPackage.createPart("Text");
    }

    @Override
    protected void onDestroy() {
        answerContentPart1.close();
        answerEditorView1.close();
        scoreContentPart1.close();
        scoreEditorView1.close();
        answerContentPart2.close();
        answerEditorView2.close();
        scoreContentPart2.close();
        scoreEditorView2.close();
        contentPackage.close();
        super.onDestroy();
    }

    // region implementations (options menu)

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (activeEditorView == null) return super.onPrepareOptionsMenu(menu);
        Editor editor = activeEditorView.getEditor();
        menu.findItem(R.id.menu_redo).setEnabled(editor != null && editor.canRedo());
        menu.findItem(R.id.menu_undo).setEnabled(editor != null && editor.canUndo());
        if (editor == null) return super.onPrepareOptionsMenu(menu);
        ContentPart contentPart = editor.getPart();
        menu.findItem(R.id.menu_clear).setEnabled(contentPart != null && !contentPart.isClosed());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (activeEditorView == null) return super.onOptionsItemSelected(item);
        Editor editor = activeEditorView.getEditor();
        if (editor != null) {
            if (!editor.isIdle()) editor.waitForIdle();
            switch (item.getItemId()) {
                case R.id.menu_clear:
                    editor.clear();
                    break;
                case R.id.menu_convert:
                    ConversionState[] conversionStates =
                            editor.getSupportedTargetConversionStates(null);
                    if (conversionStates.length != 0)
                        editor.convert(null, conversionStates[0]);
                    break;
                case R.id.menu_redo:
                    editor.redo();
                    break;
                case R.id.menu_undo:
                    editor.undo();
                    break;
                default:
                    break;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    // endregion

    // region implementations (IEditorListener)

    @Override
    public void partChanging(Editor editor, ContentPart oldPart, ContentPart newPart) {
        invalidateOptionsMenu();
    }

    @Override
    public void partChanged(Editor editor) {
        invalidateOptionsMenu();
    }

    @Override
    public void contentChanged(Editor editor, String[] blockIds) {
        invalidateOptionsMenu();
        if (editor == answerEditorView1.getEditor()) activeEditorView = answerEditorView1;
        else if (editor == answerEditorView2.getEditor()) activeEditorView = answerEditorView2;
        else if (editor == scoreEditorView1.getEditor()) activeEditorView = scoreEditorView1;
        else if (editor == scoreEditorView2.getEditor()) activeEditorView = scoreEditorView2;
        else activeEditorView = null;
    }

    @Override
    public void onError(final Editor editor, String blockId, final String message) {
        if (activeEditorView == null) return;
        activeEditorView.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activeEditorView.getContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    // endregion
}
