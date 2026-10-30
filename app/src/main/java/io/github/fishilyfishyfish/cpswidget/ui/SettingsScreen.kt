package io.github.fishilyfishyfish.cpswidget.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fishilyfishyfish.cpswidget.data.MealStore
import io.github.fishilyfishyfish.cpswidget.data.NeisClient
import io.github.fishilyfishyfish.cpswidget.data.SettingsStore
import io.github.fishilyfishyfish.cpswidget.data.effectiveApiKey
import io.github.fishilyfishyfish.cpswidget.logic.Allergens
import io.github.fishilyfishyfish.cpswidget.logic.DAY_NAMES
import io.github.fishilyfishyfish.cpswidget.logic.MAX_PERIODS
import io.github.fishilyfishyfish.cpswidget.logic.MealType
import io.github.fishilyfishyfish.cpswidget.logic.PeriodTime
import io.github.fishilyfishyfish.cpswidget.logic.SCHOOL_DAYS
import io.github.fishilyfishyfish.cpswidget.logic.School
import io.github.fishilyfishyfish.cpswidget.logic.Settings
import io.github.fishilyfishyfish.cpswidget.logic.formatMinutes
import io.github.fishilyfishyfish.cpswidget.schedule.MealFetchWorker
import io.github.fishilyfishyfish.cpswidget.schedule.TickScheduler
import io.github.fishilyfishyfish.cpswidget.update.InstallState
import io.github.fishilyfishyfish.cpswidget.update.UpdateInstaller
import io.github.fishilyfishyfish.cpswidget.update.Updates
import io.github.fishilyfishyfish.cpswidget.widget.WidgetRefresher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TIME_COL = 52.dp

/**
 * 설정 화면 하나. 바꾸는 즉시 저장되고, 잠깐 뒤 위젯에 반영된다.
 * 순서: 시간표 → 하교 시간 → 식사 시간 → 알레르기 → 학교 → 교육청 인증키
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    var settings by remember { mutableStateOf(store.load()) }
    var applied by remember { mutableStateOf(settings) }

    val onChange: (Settings) -> Unit = { new ->
        settings = new
        store.save(new)
    }

    // 입력이 멈추고 0.7초 뒤에 위젯 갱신
    LaunchedEffect(settings) {
        delay(700)
        val old = applied
        val new = settings
        if (old == new) return@LaunchedEffect
        applied = new
        val sourceChanged = old.school != new.school || old.apiKey.trim() != new.apiKey.trim()
        try {
            if (sourceChanged) MealStore(context).clear()
            WidgetRefresher.refresh(context)
            TickScheduler.scheduleNext(context)
            if (new.school != null) {
                if (sourceChanged) MealFetchWorker.enqueueNow(context) else MealFetchWorker.ensureFresh(context)
            }
        } catch (e: Exception) {
            Log.w("CpsWidget", "refresh after change failed", e)
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
        ) {
            UpdateBanner()
            SectionTitle("시간표")
            TimetableGrid(settings, onChange)
            Hint("칸을 눌러 과목·교실 입력. 왼쪽 칸을 눌러 교시 시각 입력")

            Spacer(Modifier.height(24.dp))
            SectionTitle("하교 시간")
            DismissalRow(settings, onChange)

            Spacer(Modifier.height(24.dp))
            SectionTitle("식사 시간")
            MealTimes(settings, onChange)

            Spacer(Modifier.height(24.dp))
            SectionTitle("알레르기")
            AllergyChecks(settings, onChange)

            Spacer(Modifier.height(24.dp))
            SectionTitle("학교 이름")
            SchoolSearch(settings, onChange)

            Spacer(Modifier.height(24.dp))
            SectionTitle("교육청 인증키")
            OutlinedTextField(
                value = settings.apiKey,
                onValueChange = { onChange(settings.copy(apiKey = it.trim())) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("open.neis.go.kr에서 무료 발급") },
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

// ───────────────────────── 업데이트 띠 ─────────────────────────

/** 새 버전이 있을 때만 맨 위에 보이는 띠 */
@Composable
private fun UpdateBanner() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val release by Updates.available.collectAsState()
    val state by Updates.install.collectAsState()

    // 설치 확인 화면은 앱이 화면에 보일 때 띄운다.
    LaunchedEffect(state) {
        val s = state
        if (s is InstallState.NeedsConfirm) {
            Updates.install.value = InstallState.Installing
            try {
                context.startActivity(s.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                Updates.install.value = InstallState.Failed("설치 화면을 열 수 없음")
            }
        }
    }

    val r = release ?: return
    val s = state
    val detail = when (s) {
        InstallState.Idle -> "지금은 빌드 ${Updates.currentBuild}"
        is InstallState.Downloading -> "받는 중" + (s.percent?.let { " $it%" } ?: "")
        InstallState.Installing, is InstallState.NeedsConfirm -> "설치 중"
        InstallState.NeedsPermission -> "이 앱의 '출처를 알 수 없는 앱 설치'를 허용하고 돌아오기"
        is InstallState.Failed -> s.message
    }
    val busy = s is InstallState.Downloading || s is InstallState.Installing || s is InstallState.NeedsConfirm

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("새 버전 있음 · 빌드 ${r.build}", style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(8.dp))
            when {
                busy -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                s == InstallState.NeedsPermission -> Button(onClick = {
                    context.startActivity(
                        Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }) { Text("허용하기") }
                else -> Button(onClick = { scope.launch { UpdateInstaller.start(context, r) } }) {
                    Text(if (s is InstallState.Failed) "다시 시도" else "업데이트")
                }
            }
        }
    }
}

