package com.myscript.iink.samples.assessment

import android.app.Application
import android.util.Log
import com.myscript.certificate.MyCertificate
import com.myscript.iink.Engine
import java.io.File
import java.io.IOException

class MyIInkApplication : Application() {

    private val TAG = "MyIInkApplication"

    companion object {
        private val language = "en_US"

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

    override fun onCreate() {
        super.onCreate()
        getEngine()?.apply {
            // configure recognition
            configuration.let { conf ->
                val confDir = "zip://${packageCodePath}!/assets/conf"
                conf.setStringArray("configuration-manager.search-path", arrayOf(confDir))
                val tempDir = File(cacheDir, "tmp")
                conf.setString("content-package.temp-folder", tempDir.absolutePath)

                // To enable text recognition for a specific language,
                conf.setString("lang", language);
            }

            // this create a dynamic custom grammar for mathematics
            // this custom grammar should have been referenced in the math.conf
            // the res dir of the grammar compiled file will be :
            // baseContext.filesDir.path -> /data/user/0/com.myscript.iink.samples.assessment/files
            // NB : graddle automatically copy customized math.conf in asset
            try {
                MathGrammarK8DynamicRes().build(this,baseContext.filesDir.path)
            }catch (e: IOException) {
                Log.e(TAG, "Failed to save custom grammar : ${e.message}")
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed to compile custom grammar (wrong argument): ${e.message}")
            } catch (e: RuntimeException) {
                Log.e(TAG, "Failed to compile custom grammar : ${e.message}")
            }
        }
    }
    override fun onTerminate() {
        close()
        super.onTerminate()
    }
}