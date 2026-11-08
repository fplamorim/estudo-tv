package com.fernandoamorim.estudotv

import android.content.Context
import android.net.Uri

/**
 * Onde a app aponta: Prod ou Stage do Portal de Estudo, e que endereços pode abrir.
 * O Stage muda-se em «Sobre» (5 vezes OK no logótipo) e fica guardado na app.
 */
class Ambiente(context: Context) {

    private val prefs = context.getSharedPreferences("ambiente", Context.MODE_PRIVATE)

    var stage: Boolean
        get() = prefs.getBoolean("stage", BuildConfig.STAGE_POR_OMISSAO)
        set(v) { prefs.edit().putBoolean("stage", v).apply() }

    /** Raiz do portal, sempre com «/» no fim. */
    val base: String
        get() = if (stage) BASE_STAGE else BASE_PROD

    /** Primeira página: o portal decide (QR, «Quem vai jogar?» ou a página do filho). */
    val inicio: String
        get() = "$base?tv=1"

    val sair: String
        get() = base + "entrar/entrar.php"

    val entrar: String
        get() = base + "entrar/?tv=1"

    /** Navegação (páginas): só o portal deste ambiente e os jogos do Sótão 57. */
    fun podeNavegar(url: String): Boolean {
        val u = Uri.parse(url)
        if (u.scheme != "https") return false
        val host = u.host ?: return false
        val caminho = u.path ?: "/"
        return when (host) {
            HOST_PORTAL -> caminho.startsWith(Uri.parse(base).path ?: "/estudo/")
            HOST_SOTAO, "www.$HOST_SOTAO" -> CAMINHOS_SOTAO.any { caminho.startsWith(it) } || caminho == "/"
            else -> false
        }
    }

    /** Pedidos de recursos (scripts, imagens, fetch): só destes dois domínios. */
    fun podeCarregar(url: String): Boolean {
        val u = Uri.parse(url)
        if (u.scheme == "data" || u.scheme == "blob" || u.scheme == "about") return true
        if (u.scheme != "https") return false
        return u.host == HOST_PORTAL || u.host == HOST_SOTAO || u.host == "www.$HOST_SOTAO"
    }

    fun eSotao(url: String?): Boolean {
        val h = url?.let { Uri.parse(it).host } ?: return false
        return h == HOST_SOTAO || h == "www.$HOST_SOTAO"
    }

    /** Página de jogo do Sótão 57 (onde os botões do comando passam diretos para o jogo). */
    fun eJogoSotao(url: String?): Boolean =
        eSotao(url) && (Uri.parse(url).path ?: "").startsWith("/games/")

    companion object {
        const val HOST_PORTAL = "fernandoamorim.com"
        const val HOST_SOTAO = "sotao57.com"
        const val BASE_PROD = "https://fernandoamorim.com/estudo/"
        const val BASE_STAGE = "https://fernandoamorim.com/estudo-stage/"
        val CAMINHOS_SOTAO = listOf("/pt/", "/br/", "/us/", "/games/")
    }
}
