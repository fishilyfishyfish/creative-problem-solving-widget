package io.github.fishilyfishyfish.cpswidget.data

import io.github.fishilyfishyfish.cpswidget.logic.School
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class NeisException(message: String) : Exception(message)

data class LunchResult(
    val meals: Map<LocalDate, String>,
    /** 요청 범위의 데이터를 전부 받았는지 */
    val complete: Boolean,
)

/** 나이스 교육정보 개방 포털 (https://open.neis.go.kr) 호출 */
object NeisClient {
    private const val BASE = "https://open.neis.go.kr/hub/"
    private val YMD: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

    suspend fun searchSchools(name: String, apiKey: String): List<School> = withContext(Dispatchers.IO) {
        val page = request(
            endpoint = "schoolInfo",
            params = mapOf("SCHUL_NM" to name.trim()),
            apiKey = apiKey,
            pSize = if (apiKey.isBlank()) 5 else 30,
        )
        page.rows.map {
            School(
                officeCode = it.optString("ATPT_OFCDC_SC_CODE"),
                schoolCode = it.optString("SD_SCHUL_CODE"),
                name = it.optString("SCHUL_NM"),
                officeName = it.optString("ATPT_OFCDC_SC_NM"),
                address = it.optString("ORG_RDNMA"),
            )
        }.filter { it.officeCode.isNotBlank() && it.schoolCode.isNotBlank() }
    }

    /** 점심(MMEAL_SC_CODE=2)만 날짜 범위로 받아온다. */
    suspend fun fetchLunch(school: School, from: LocalDate, to: LocalDate, apiKey: String): LunchResult =
        withContext(Dispatchers.IO) {
            val page = request(
                endpoint = "mealServiceDietInfo",
                params = mapOf(
                    "ATPT_OFCDC_SC_CODE" to school.officeCode,
                    "SD_SCHUL_CODE" to school.schoolCode,
                    "MMEAL_SC_CODE" to "2",
                    "MLSV_FROM_YMD" to from.format(YMD),
                    "MLSV_TO_YMD" to to.format(YMD),
                ),
                apiKey = apiKey,
                pSize = if (apiKey.isBlank()) 5 else 31,
            )
            val meals = mutableMapOf<LocalDate, String>()
            for (row in page.rows) {
                val date = runCatching { LocalDate.parse(row.optString("MLSV_YMD"), YMD) }.getOrNull() ?: continue
                val menu = row.optString("DDISH_NM")
                meals[date] = meals[date]?.let { "$it<br/>$menu" } ?: menu
            }
            LunchResult(meals, complete = page.total <= page.rows.size)
        }

    private class Page(val rows: List<JSONObject>, val total: Int)

    private fun request(endpoint: String, params: Map<String, String>, apiKey: String, pSize: Int): Page {
        val query = buildString {
            append("Type=json&pIndex=1&pSize=").append(pSize)
            if (apiKey.isNotBlank()) append("&KEY=").append(enc(apiKey.trim()))
            for ((k, v) in params) append('&').append(k).append('=').append(enc(v))
        }
        val conn = URL("$BASE$endpoint?$query").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = "GET"
            val code = try {
                conn.responseCode
            } catch (e: IOException) {
                throw NeisException("인터넷 연결을 확인하세요")
            }
            if (code !in 200..299) throw NeisException("나이스 서버 응답 오류 ($code)")
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return parse(endpoint, body)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(endpoint: String, body: String): Page {
        val root = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw NeisException("응답을 읽을 수 없음")
        }

        // 데이터가 없거나 오류일 때는 최상위에 RESULT만 온다.
        root.optJSONObject("RESULT")?.let { r ->
            val c = r.optString("CODE")
            if (c == "INFO-200") return Page(emptyList(), 0)
            throw NeisException(r.optString("MESSAGE").ifBlank { c })
        }

        val sections = root.optJSONArray(endpoint) ?: throw NeisException("예상하지 못한 응답")
        var total = 0
        val rows = mutableListOf<JSONObject>()
        for (i in 0 until sections.length()) {
            val section = sections.optJSONObject(i) ?: continue
            section.optJSONArray("head")?.let { head ->
                for (j in 0 until head.length()) {
                    val h = head.optJSONObject(j) ?: continue
                    if (h.has("list_total_count")) total = h.optInt("list_total_count")
                    h.optJSONObject("RESULT")?.let { r ->
                        val c = r.optString("CODE")
                        if (c != "INFO-000" && c != "INFO-200") {
                            throw NeisException(r.optString("MESSAGE").ifBlank { c })
                        }
                    }
                }
            }
            section.optJSONArray("row")?.let { arr ->
                for (j in 0 until arr.length()) arr.optJSONObject(j)?.let { rows += it }
            }
        }
        return Page(rows, maxOf(total, rows.size))
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
