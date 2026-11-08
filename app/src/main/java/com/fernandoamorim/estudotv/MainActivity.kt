package com.fernandoamorim.estudotv

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Estudo TV: WebView em ecrã inteiro com o Portal de Estudo (e os jogos do Sótão 57).
 * Comando da TV e comando PlayStation: setas/direcional = mover, OK/✕ = escolher, Voltar/○ = voltar,
 * botão PS, Menu ou Voltar mantido = menu da app.
 */
class MainActivity : Activity() {

    private lateinit var ambiente: Ambiente
    private lateinit var web: WebView
    private lateinit var faixaStage: TextView
    private lateinit var semRede: LinearLayout
    private lateinit var narrador: Narrador
    private lateinit var atualizador: Atualizador

    private val tarefas = Executors.newSingleThreadExecutor()
    private val principal = Handler(Looper.getMainLooper())
    private var erroNaPagina = false

    // Voltar mantido (1 s) abre o menu.
    private var voltarDesde = 0L
    private var voltarLongoUsado = false

    // Ecrã ligado enquanto se usa a app; 30 minutos sem teclas devolvem a proteção de ecrã à TV.
    private val inativo = Runnable { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ambiente = Ambiente(this)
        narrador = Narrador(this)
        atualizador = Atualizador(this)

        ecraInteiro()
        montarEcra()
        configurarWeb()

        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(web, false)
        // Como um browser que se fecha: sessões sem «Lembrar este aparelho» acabam quando a app arranca de novo.
        if (!jaArrancou) { cookies.removeSessionCookies(null); jaArrancou = true }

        Ponte(narrador, ambiente) { principal.post { abrirMenu() } }.ligar(web)
        web.loadUrl(ambiente.inicio)
        atividade()
        atualizador.verificarAoAbrir()
    }

    // ---------- ecrã ----------

