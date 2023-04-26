package com.myscript.iink.demo.inksample.data

interface InkRepository {
    fun readInkFromFile(): String?
    fun saveInkToFile(jsonString: String)
}