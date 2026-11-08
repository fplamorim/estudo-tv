package com.fernandoamorim.estudotv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

/** Recebe o estado da instalação: quando o Android pede confirmação, abre o ecrã do instalador. */
class InstaladorRecetor : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmar = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirmar?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirmar != null) context.startActivity(confirmar)
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> Toast.makeText(context, R.string.atualizacao_erro, Toast.LENGTH_LONG).show()
        }
    }
}
