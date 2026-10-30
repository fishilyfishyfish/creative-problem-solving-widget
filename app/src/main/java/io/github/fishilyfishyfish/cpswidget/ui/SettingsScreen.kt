package io.github.fishilyfishyfish.cpswidget.ui

import android.app.TimePickerDialog
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.fishilyfishyfish.cpswidget.BuildConfig
import io.github.fishilyfishyfish.cpswidget.data.MealStore
import io.github.fishilyfishyfish.cpswidget.data.NeisClient
import io.github.fishilyfishyfish.cpswidget.data.SettingsStore
import io.github.fishilyfishyfish.cpswidget.data.effectiveApiKey
import io.github.fishilyfishyfish.cpswidget.logic.Allergens
import io.github.fishilyfishyfish.cpswidget.logic.DAY_NAMES
import io.github.fishilyfishyfish.cpswidget.logic.MAX_PERIODS
import io.github.fishilyfishyfish.cpswidget.logic.PeriodTime
import io.github.fishilyfishyfish.cpswidget.logic.SCHOOL_DAYS
import io.github.fishilyfishyfish.cpswidget.logic.School
import io.github.fishilyfishyfish.cpswidget.logic.Settings
import io.github.fishilyfishyfish.cpswidget.logic.formatMinutes
import io.github.fishilyfishyfish.cpswidget.schedule.MealFetchWorker
import io.github.fishilyfishyfish.cpswidget.schedule.TickScheduler
import io.github.fishilyfishyfish.cpswidget.widget.WidgetRefresher
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TABS = listOf("시간표", "교시 시간", "알레르기", "학교")

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    var saved by remember { mutableStateOf(store.load()) }
    var draft by remember { mutableStateOf(saved) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val dirty = draft != saved

    fun save() {
        val old = saved
        val new = draft
        store.save(new)
        saved = new
        val sourceChanged = old.school != new.school || old.apiKey.trim() != new.apiKey.trim()
        if (sourceChanged) MealStore(context).clear()
        scope.launch {
            try {
                WidgetRefresher.refresh(context)
                TickScheduler.scheduleNext(context)
                if (new.school != null) {
                    if (sourceChanged) MealFetchWorker.enqueueNow(context) else MealFetchWorker.ensureFresh(context)
                }
            } catch (e: Exception) {
                Log.w("CpsWidget", "refresh after save failed", e)
            }
        }
        Toast.makeText(context, "저장됨", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("급식·시간표 위젯") },
                actions = {
                    TextButton(onClick = { save() }, enabled = dirty) {
                        Text(if (dirty) "저장" else "저장됨")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                TABS.forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                }
            }
            val onChange: (Settings) -> Unit = { draft = it }
            when (tab) {
                0 -> TimetableTab(draft, onChange)
                1 -> PeriodTimesTab(draft, onChange)
                2 -> AllergyTab(draft, onChange)
                else -> SchoolTab(draft, onChange)
            }
        }
    }
}

@Composable
private fun TabColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ───────────────────────── 시간표 ─────────────────────────