// ───────────────────────── 시간표 ─────────────────────────

@Composable
private fun TimetableGrid(s: Settings, onChange: (Settings) -> Unit) {
    var editCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var editTime by remember { mutableStateOf<Int?>(null) }
    val line = MaterialTheme.colorScheme.outlineVariant

    Column(Modifier.fillMaxWidth().border(1.dp, line)) {
        Row(Modifier.fillMaxWidth().height(36.dp)) {
            Spacer(Modifier.width(TIME_COL))
            VerticalDivider(color = line)
            for (d in SCHOOL_DAYS) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(DAY_NAMES[d - 1], style = MaterialTheme.typography.titleSmall)
                }
                if (d != SCHOOL_DAYS.last) VerticalDivider(color = line)
            }
        }
        for (p in 1..MAX_PERIODS) {
            HorizontalDivider(color = line)
            Row(Modifier.fillMaxWidth().height(58.dp)) {
                TimeCell(p, s.timeOf(p), Modifier.width(TIME_COL)) { editTime = p }
                VerticalDivider(color = line)
                for (d in SCHOOL_DAYS) {
                    ClassCell(
                        subject = s.subjectsOf(d)[p - 1],
                        room = s.roomsOf(d)[p - 1],
                        modifier = Modifier.weight(1f),
                    ) { editCell = d to p }
                    if (d != SCHOOL_DAYS.last) VerticalDivider(color = line)
                }
            }
        }
    }

    editCell?.let { (d, p) ->
        key(d, p) {
            CellDialog(
                title = "${DAY_NAMES[d - 1]}요일 ${p}교시",
                subject = s.subjectsOf(d)[p - 1],
                room = s.roomsOf(d)[p - 1],
                onDismiss = { editCell = null },
                onConfirm = { subject, room ->
                    onChange(s.withCell(d, p, subject, room))
                    editCell = null
                },
            )
        }
    }

    editTime?.let { p ->
        key(p) {
            PeriodTimeDialog(
                period = p,
                initial = s.timeOf(p),
                onDismiss = { editTime = null },
                onConfirm = { t ->
                    onChange(s.withPeriodTime(p, t))
                    editTime = null
                },
            )
        }
    }
}

