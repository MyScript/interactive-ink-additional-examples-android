// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.samples.hwgeneration

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myscript.iink.ContentSelection
import com.myscript.iink.Engine
import com.myscript.iink.HandwritingGenerator
import com.myscript.iink.HandwritingGeneratorError
import com.myscript.iink.HandwritingResult
import com.myscript.iink.IHandwritingGeneratorListener
import com.myscript.iink.MimeType
import com.myscript.iink.PointerEvent
import com.myscript.iink.PredefinedHandwritingProfileId
import com.myscript.iink.graphics.Transform
import com.myscript.iink.samples.hwgeneration.None.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

open class HWRResult(val word: String, val events: Array<PointerEvent>)
object None: HWRResult("", emptyArray())

data class Message(
    val type: Type,
    val message: String,
    val exception: Exception? = null,
) {
    enum class Type { NOTIFICATION, ERROR }

    internal val id = UUID.randomUUID()
}

data class GenerationProfile(
    val id: PredefinedHandwritingProfileId = PredefinedHandwritingProfileId.DEFAULT,
    val profilePath: String? = null) {

    companion object {
        fun fromId(id: PredefinedHandwritingProfileId): GenerationProfile {
            return GenerationProfile(id, null)
        }

        fun fromPath(path: String): GenerationProfile {
            return GenerationProfile(PredefinedHandwritingProfileId.DEFAULT, path)
        }
    }
}

class GenerationViewModel(application: Application, private val engine: Engine) : AndroidViewModel(application) {

    private val generator: HandwritingGenerator

    private val _isGenerating = MutableLiveData(false)
    val isGenerating: LiveData<Boolean>
        get() = _isGenerating

    private val _isProfileBuilding = MutableLiveData(false)
    val isProfileBuilding: LiveData<Boolean>
        get() = _isProfileBuilding

    private val _hwrResults = MutableLiveData<List<HWRResult>>()
    val hwrResults: LiveData<List<HWRResult>>
        get() = _hwrResults

    private var _message = MutableLiveData<Message?>(null)
    val message: LiveData<Message?>
        get() = _message

    init {
        _isGenerating.value = false

        generator = engine.createHandwritingGenerator()
    }

    private interface HWResultListener {
        fun onResult(result: HWRResult)
    }

    fun cancel() {
        generator.cancel()
        viewModelScope.launch(Dispatchers.Main) {
            _isGenerating.value = false
            _hwrResults.value = emptyList()
        }
    }

    fun getProfiles(): List<GenerationProfile> {
        val userProfiles = File(getApplication<Application>().filesDir, PROFILE_FOLDER).listFiles()?.map { file ->
            GenerationProfile.fromPath(file.absolutePath)
        } ?: emptyList()

        val predefinedProfiles = PredefinedHandwritingProfileId.entries.toList().map {
            GenerationProfile.fromId(it)
        }

        return userProfiles + predefinedProfiles
    }

    fun buildProfile(iinkFile: File) {
        buildProfileInternal(iinkFile = iinkFile)
    }

    fun buildProfile(selection: ContentSelection) {
        buildProfileInternal(selection = selection)
    }

    private fun buildProfileInternal(selection: ContentSelection? = null, iinkFile: File? = null) {
        viewModelScope.launch(Dispatchers.Main) {
            _isProfileBuilding.value = true

            withContext(Dispatchers.IO) {
                val builder = generator.createHandwritingProfileBuilder()

                val profile = try {
                    if (selection != null) {
                        builder.createFromSelection(selection)
                    } else if (iinkFile != null && iinkFile.exists()) {
                        builder.createFromFile(iinkFile.absolutePath)
                    } else {
                        return@withContext
                    }
                } catch (e: IllegalArgumentException) {
                    notify(Message(Message.Type.ERROR, "Error while building profile ${e.message}", e))
                    return@withContext
                }

                val profileDirectory = File(getApplication<Application>().filesDir, PROFILE_FOLDER)
                if (!profileDirectory.exists()) {
                    profileDirectory.mkdirs()
                }
                val profileId = UUID.randomUUID()
                val profileFile = File(profileDirectory, "$profileId.profile")
                builder.store(profile, profileFile.absolutePath)

                notify(Message(Message.Type.NOTIFICATION, "Profile built with ID $profileId"))
            }

            _isProfileBuilding.value = false
        }
    }

