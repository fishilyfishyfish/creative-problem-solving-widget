package io.github.fishilyfishyfish.cpswidget.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Release의 APK를 받아서 안드로이드 설치기에 넘긴다. */
object UpdateInstaller {

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    suspend fun start(context: Context, release: Release) {
        val app = context.applicationContext
        val state = Updates.install.value
        if (state is InstallState.Downloading || state is InstallState.Installing) return
        if (!canInstall(app)) {
            Updates.install.value = InstallState.NeedsPermission
            return
        }
        Updates.install.value = InstallState.Downloading(null)
        try {
            withContext(Dispatchers.IO) { downloadAndCommit(app, release) }
            // 이후 결과는 InstallResultReceiver로 온다.
            Updates.install.value = InstallState.Installing
        } catch (e: Exception) {
            Updates.install.value = InstallState.Failed("받기 실패: ${e.message ?: "알 수 없는 오류"}")
        }
    }

    private fun downloadAndCommit(context: Context, release: Release) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // 가능한 폰에서는 확인 화면 없이 업데이트된다. 안 되면 확인 화면이 뜬다.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true
                val code = conn.responseCode
                if (code !in 200..299) throw IOException("서버 응답 $code")
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    session.openWrite("base.apk", 0, if (total > 0) total else -1).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastPercent = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val percent = (done * 100 / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    Updates.install.value = InstallState.Downloading(percent)
                                }
                            }
                        }
                        session.fsync(out)
                    }
                }
            } finally {
                conn.disconnect()
            }

            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            )
            session.commit(pending.intentSender)
        } catch (e: Exception) {
            runCatching { session.abandon() }
            throw e
        } finally {
            runCatching { session.close() }
        }
    }
}

/** 설치기가 보내는 결과 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        Updates.install.value = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) InstallState.NeedsConfirm(confirm) else InstallState.Failed("설치 화면을 열 수 없음")
            }
            PackageInstaller.STATUS_SUCCESS -> InstallState.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallState.Failed("설치를 취소함")
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                InstallState.Failed("서명이 달라 덮어 설치할 수 없음. 앱을 지우고 Release에서 새로 설치해야 함")
            PackageInstaller.STATUS_FAILURE_STORAGE -> InstallState.Failed("저장 공간 부족")
            else -> InstallState.Failed(
                "설치 실패" + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.let { ": $it" } ?: ""),
            )
        }
    }
}
