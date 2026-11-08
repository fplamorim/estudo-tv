package com.fernandoamorim.estudotv

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Voz do próprio Android (TextToSpeech), em português de Portugal. Corre na TV, sem enviar nada para fora. */
class Narrador(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var pronto = false

    override fun onInit(estado: Int) {
        if (estado != TextToSpeech.SUCCESS) return
        val r = tts.setLanguage(Locale.forLanguageTag("pt-PT"))
        pronto = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
    }

    /** Devolve false se não houver voz portuguesa: a página mostra só o texto. */
    fun falar(texto: String): Boolean {
        if (!pronto || texto.isBlank()) return false
        tts.speak(texto.take(1000), TextToSpeech.QUEUE_FLUSH, null, "estudo")
        return true
    }

    fun parar() { if (pronto) tts.stop() }

    fun temVoz() = pronto

    fun fechar() = tts.shutdown()
}
