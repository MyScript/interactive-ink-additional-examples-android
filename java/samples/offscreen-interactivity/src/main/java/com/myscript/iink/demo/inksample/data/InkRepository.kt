// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.inksample.data

/**
 * Repository to read/write ink saved locally
 */
interface InkRepository {
    fun readInkFromFile(): String?
    fun saveInkToFile(jsonString: String)
}