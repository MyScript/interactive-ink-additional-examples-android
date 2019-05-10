/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.app.common;

import android.support.annotation.NonNull;

import com.myscript.iink.Engine;

/**
 * Defines an iink application containing a runtime {@link Engine}.
 */
public interface IInteractiveInkApplication {

    @NonNull
    Engine getEngine();

}