@Composable
private fun TimeCell(period: Int, time: PeriodTime?, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.fillMaxHeight().clickable(onClick = onClick).padding(2.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${period}교시", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        if (time != null) {
            Text(formatMinutes(time.startMin), style = MaterialTheme.typography.labelSmall)
            Text(formatMinutes(time.endMin), style = MaterialTheme.typography.labelSmall)
        } else {
            Text("-", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ClassCell(subject: String, room: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.fillMaxHeight().clickable(onClick = onClick).padding(2.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(subject, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (room.isNotEmpty()) {
            Text(
                text = room,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CellDialog(
    title: String,
    subject: String,
    room: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var sub by remember { mutableStateOf(subject) }
    var rm by remember { mutableStateOf(room) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(sub, { sub = it }, label = { Text("과목") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(rm, { rm = it }, label = { Text("교실") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(sub.trim(), rm.trim()) }) { Text("확인") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun PeriodTimeDialog(
    period: Int,
    initial: PeriodTime?,
    onDismiss: () -> Unit,
    onConfirm: (PeriodTime?) -> Unit,
) {
    var start by remember { mutableStateOf(initial?.startMin ?: (9 * 60)) }
    var end by remember { mutableStateOf(initial?.endMin ?: (9 * 60 + 50)) }
    val valid = PeriodTime(start, end).isValid
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${period}교시 시각") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeButton(start) { start = it }
                    Text("~", Modifier.padding(horizontal = 8.dp))
                    TimeButton(end) { end = it }
                }
                if (!valid) {
                    Text(
                        "끝 시각이 시작보다 빠름",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                TextButton(onClick = { onConfirm(null) }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("이 교시 사용 안 함")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(PeriodTime(start, end)) }, enabled = valid) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

// ───────────────────────── 하교 시간 ─────────────────────────

@Composable
private fun DismissalRow(s: Settings, onChange: (Settings) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        for (d in SCHOOL_DAYS) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(DAY_NAMES[d - 1], style = MaterialTheme.typography.labelLarge)
                TimeButton(s.dismissalOf(d), compact = true) { m ->
                    onChange(s.copy(dismissal = s.dismissal + (d to m)))
                }
            }
        }
    }
}

// ───────────────────────── 식사 시간 ─────────────────────────

@Composable
private fun MealTimes(s: Settings, onChange: (Settings) -> Unit) {
    for (type in MealType.entries) {
        val m = s.mealOf(type)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${type.label}시간", Modifier.width(72.dp), style = MaterialTheme.typography.bodyMedium)
            TimeButton(m.time.startMin, enabled = m.enabled) { t ->
                onChange(s.withMeal(type, m.copy(time = PeriodTime(t, m.time.endMin))))
            }
            Text("~", Modifier.padding(horizontal = 4.dp))
            TimeButton(m.time.endMin, enabled = m.enabled) { t ->
                onChange(s.withMeal(type, m.copy(time = PeriodTime(m.time.startMin, t))))
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.toggleable(
                    value = !m.enabled,
                    role = Role.Checkbox,
                    onValueChange = { none -> onChange(s.withMeal(type, m.copy(enabled = !none))) },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = !m.enabled, onCheckedChange = null)
                Text("없음")
            }
        }
        if (m.enabled && !m.time.isValid) {
            Text(
                "끝 시각이 시작보다 빠름",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

// ───────────────────────── 알레르기 ─────────────────────────

@Composable
private fun AllergyChecks(s: Settings, onChange: (Settings) -> Unit) {
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
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Text("$n. $name")
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Hint("번호는 급식표 메뉴 옆 괄호 숫자. 학교가 번호를 빠뜨리는 경우도 있으니 급식표도 확인할 것")
}

// ───────────────────────── 학교 ─────────────────────────

@Composable
private fun SchoolSearch(s: Settings, onChange: (Settings) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<School>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

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

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
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

    val current = s.school
    Hint(
        if (current == null) "선택한 학교 없음"
        else "선택됨: " + listOf(current.name, current.officeName).filter { it.isNotBlank() }.joinToString(" · "),
    )
    message?.let { Hint(it) }

    results.forEach { school ->
        Column(
            Modifier
                .fillMaxWidth()
                .clickable {
                    onChange(s.copy(school = school))
                    results = emptyList()
                    message = null
                }
                .padding(vertical = 10.dp),
        ) {
            Text(school.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = listOf(school.officeName, school.address).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
    }
}

// ───────────────────────── 공통 ─────────────────────────

@Composable
private fun TimeButton(minutes: Int, enabled: Boolean = true, compact: Boolean = false, onPicked: (Int) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = { pickTime(context, minutes, onPicked) },
        enabled = enabled,
        contentPadding = if (compact) PaddingValues(horizontal = 6.dp, vertical = 4.dp) else PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(formatMinutes(minutes), style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge)
    }
}

private fun pickTime(context: Context, initial: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(hour * 60 + minute) },
        initial / 60,
        initial % 60,
        true,
    ).show()
}
