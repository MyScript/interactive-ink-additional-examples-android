// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.inksample

import android.app.Application
import com.myscript.certificate.MyCertificate
import com.myscript.iink.Engine
import java.io.File

class InkApplication: Application() {

    var engine: Engine? = null

    override fun onCreate() {
        super.onCreate()

        engine =  Engine.create(MyCertificate.getBytes()).apply {
            configuration.let { conf ->
                val confDir = "zip://${packageCodePath}!/assets/conf"
                conf.setStringArray("configuration-manager.search-path", arrayOf(confDir))
                val tempDir = File(cacheDir, "tmp")
                conf.setString("content-package.temp-folder", tempDir.absolutePath)
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        engine?.close()
        engine = null
    }
}