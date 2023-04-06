// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.inksample.data

import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException

class InkRepositoryImpl(private val dataDir: File): InkRepository {

    private val inkFile: File
        get() = File(dataDir, "current.json")

    override fun readInkFromFile(): String? {
        return try {
            inkFile.bufferedReader().use(BufferedReader::readText)
        } catch (e: FileNotFoundException) {
            null
        }
    }

    override fun saveInkToFile(jsonString: String) {
        inkFile.writeText(jsonString)
    }
}