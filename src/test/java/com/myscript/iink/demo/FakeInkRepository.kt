// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo

import com.myscript.iink.demo.inksample.data.InkRepository

class FakeInkRepository: InkRepository {

    private var savedJson: String? = null

    override fun readInkFromFile(): String? {
        return savedJson
    }

    override fun saveInkToFile(jsonString: String) {
        savedJson = jsonString
    }
}