    private fun ecraInteiro() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun montarEcra() {
        val raiz = FrameLayout(this)
        raiz.setBackgroundColor(getColor(R.color.fundo))

        web = WebView(this)
        web.setBackgroundColor(getColor(R.color.fundo))
        raiz.addView(web, FrameLayout.LayoutParams(-1, -1))

        faixaStage = TextView(this).apply {
            text = getString(R.string.faixa_stage)
            setTextColor(Color.BLACK)
            setBackgroundColor(getColor(R.color.amarelo))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(10), dp(3), dp(10), dp(3))
            isFocusable = false
        }
        raiz.addView(faixaStage, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(12), dp(24), 0)
        })

        semRede = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(getColor(R.color.fundo))
            visibility = View.GONE
            addView(TextView(context).apply {
                text = getString(R.string.sem_rede_titulo)
                setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f); gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = getString(R.string.sem_rede_texto)
                setTextColor(getColor(R.color.verde)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f); gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(24))
            })
            addView(Button(context).apply {
                text = getString(R.string.tentar_outra_vez)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setOnClickListener { semRede.visibility = View.GONE; web.reload() }
            }, LinearLayout.LayoutParams(-2, -2))
        }
        raiz.addView(semRede, FrameLayout.LayoutParams(-1, -1))

        setContentView(raiz)
        atualizarFaixa()
    }

    private fun atualizarFaixa() { faixaStage.visibility = if (ambiente.stage) View.VISIBLE else View.GONE }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configurarWeb() {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            setSupportMultipleWindows(false)   // «abrir noutro separador» abre aqui, sujeito à mesma lista
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = false
            userAgentString = "$userAgentString EstudoTV/${BuildConfig.VERSION_NAME} Android TV"
        }
        web.isFocusable = true
        web.isFocusableInTouchMode = true
        web.setDownloadListener { _, _, _, _, _ -> bloqueado() }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (ambiente.podeNavegar(url)) return false
                if (request.isForMainFrame) bloqueado()
                return true
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (ambiente.podeCarregar(request.url.toString())) return null
                return WebResourceResponse("text/plain", "utf-8", 403, "Bloqueado", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) { erroNaPagina = false }

            override fun onPageFinished(view: WebView, url: String) {
                if (!erroNaPagina) semRede.visibility = View.GONE
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                when (error.errorCode) {
                    ERROR_HOST_LOOKUP, ERROR_CONNECT, ERROR_TIMEOUT, ERROR_IO, ERROR_PROXY_AUTHENTICATION -> {
                        erroNaPagina = true
                        semRede.visibility = View.VISIBLE
                        semRede.getChildAt(2).requestFocus()
                    }
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                recreate(); return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) =
                callback.invoke(origin, false, false)
        }
    }

    private fun bloqueado() = Toast.makeText(this, R.string.link_bloqueado, Toast.LENGTH_LONG).show()

    // ---------- teclas: comando da TV e comando PlayStation ----------

    private fun atividade() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        principal.removeCallbacks(inativo)
        principal.postDelayed(inativo, 30 * 60 * 1000L)
    }

    private fun eComando(e: KeyEvent) =
        e.isFromSource(InputDevice.SOURCE_GAMEPAD) || e.isFromSource(InputDevice.SOURCE_JOYSTICK)

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        atividade()
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        atividade()
        val code = e.keyCode
        val noJogo = ambiente.eJogoSotao(web.url)

        // Menu da app: botão PS (sempre), tecla Menu, e Options/Start fora dos jogos do Sótão 57.
        if (code == KeyEvent.KEYCODE_BUTTON_MODE || code == KeyEvent.KEYCODE_MENU ||
            (code == KeyEvent.KEYCODE_BUTTON_START && !noJogo)) {
            if (e.action == KeyEvent.ACTION_UP) abrirMenu()
            return true
        }

        // Nos jogos do Sótão 57, os botões do comando passam diretos para o jogo (Gamepad API).
        if (eComando(e) && noJogo && code != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(e)

        // Fora dos jogos: ✕ = OK, ○ = Voltar (o direcional já chega como setas).
        if (eComando(e) && code == KeyEvent.KEYCODE_BUTTON_A) {
            web.dispatchKeyEvent(KeyEvent(e.downTime, e.eventTime, e.action, KeyEvent.KEYCODE_ENTER, e.repeatCount))
            return true
        }
        if (code == KeyEvent.KEYCODE_BACK || (eComando(e) && code == KeyEvent.KEYCODE_BUTTON_B)) {
            tratarVoltar(e); return true
        }
        return super.dispatchKeyEvent(e)
    }

    private fun tratarVoltar(e: KeyEvent) {
        when (e.action) {
            KeyEvent.ACTION_DOWN -> {
                if (e.repeatCount == 0) { voltarDesde = SystemClock.uptimeMillis(); voltarLongoUsado = false }
                else if (!voltarLongoUsado && SystemClock.uptimeMillis() - voltarDesde > 900) {
                    voltarLongoUsado = true; abrirMenu()
                }
            }
            KeyEvent.ACTION_UP -> if (!voltarLongoUsado) voltar()
        }
    }

    /** Nos jogos do Sótão 57, o jogo trata primeiro (window.sotao57TV.voltar()); depois, a navegação. */
    private fun voltar() {
        if (semRede.visibility == View.VISIBLE) { confirmarFechar(); return }
        if (ambiente.eSotao(web.url)) {
            web.evaluateJavascript(
                "(function(){try{return !!(window.sotao57TV&&window.sotao57TV.voltar&&window.sotao57TV.voltar())}catch(e){return false}})()"
            ) { r -> if (r != "true") navegarAtras() }
        } else navegarAtras()
    }

    private fun navegarAtras() {
        val url = web.url ?: ""
        when {
            web.canGoBack() -> web.goBack()
            !url.startsWith(ambiente.inicio) -> web.loadUrl(ambiente.inicio)
            else -> confirmarFechar()
        }
    }

    private fun confirmarFechar() {
        AlertDialog.Builder(this, R.style.Dialogo)
            .setTitle(R.string.fechar_titulo)
            .setPositiveButton(R.string.fechar_sim) { _, _ -> finish() }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    // ---------- menu da app ----------

    private fun abrirMenu() {
        val itens = arrayOf(
            getString(R.string.menu_inicio), getString(R.string.menu_sair),
            getString(R.string.menu_atualizar), getString(R.string.menu_sobre),
        )
        AlertDialog.Builder(this, R.style.Dialogo)
            .setTitle(R.string.menu_titulo)
            .setItems(itens) { _, i ->
                when (i) {
                    0 -> { web.clearHistory(); web.loadUrl(ambiente.inicio) }
                    1 -> confirmarSair()
                    2 -> atualizador.verificar(manual = true)
                    3 -> sobre()
                }
            }
            .show()
    }

    private fun confirmarSair() {
        AlertDialog.Builder(this, R.style.Dialogo)
            .setTitle(R.string.sair_titulo)
            .setMessage(R.string.sair_texto)
            .setPositiveButton(R.string.sair_sim) { _, _ -> sair() }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    /** Chama o «Sair» do portal (termina a sessão no servidor) e apaga os cookies da app. */
    private fun sair() {
        val url = ambiente.sair
        val cookie = CookieManager.getInstance().getCookie(url)
        tarefas.execute {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 15000; c.readTimeout = 15000
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("X-Estudo", "1")
                c.setRequestProperty("User-Agent", web.settings.userAgentString)
                if (cookie != null) c.setRequestProperty("Cookie", cookie)
                c.outputStream.use { it.write("{\"acao\":\"sair\"}".toByteArray()) }
                c.responseCode
                c.disconnect()
            } catch (_: Exception) { /* sem rede: os cookies são apagados na mesma */ }
            principal.post {
                CookieManager.getInstance().removeAllCookies {
                    CookieManager.getInstance().flush()
                    web.clearHistory()
                    web.loadUrl(ambiente.entrar)
                }
            }
        }
    }

    /** «Sobre»: versão e ambiente. 5 vezes OK no logótipo alternam entre Prod e Stage. */
    private fun sobre() {
        var toques = 0
        val texto = TextView(this).apply {
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }
        fun escrever() {
            texto.text = getString(
                R.string.sobre_texto, BuildConfig.VERSION_NAME,
                getString(if (ambiente.stage) R.string.ambiente_stage else R.string.ambiente_prod)
            )
        }
        escrever()
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.logo)
            isFocusable = true
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnFocusChangeListener { v, f -> v.setBackgroundColor(if (f) getColor(R.color.amarelo) else Color.TRANSPARENT) }
            setOnClickListener {
                toques++
                if (toques >= 5) {
                    toques = 0
                    ambiente.stage = !ambiente.stage
                    escrever(); atualizarFaixa()
                    val nome = getString(if (ambiente.stage) R.string.ambiente_stage else R.string.ambiente_prod)
                    Toast.makeText(this@MainActivity, getString(R.string.ambiente_mudou, nome), Toast.LENGTH_LONG).show()
                    web.clearHistory(); web.loadUrl(ambiente.inicio)
                }
            }
        }
        val caixa = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(16), 0, 0)
            addView(logo, LinearLayout.LayoutParams(dp(96), dp(96)))
            addView(texto)
        }
        AlertDialog.Builder(this, R.style.Dialogo)
            .setTitle(R.string.sobre_titulo)
            .setView(caixa)
            .setPositiveButton(R.string.sobre_fechar, null)
            .show()
    }

    // ---------- ciclo de vida ----------

    override fun onResume() { super.onResume(); web.onResume(); atividade() }

    override fun onPause() {
        CookieManager.getInstance().flush()
        web.onPause()
        narrador.parar()
        super.onPause()
    }

    override fun onDestroy() {
        principal.removeCallbacks(inativo)
        narrador.fechar()
        web.destroy()
        super.onDestroy()
    }

    companion object {
        private var jaArrancou = false
    }
}
