package com.myscript.iink.samples.batchmode

import android.app.Application
import com.myscript.certificate.MyCertificate
import com.myscript.iink.Engine

class IInkApplication : Application() {


    companion object {

        private var engine: Engine? = null

        fun getEngine(): Engine? {
            if (IInkApplication.engine == null) {
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