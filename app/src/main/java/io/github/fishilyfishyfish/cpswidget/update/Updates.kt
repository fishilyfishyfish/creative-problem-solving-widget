package io.github.fishilyfishyfish.cpswidget.update

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import io.github.fishilyfishyfish.cpswidget.BuildConfig
import io.github.fishilyfishyfish.cpswidget.logic.Versions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** GitHub Release 하나 */
data class Release(val build: Int, val apkUrl: String)

sealed interface InstallState {
    data object Idle : InstallState
    data class Downloading(val percent: Int?) : InstallState
    data object Installing : InstallState

    /** 설치 확인 화면을 띄워야 함 */
    data class NeedsConfirm(val intent: Intent) : InstallState

    /** "출처를 알 수 없는 앱 설치" 허용이 필요함 */
    data object NeedsPermission : InstallState
    data class Failed(val message: String) : InstallState
}

/**
 * 새 버전 확인 상태. 앱 화면(맨 위 띠)과 백그라운드 확인이 같이 쓴다.
 * 버전 번호는 GitHub Actions 실행 번호이고, Release 태그는 "build-번호"다.
 */
object Updates {
    val currentBuild: Int get() = BuildConfig.VERSION_CODE

    private val _available = MutableStateFlow<Release?>(null)

    /** 지금보다 새 버전이 있으면 그 Release, 없으면 null */
    val available: StateFlow<Release?> = _available

    val install = MutableStateFlow<InstallState>(InstallState.Idle)

    @Volatile
    private var loaded = false

    /** 마지막으로 확인한 결과를 불러온다. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        val build = p.getInt(K_BUILD, 0)
        val url = p.getString(K_URL, null)
        if (build > currentBuild && !url.isNullOrBlank()) _available.value = Release(build, url)
    }

    /**
     * GitHub에서 가장 최근 Release를 확인한다.
     * @return 새 버전이 있으면 그 Release
     * @throws IOException 네트워크나 응답 문제
     */
    suspend fun check(context: Context): Release? {
        val latest = fetchLatest()
        prefs(context).edit {
            if (latest == null) {
                remove(K_BUILD)
                remove(K_URL)
            } else {
                putInt(K_BUILD, latest.build)
                putString(K_URL, latest.apkUrl)
            }
        }
        loaded = true
        val newer = latest?.takeIf { it.build > currentBuild }
        _available.value = newer
        return newer
    }

    fun wasNotified(context: Context, build: Int): Boolean = prefs(context).getInt(K_NOTIFIED, 0) >= build

    fun markNotified(context: Context, build: Int) {
        prefs(context).edit { putInt(K_NOTIFIED, build) }
    }

    private suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        val conn = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "cps-widget/${BuildConfig.VERSION_CODE}")
            when (val code = conn.responseCode) {
                404 -> return@withContext null // 아직 Release가 없음
                !in 200..299 -> throw IOException("GitHub 응답 오류 ($code)")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val build = Versions.buildFromTag(json.optString("tag_name")) ?: return@withContext null
            val assets = json.optJSONArray("assets") ?: return@withContext null
            val apk = (0 until assets.length())
                .mapNotNull { assets.optJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?: return@withContext null
            Release(build, apk.optString("browser_download_url"))
        } finally {
            conn.disconnect()
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("updates", Context.MODE_PRIVATE)

    private const val K_BUILD = "latest_build"
    private const val K_URL = "latest_url"
    private const val K_NOTIFIED = "notified_build"
}