    fun generateHandwriting(inputSentence: String, generationProfile: GenerationProfile, inputTextSize: Float, inputX: Float, inputY: Float, width: Float, transform: Transform) {
        if (inputSentence.isEmpty()) return

        viewModelScope.launch(Dispatchers.Main) {
            _isGenerating.value = true

            withContext(Dispatchers.IO) {
                generate(inputSentence, generationProfile, inputTextSize, inputX, inputY, width, transform, object : HWResultListener {
                    override fun onResult(result: HWRResult) {
                        viewModelScope.launch(Dispatchers.Main) {
                            val previousList: MutableList<HWRResult> = _hwrResults.value?.toMutableList() ?: mutableListOf()
                            previousList.add(result)
                            _hwrResults.value = previousList
                        }
                    }
                })
            }
            _isGenerating.value = false
        }
    }

    private fun generate(sentence: String, generationProfile: GenerationProfile, textSize: Float, xOffset: Float, yOffset: Float, width: Float, transform: Transform, listener: HWResultListener): HWRResult {
        val builder = generator.createHandwritingProfileBuilder()

        val profile = if (generationProfile.profilePath != null && File(generationProfile.profilePath).exists()) {
            builder.load(generationProfile.profilePath)
        } else {
            builder.createFromId(generationProfile.id)
        }

        val indexLock = Any()
        var currentPointerEventIndex = 0
        var wordIndex = 0

        val words = sentence.split(" ").filter { it.isNotEmpty() }

        generator.setListener(object: IHandwritingGeneratorListener {
            override fun onPartialResult(generator: HandwritingGenerator, result: HandwritingResult) {
                val allEvents = result.toPointerEvents(transform)
                val newEvents = allEvents.drop(synchronized(indexLock) { currentPointerEventIndex })

                listener.onResult(HWRResult(words[wordIndex], newEvents.toTypedArray()))

                synchronized(indexLock) {
                    currentPointerEventIndex += newEvents.size
                    wordIndex++
                }
            }
            override fun onEnd(generator: HandwritingGenerator) = Unit
            override fun onError(generator: HandwritingGenerator, code: HandwritingGeneratorError, message: String) {
                notify(Message(Message.Type.ERROR, "Error $code due to $message"))
            }
            override fun onUnsupportedCharacter(generator: HandwritingGenerator, label: String, character: String, index: Int) = Unit
        })

        generator.start("Text", profile, engine.createParameterSet().apply {
            setBoolean("handwriting-generation.session.force-new-line", false)

            val invertTransform = Transform(transform).apply {
                invert()
            }
            val offsetMM = invertTransform.apply(xOffset, yOffset)
            val widthMM = invertTransform.apply(width, 0f)
            setNumber("handwriting-generation.session.width-mm", widthMM.x - offsetMM.x)
            setNumber("handwriting-generation.session.left-x-mm", offsetMM.x)
            setNumber("handwriting-generation.session.origin-y-mm", offsetMM.y)

            setNumber("handwriting-generation.session.line-gap-mm", textSize * LINE_GAP_RATIO)
            setNumber("handwriting-generation.session.x-height-mm", textSize)
        })

        words.forEach { word ->
            generator.add(word, MimeType.TEXT)
        }

        generator.end()
        generator.waitForIdle()

        val result = generator.getResult()
        val events = result.toPointerEvents(transform)

        return HWRResult(word, events)
    }

    private fun notify(message: Message) {
        synchronized(_message) {
            viewModelScope.launch(Dispatchers.Main) {
                _message.value = message
            }
        }
    }

    fun dismissMessage(message: Message) {
        synchronized(_message) {
            viewModelScope.launch(Dispatchers.Main) {
                val currentError = _message.value
                if (currentError?.id == message.id) {
                    _message.value = null
                }
            }
        }
    }

    companion object {
        private const val LINE_GAP_RATIO = 3

        private const val PROFILE_FOLDER = "profiles"
    }
}

class GenerationViewModelFactory(private val application: Application, private val engine: Engine) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(GenerationViewModel::class.java) -> {
                GenerationViewModel(application, engine) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel $modelClass")
        }
    }
}
