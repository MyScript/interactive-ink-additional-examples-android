package com.myscript.iink.samples.search

import android.app.Application
import com.myscript.certificate.MyCertificate
import com.myscript.iink.Engine

class MyIInkApplication : Application() {


    companion object {

        private var engine: Engine? = null

        fun getEngine(): Engine? {
            if (MyIInkApplication.engine == null) {
                engine = Engine.create(MyCertificate.getBytes())
            }
            return engine
        }

        fun close() {
            engine?.close()
        }
    }

    override fun onTerminate() {
        close()
        super.onTerminate()
    }
}