@Composable
private fun TimetableTab(s: Settings, onChange: (Settings) -> Unit) {
    val context = LocalContext.current
    var day by rememberSaveable { mutableIntStateOf(defaultDay()) }
    val subjects = s.subjectsOf(day)
    val dismissal = s.dismissalOf(day)

    TabColumn {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (d in SCHOOL_DAYS) {
                FilterChip(
                    selected = day == d,
                    onClick = { day = d },
                    label = { Text(DAY_NAMES[d - 1]) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${DAY_NAMES[day - 1]}요일 하교 시간",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedButton(onClick = {
                pickTime(context, dismissal) { m -> onChange(s.copy(dismissal = s.dismissal + (day to m))) }
            }) {
                Text(formatMinutes(dismissal))
            }
        }
        TextButton(onClick = { onChange(s.copy(dismissal = SCHOOL_DAYS.associateWith { dismissal })) }) {
            Text("모든 요일에 이 하교 시간 적용")
        }
        Hint("하교 시간이 지나면 위젯이 다음 등교일 시간표와 급식으로 넘어감")

        Spacer(Modifier.height(16.dp))

        for (p in 1..MAX_PERIODS) {
            val time = s.timeOf(p)?.takeIf { it.isValid }
            OutlinedTextField(
                value = subjects[p - 1],
                onValueChange = { v ->
                    val list = subjects.toMutableList().also { it[p - 1] = v }
                    onChange(s.copy(timetable = s.timetable + (day to list)))
                },
                label = {
                    Text(if (time != null) "${p}교시 · ${formatMinutes(time.startMin)}" else "${p}교시")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
        }
        Hint("비워둔 교시는 위젯에 표시되지 않음")
    }
}

// ───────────────────────── 교시 시간 ─────────────────────────

@Composable
private fun PeriodTimesTab(s: Settings, onChange: (Settings) -> Unit) {
    fun update(p: Int, t: PeriodTime?) {
        val list = MutableList(MAX_PERIODS) { i -> s.periodTimes.getOrNull(i) }
        list[p - 1] = t
        onChange(s.copy(periodTimes = list))
    }

    TabColumn {
        Hint("현재 교시 강조와 위젯이 바뀌는 시각에 쓰임. 모든 요일에 똑같이 적용됨")
        Spacer(Modifier.height(12.dp))

        for (p in 1..MAX_PERIODS) {
            val t = s.timeOf(p)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${p}교시", Modifier.width(52.dp), style = MaterialTheme.typography.titleSmall)
                TimeButton(t?.startMin, "시작") { m ->
                    update(p, if (t != null) PeriodTime(m, t.endMin) else PeriodTime(m, minOf(m + 50, 24 * 60)))
                }
                Text("~", Modifier.padding(horizontal = 6.dp))
                TimeButton(t?.endMin, "끝") { m ->
                    update(p, if (t != null) PeriodTime(t.startMin, m) else PeriodTime(maxOf(m - 50, 0), m))
                }
                Spacer(Modifier.weight(1f))
                if (t != null) {
                    TextButton(onClick = { update(p, null) }) { Text("지우기") }
                }
            }
            if (t != null && !t.isValid) {
                Text(
                    text = "끝 시각이 시작보다 빠름",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TimeButton(minutes: Int?, placeholder: String, onPicked: (Int) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = { pickTime(context, minutes ?: (9 * 60)) { onPicked(it) } },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(minutes?.let { formatMinutes(it) } ?: placeholder)
    }
}

// ───────────────────────── 알레르기 ─────────────────────────

@Composable
private fun AllergyTab(s: Settings, onChange: (Settings) -> Unit) {
    TabColumn {
        Hint("체크한 항목이 든 메뉴를 위젯에서 빨갛게 표시함. 번호는 급식표 메뉴 옆 괄호 숫자와 같음")
        Spacer(Modifier.height(8.dp))

        Allergens.NAMES.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEach { (n, name) ->
                    val checked = n in s.allergens
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { on ->
                                    onChange(s.copy(allergens = if (on) s.allergens + n else s.allergens - n))
                                },
                            )
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text("$n. $name")
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = s.keywordsRaw,
            onValueChange = { onChange(s.copy(keywordsRaw = it)) },
            label = { Text("직접 추가할 재료") },
            placeholder = { Text("예: 오이, 키위") },
            supportingText = { Text("쉼표로 구분. 메뉴 이름에 들어 있으면 표시") },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
        Hint("학교가 번호를 빠뜨려 올리는 경우도 있으니 위젯 표시만 믿지 말고 급식표도 확인할 것")
    }
}

// ───────────────────────── 학교 ─────────────────────────

@Composable
private fun SchoolTab(s: Settings, onChange: (Settings) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mealStore = remember { MealStore(context) }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<School>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var statusTick by remember { mutableIntStateOf(0) }

    fun search() {
        val q = query.trim()
        if (q.isEmpty() || searching) return
        searching = true
        message = null
        scope.launch {
            try {
                val found = NeisClient.searchSchools(q, s.effectiveApiKey())
                results = found
                if (found.isEmpty()) message = "검색 결과 없음"
            } catch (e: Exception) {
                message = "검색 실패: ${e.message}"
            } finally {
                searching = false
            }
        }
    }

    TabColumn {
        Text("학교", style = MaterialTheme.typography.titleSmall)
        Text(
            text = s.school?.let { sc -> listOf(sc.name, sc.officeName).filter { it.isNotBlank() }.joinToString(" · ") }
                ?: "미설정",
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("학교 이름으로 검색") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { search() }, enabled = !searching && query.isNotBlank()) {
                Text(if (searching) "검색 중" else "검색")
            }
        }

        message?.let {
            Spacer(Modifier.height(6.dp))
            Hint(it)
        }

        results.forEach { school ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onChange(s.copy(school = school))
                        results = emptyList()
                        message = "${school.name} 선택됨. 위의 저장을 눌러야 적용됨"
                    }
                    .padding(vertical = 10.dp),
            ) {
                Text(school.name, style = MaterialTheme.typography.bodyLarge)
                Hint(listOf(school.officeName, school.address).filter { it.isNotBlank() }.joinToString(" · "))
            }
            HorizontalDivider()
        }

        Spacer(Modifier.height(24.dp))

        Text("나이스 인증키", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = s.apiKey,
            onValueChange = { onChange(s.copy(apiKey = it.trim())) },
            label = { Text("인증키 (선택)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                Text(
                    if (BuildConfig.NEIS_API_KEY.isNotBlank()) "비워두면 빌드에 넣은 키를 씀"
                    else "open.neis.go.kr에서 무료 발급. 없으면 받아오는 양이 제한될 수 있음",
                )
            },
        )

        Spacer(Modifier.height(24.dp))

        Text("급식 데이터", style = MaterialTheme.typography.titleSmall)
        val status = remember(statusTick) { mealStatus(mealStore) }
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Row {
            OutlinedButton(
                onClick = {
                    MealFetchWorker.enqueueNow(context)
                    Toast.makeText(context, "요청함. 잠시 후 상태 새로고침", Toast.LENGTH_SHORT).show()
                },
                enabled = s.school != null,
            ) {
                Text("지금 다시 불러오기")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { statusTick++ }) { Text("상태 새로고침") }
        }
        Hint("학교나 인증키를 바꿨으면 먼저 저장")
    }
}

// ───────────────────────── 공통 ─────────────────────────

private fun pickTime(context: Context, initial: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(hour * 60 + minute) },
        initial / 60,
        initial % 60,
        true,
    ).show()
}

private fun defaultDay(): Int = LocalDate.now().dayOfWeek.value.let { if (it in SCHOOL_DAYS) it else 1 }

private fun mealStatus(store: MealStore): String {
    val ok = store.lastSuccessAt
    val errAt = store.lastErrorAt
    val err = store.lastError
    return when {
        err != null && errAt >= ok -> "마지막 시도 실패 (${clock(errAt)}): $err"
        ok > 0 -> "마지막 갱신 ${clock(ok)}"
        else -> "아직 받아온 적 없음"
    }
}

private fun clock(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M/d HH:mm"))
