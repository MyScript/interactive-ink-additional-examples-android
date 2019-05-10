/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.sample.assessment;

import android.app.Application;
import android.support.annotation.NonNull;

import com.myscript.certificate.MyCertificate;
import com.myscript.iink.Configuration;
import com.myscript.iink.Engine;
import com.myscript.iink.app.common.IInteractiveInkApplication;

import java.io.File;

/**
 * Please replace the content of {@link MyCertificate} with a valid certificate you received from
 * the developer portal.
 */
public class MyApplication extends Application implements IInteractiveInkApplication {

    private Engine engine;

    @Override
    public void onTerminate() {
        // release native resources.
        engine.close();
        super.onTerminate();
    }

    @NonNull
    @Override
    public Engine getEngine() {
        if (engine == null)
            engine = onCreateEngine();
        return engine;
    }

    private Engine onCreateEngine() {
        // create an engine with a valid certificate.
        Engine engine = Engine.create(MyCertificate.getBytes());
        // initial configuration.
        Configuration configuration = engine.getConfiguration();
        // configure the directories where to find *.conf.
        configuration.setStringArray(
                "configuration-manager.search-path",
                new String[]{"zip://" + getPackageCodePath() + "!/assets/conf"}
        );
        // configure a temporary directory.
        configuration.setString(
                "content-package.temp-folder",
                getFilesDir().getPath() + File.separator + "tmp"
        );
        return engine;
    }
}
