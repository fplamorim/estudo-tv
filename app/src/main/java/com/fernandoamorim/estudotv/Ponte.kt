package com.fernandoamorim.estudotv

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/**
 * Ponte entre o portal e a app, só para a origem https://fernandoamorim.com.
 * No portal: window.EstudoTV.postMessage(JSON.stringify({acao:"falar", texto:"…"})).
 * Ações: falar, parar, menu, info (responde {versao, ambiente, voz}).
 */
class Ponte(private val narrador: Narrador, private val ambiente: Ambiente, private val abrirMenu: () -> Unit) {

    fun ligar(web: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(web, "EstudoTV", setOf("https://" + Ambiente.HOST_PORTAL)) {
                _: WebView, msg: WebMessageCompat, origem: Uri, principal: Boolean, resposta: JavaScriptReplyProxy ->
            if (!principal || origem.host != Ambiente.HOST_PORTAL) return@addWebMessageListener
            val j = try { JSONObject(msg.data ?: "{}") } catch (e: Exception) { return@addWebMessageListener }
            when (j.optString("acao")) {
                "falar" -> narrador.falar(j.optString("texto"))
                "parar" -> narrador.parar()
                "menu" -> abrirMenu()
                "info" -> resposta.postMessage(
                    JSONObject()
                        .put("versao", BuildConfig.VERSION_NAME)
                        .put("ambiente", if (ambiente.stage) "stage" else "prod")
                        .put("voz", narrador.temVoz())
                        .toString()
                )
            }
        }
    }
}
