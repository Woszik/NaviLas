package pl.navilas.finder.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import android.net.Uri
import java.io.File
import java.io.IOException

object AppUpdateInstaller {
    const val ACTION_INSTALL_STATUS = "pl.navilas.finder.UPDATE_INSTALL_STATUS"

    fun updatesDir(context: Context): File =
        File(context.cacheDir, "updates").apply { mkdirs() }

    fun apkFile(context: Context): File = File(updatesDir(context), "navilas-update.apk")

    fun canInstallPackages(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    fun unknownSourcesSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    /**
     * Stages [apkFile] into a [PackageInstaller] session and commits it.
     * Status (including STATUS_PENDING_USER_ACTION) is delivered to a broadcast
     * with [ACTION_INSTALL_STATUS].
     */
    @Throws(IOException::class)
    fun commitSession(context: Context, apkFile: File): Int {
        if (!apkFile.isFile || apkFile.length() <= 0L) {
            throw IOException("Brak pliku APK do instalacji")
        }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apkFile.inputStream().use { input ->
                val size = apkFile.length()
                session.openWrite("navilas.apk", 0, size).use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                    }
                    session.fsync(out)
                }
            }
            val callback = Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val pending = PendingIntent.getBroadcast(context, sessionId, callback, flags)
            session.commit(pending.intentSender)
        }
        return sessionId
    }
}
