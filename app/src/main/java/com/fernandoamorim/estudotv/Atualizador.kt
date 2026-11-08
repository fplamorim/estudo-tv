package com.fernandoamorim.estudotv

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Atualizações pela API pública de Releases do GitHub (fplamorim/estudo-tv).
 * O pedido não leva dados nem cookies. O APK é verificado (tamanho e SHA-256 publicados pelo GitHub)
 * e instalado pelo instalador do Android, que só aceita a mesma assinatura.
 */
class Atualizador(private val activity: Activity) {

    private val prefs = activity.getSharedPreferences("atualizacoes", Context.MODE_PRIVATE)
    private val trabalho = Executors.newSingleThreadExecutor()

    private data class Versao(val nome: String, val notas: String, val url: String, val tamanho: Long, val sha256: String?)

    /** Ao abrir: no máximo de 6 em 6 horas; «Mais tarde» só volta a perguntar no dia seguinte. */
    fun verificarAoAbrir() {
        val agora = System.currentTimeMillis()
        if (agora - prefs.getLong("ultima", 0) < 6 * HORA) return
        if (agora < prefs.getLong("adiar_ate", 0)) return
        prefs.edit().putLong("ultima", agora).apply()
        verificar(manual = false)
    }

    fun verificar(manual: Boolean) {
        trabalho.execute {
            val v = try { ultimaVersao() } catch (e: Exception) { null }
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                when {
                    v != null && maisNova(v.nome, BuildConfig.VERSION_NAME) -> perguntar(v)
                    manual && v != null -> aviso(activity.getString(R.string.atualizacao_em_dia, BuildConfig.VERSION_NAME))
                    manual -> aviso(activity.getString(R.string.atualizacao_erro))
                }
            }
        }
    }

    private fun ultimaVersao(): Versao? {
        val c = abrir(API)
        c.setRequestProperty("Accept", "application/vnd.github+json")
        if (c.responseCode != 200) return null
        val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val ativos = j.getJSONArray("assets")
        for (i in 0 until ativos.length()) {
            val a = ativos.getJSONObject(i)
            if (a.getString("name") == NOME_APK) {
                val digest = a.optString("digest", "")
                return Versao(
                    nome = j.getString("tag_name").removePrefix("v"),
                    notas = j.optString("body", "").take(600),
                    url = a.getString("browser_download_url"),
                    tamanho = a.optLong("size", -1),
                    sha256 = digest.removePrefix("sha256:").takeIf { digest.startsWith("sha256:") },
                )
            }
        }
        return null
    }

    private fun perguntar(v: Versao) {
        AlertDialog.Builder(activity, R.style.Dialogo)
            .setTitle(activity.getString(R.string.atualizacao_titulo, v.nome))
            .setMessage(v.notas.ifBlank { " " })
            .setPositiveButton(R.string.atualizacao_atualizar) { _, _ -> atualizar(v) }
            .setNegativeButton(R.string.atualizacao_mais_tarde) { _, _ ->
                prefs.edit().putLong("adiar_ate", System.currentTimeMillis() + 24 * HORA).apply()
            }
            .show()
    }

    private fun atualizar(v: Versao) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity, R.style.Dialogo)
                .setTitle(R.string.atualizacao_permissao_titulo)
                .setMessage(R.string.atualizacao_permissao_texto)
                .setPositiveButton(R.string.atualizacao_abrir_definicoes) { _, _ ->
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.packageName))
                    )
                }
                .setNegativeButton(R.string.cancelar, null)
                .show()
            return
        }
        aviso(activity.getString(R.string.atualizacao_a_descarregar))
        trabalho.execute {
            val ok = try { instalar(v) } catch (e: Exception) { Erro.FALHA }
            activity.runOnUiThread {
                when (ok) {
                    Erro.OK -> Unit
                    Erro.VERIFICACAO -> aviso(activity.getString(R.string.atualizacao_verificacao_falhou))
                    Erro.FALHA -> aviso(activity.getString(R.string.atualizacao_erro))
                }
            }
        }
    }

    private enum class Erro { OK, VERIFICACAO, FALHA }

    private fun instalar(v: Versao): Erro {
        val ficheiro = File(activity.cacheDir, NOME_APK)
        val c = abrir(v.url)
        if (c.responseCode != 200) return Erro.FALHA
        val md = MessageDigest.getInstance("SHA-256")
        var total = 0L
        c.inputStream.use { entrada ->
            ficheiro.outputStream().use { saida ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = entrada.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                    saida.write(buf, 0, n)
                    total += n
                }
            }
        }
        val hash = md.digest().joinToString("") { "%02x".format(it) }
        if ((v.tamanho > 0 && total != v.tamanho) || (v.sha256 != null && !hash.equals(v.sha256, ignoreCase = true))) {
            ficheiro.delete()
            return Erro.VERIFICACAO
        }

        val instalador = activity.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(activity.packageName)
        val id = instalador.createSession(params)
        instalador.openSession(id).use { sessao ->
            sessao.openWrite("estudo-tv", 0, ficheiro.length()).use { saida ->
                ficheiro.inputStream().use { it.copyTo(saida) }
                sessao.fsync(saida)
            }
            val intent = Intent(activity, InstaladorRecetor::class.java)
            val pi = PendingIntent.getBroadcast(
                activity, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            sessao.commit(pi.intentSender)
        }
        ficheiro.delete()
        return Erro.OK
    }

    private fun abrir(url: String): HttpURLConnection {
        var atual = URL(url)
        repeat(5) {
            val c = atual.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "EstudoTV/" + BuildConfig.VERSION_NAME)
            val codigo = c.responseCode
            if (codigo in 300..399) {
                val destino = c.getHeaderField("Location") ?: return c
                c.disconnect()
                atual = URL(atual, destino)
                if (atual.protocol != "https") throw IllegalStateException("redirecionamento sem https")
            } else return c
        }
        throw IllegalStateException("demasiados redirecionamentos")
    }

    private fun aviso(texto: String) = Toast.makeText(activity, texto, Toast.LENGTH_LONG).show()

    companion object {
        private const val API = "https://api.github.com/repos/fplamorim/estudo-tv/releases/latest"
        private const val NOME_APK = "estudo-tv.apk"
        private const val HORA = 3600_000L

        /** «1.2.10» > «1.2.9». Ignora sufixos que não sejam números. */
        fun maisNova(nova: String, atual: String): Boolean {
            fun partes(s: String) = s.split('.').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
            val a = partes(nova); val b = partes(atual)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
