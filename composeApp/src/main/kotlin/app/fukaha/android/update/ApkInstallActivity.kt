package app.fukaha.android.update

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import app.fukaha.R

/**
 * Receives [PackageInstaller.Session.commit] status. The confirm sheet must be
 * started from an Activity (see STATUS_PENDING_USER_ACTION / EXTRA_INTENT).
 */
class ApkInstallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        finish()
    }

    private fun handle(intent: Intent) {
        val sessionId = intent.getIntExtra(ApkUpdater.EXTRA_SESSION_ID, -1)
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val failure = runCatching {
                    val confirm = confirmIntent(intent) ?: error(getString(R.string.update_failed))
                    startActivity(confirm)
                }.exceptionOrNull()
                if (failure != null) {
                    ApkUpdater.completeInstall(sessionId, failure.message ?: getString(R.string.update_failed))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> ApkUpdater.completeInstall(sessionId, null)
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.update_failed)
                ApkUpdater.completeInstall(sessionId, message)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val ACTION_STATUS = "app.fukaha.android.update.INSTALL_STATUS"

        fun confirmIntent(intent: Intent): Intent? {
            return parcelableIntent(intent, Intent.EXTRA_INTENT)
                ?: parcelableIntent(intent, "android.content.pm.extra.INTENT")
        }

        private fun parcelableIntent(intent: Intent, key: String): Intent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(key, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(key)
            }
    }
}
