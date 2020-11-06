/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.app.common;

import com.myscript.iink.Engine;
import androidx.annotation.NonNull;

/**
 * Defines an iink application containing a runtime {@link Engine}.
 */
public interface IInteractiveInkApplication {

    @NonNull
    Engine getEngine();

}
