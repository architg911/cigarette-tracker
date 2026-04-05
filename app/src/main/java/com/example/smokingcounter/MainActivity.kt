package com.example.smokingcounter

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp

private val BackgroundColor = Color(0xFF0D0D0D)
private val TextColor = Color(0xFFFFFFFF)
private val SubduedColor = Color(0xFF666666)
private val AccentColor = Color(0xFFE8A020)
private val DangerColor = Color(0xFFCF4E4E)

private val guiltMessages = listOf(
    "there it is.",
    "again.",
    "you know this.",
    "still going.",
    "is it worth it?",
    "just one more, right?",
    "your lungs noted that.",
    "that's a pattern.",
    "you said you'd stop.",
    "still here. still counting."
)

private const val PREFS_NAME = "smoking_prefs"
private const val KEY_COUNT = "count"
private const val KEY_DATE = "last_date"
private const val KEY_GOAL = "goal"
private const val KEY_HISTORY_PREFIX = "history_"
private const val KEY_GOAL_HISTORY_PREFIX = "goal_history_"
private const val KEY_NOTE_PREFIX = "note_"
private const val KEY_TIMESTAMPS_PREFIX = "timestamps_"

data class HistoryEntry(val date: LocalDate, val count: Int, val goal: Int, val note: String = "")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SmokingCounterApp() }
    }
}

private fun loadCount(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val today = LocalDate.now().toString()
    val lastDate = prefs.getString(KEY_DATE, "")
    return if (lastDate == today) {
        prefs.getInt(KEY_COUNT, 0)
    } else {
        prefs.edit().putInt(KEY_COUNT, 0).putString(KEY_DATE, today).apply()
        0
    }
}

private fun saveCount(context: Context, count: Int) {
    val today = LocalDate.now()
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(KEY_COUNT, count)
        .putString(KEY_DATE, today.toString())
        .putInt("$KEY_HISTORY_PREFIX$today", count)
        .apply()
}

private fun loadHistory(context: Context, days: Int = 7): List<Pair<LocalDate, Int>> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val today = LocalDate.now()
    return (days - 1 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        date to prefs.getInt("$KEY_HISTORY_PREFIX$date", 0)
    }
}

private fun loadGoal(context: Context): Int =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_GOAL, 0)

private fun saveGoal(context: Context, goal: Int) {
    val today = LocalDate.now()
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(KEY_GOAL, goal)
        .putInt("$KEY_GOAL_HISTORY_PREFIX$today", goal)
        .apply()
}

private fun SharedPreferences.readEntry(date: LocalDate, globalGoal: Int): HistoryEntry {
    val count = getInt("$KEY_HISTORY_PREFIX$date", 0)
    val goal = getInt("$KEY_GOAL_HISTORY_PREFIX$date", globalGoal)
    val note = getString("$KEY_NOTE_PREFIX$date", "") ?: ""
    return HistoryEntry(date, count, goal, note)
}

private fun loadAllHistory(context: Context): List<HistoryEntry> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val globalGoal = prefs.getInt(KEY_GOAL, 0)
    return prefs.all.entries
        .filter { it.key.startsWith(KEY_HISTORY_PREFIX) }
        .mapNotNull { entry ->
            try {
                val date = LocalDate.parse(entry.key.removePrefix(KEY_HISTORY_PREFIX))
                prefs.readEntry(date, globalGoal).copy(count = entry.value as? Int ?: 0)
            } catch (e: Exception) { null }
        }
        .sortedByDescending { it.date }
}

private fun loadHistoryWithGoals(context: Context, days: Int = 30, endDate: LocalDate = LocalDate.now()): List<HistoryEntry> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val globalGoal = prefs.getInt(KEY_GOAL, 0)
    return (days - 1 downTo 0).map { offset ->
        prefs.readEntry(endDate.minusDays(offset.toLong()), globalGoal)
    }
}

private fun saveHistoryEntry(context: Context, date: LocalDate, count: Int, goal: Int) {
    val today = LocalDate.now()
    val edit = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putInt("$KEY_HISTORY_PREFIX$date", count)
        .putInt("$KEY_GOAL_HISTORY_PREFIX$date", goal)
    if (date == today) {
        edit.putInt(KEY_COUNT, count)
            .putString(KEY_DATE, today.toString())
            .putInt(KEY_GOAL, goal)
    }
    edit.apply()
}

private fun recordTimestamp(context: Context) {
    val today = LocalDate.now()
    val key = "$KEY_TIMESTAMPS_PREFIX$today"
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val existing = prefs.getString(key, "") ?: ""
    val ts = Instant.now().toString()
    val updated = if (existing.isEmpty()) ts else "$existing|$ts"
    prefs.edit().putString(key, updated).apply()
}

private fun removeLastTimestamp(context: Context) {
    val today = LocalDate.now()
    val key = "$KEY_TIMESTAMPS_PREFIX$today"
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val existing = prefs.getString(key, "") ?: ""
    if (existing.isEmpty()) return
    val updated = existing.substringBeforeLast('|', "")
    prefs.edit().putString(key, updated).apply()
}

private fun loadNote(context: Context, date: LocalDate): String =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString("$KEY_NOTE_PREFIX$date", "") ?: ""

private fun saveNote(context: Context, date: LocalDate, note: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString("$KEY_NOTE_PREFIX$date", note).apply()
}

private fun calculateStreak(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val globalGoal = prefs.getInt(KEY_GOAL, 0)
    var streak = 0
    var date = LocalDate.now().minusDays(1)
    repeat(365) {
        val goal = prefs.getInt("$KEY_GOAL_HISTORY_PREFIX$date", globalGoal)
        if (goal <= 0) return streak
        val count = prefs.getInt("$KEY_HISTORY_PREFIX$date", -1)
        if (count < 0 || count > goal) return streak
        streak++
        date = date.minusDays(1)
    }
    return streak
}


@Composable
fun SmokingCounterApp() {
    val context = LocalContext.current
    var count by rememberSaveable { mutableIntStateOf(0) }
    var history by remember { mutableStateOf(emptyList<Pair<LocalDate, Int>>()) }
    var goal by rememberSaveable { mutableIntStateOf(0) }
    var showGoalDialog by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var streak by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { 2 })

    LaunchedEffect(Unit) {
        count = loadCount(context)
        history = loadHistory(context)
        goal = loadGoal(context)
        streak = calculateStreak(context)
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> MainCounterPage(
                    count = count, history = history, goal = goal, streak = streak,
                    onIncrement = {
                        count++
                        saveCount(context, count)
                        recordTimestamp(context)
                        history = loadHistory(context)
                        streak = calculateStreak(context)
                    },
                    onDecrement = {
                        if (count > 0) {
                            count--
                            saveCount(context, count)
                            removeLastTimestamp(context)
                            history = loadHistory(context)
                            streak = calculateStreak(context)
                        }
                    },
                    onShowGoalDialog = { showGoalDialog = true },
                    onShowHistory = { showHistory = true }
                )
                else -> TrendsScreen(goal = goal)
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(2) { i ->
                Box(
                    modifier = Modifier
                        .size(if (pagerState.currentPage == i) 5.dp else 4.dp)
                        .background(
                            if (pagerState.currentPage == i) SubduedColor
                            else SubduedColor.copy(alpha = 0.35f),
                            CircleShape
                        )
                )
            }
        }
    }

    if (showGoalDialog) {
        GoalDialog(
            currentGoal = goal,
            onSave = { newGoal ->
                goal = newGoal
                saveGoal(context, newGoal)
                showGoalDialog = false
            },
            onClear = {
                goal = 0
                saveGoal(context, 0)
                showGoalDialog = false
            },
            onDismiss = { showGoalDialog = false }
        )
    }

    if (showHistory) {
        HistoryEditorScreen(
            goal = goal,
            onDismiss = {
                showHistory = false
                count = loadCount(context)
                streak = calculateStreak(context)
                goal = loadGoal(context)
                history = loadHistory(context)
            }
        )
    }
}

@Composable
fun MainCounterPage(
    count: Int,
    history: List<Pair<LocalDate, Int>>,
    goal: Int,
    streak: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onShowGoalDialog: () -> Unit,
    onShowHistory: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        LandscapeCounterLayout(
            count = count, goal = goal, streak = streak,
            onIncrement = onIncrement, onDecrement = onDecrement,
            onShowGoalDialog = onShowGoalDialog, onShowHistory = onShowHistory
        )
    } else {
        PortraitCounterLayout(
            count = count, history = history, goal = goal, streak = streak,
            onIncrement = onIncrement, onDecrement = onDecrement,
            onShowGoalDialog = onShowGoalDialog, onShowHistory = onShowHistory
        )
    }
}

@Composable
private fun CounterAnimationEffects(
    pressKey: Int,
    fabScale: Animatable<Float, AnimationVector1D>,
    counterShake: Animatable<Float, AnimationVector1D>,
    guiltAlpha: Animatable<Float, AnimationVector1D>
) {
    LaunchedEffect(pressKey) {
        if (pressKey == 0) return@LaunchedEffect
        fabScale.animateTo(1f, animationSpec = keyframes {
            durationMillis = 300
            1.15f at 60; 0.88f at 140; 1.02f at 220; 1f at 300
        })
    }
    LaunchedEffect(pressKey) {
        if (pressKey == 0) return@LaunchedEffect
        counterShake.animateTo(0f, animationSpec = keyframes {
            durationMillis = 420
            0f at 0; -14f at 60; 14f at 120; -10f at 180; 10f at 240; -5f at 300; 5f at 350; 0f at 420
        })
    }
    LaunchedEffect(pressKey) {
        if (pressKey == 0) return@LaunchedEffect
        guiltAlpha.snapTo(0f)
        guiltAlpha.animateTo(1f, animationSpec = tween(120))
        kotlinx.coroutines.delay(1200)
        guiltAlpha.animateTo(0f, animationSpec = tween(400))
    }
}

@Composable
private fun PortraitCounterLayout(
    count: Int,
    history: List<Pair<LocalDate, Int>>,
    goal: Int,
    streak: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onShowGoalDialog: () -> Unit,
    onShowHistory: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var pressKey by remember { mutableIntStateOf(0) }
    val fabScale = remember { Animatable(1f) }
    val counterShake = remember { Animatable(0f) }
    val guiltAlpha = remember { Animatable(0f) }
    val guiltFraction = if (goal > 0) (count.toFloat() / goal).coerceIn(0f, 1f) else 0f
    val fabColor = lerp(AccentColor, DangerColor, guiltFraction)
    val currentMessage = guiltMessages[(count - 1).coerceAtLeast(0) % guiltMessages.size]

    CounterAnimationEffects(pressKey, fabScale, counterShake, guiltAlpha)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "🔥 $streak day streak",
            color = if (streak > 0) AccentColor else SubduedColor.copy(alpha = 0.4f),
            fontSize = 12.sp, letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 28.dp)
        )

        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onShowHistory) { Text("···", color = SubduedColor, fontSize = 18.sp) }
            TextButton(onClick = onShowGoalDialog) {
                Text(
                    text = if (goal > 0) "/ $goal" else "set goal",
                    color = if (goal > 0) AccentColor else SubduedColor,
                    fontSize = 13.sp, letterSpacing = 1.sp
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = count.toString(),
                color = lerp(TextColor, DangerColor, guiltAlpha.value),
                fontSize = 120.sp,
                fontWeight = if (guiltAlpha.value > 0.05f) FontWeight.Bold else FontWeight.Thin,
                lineHeight = 120.sp,
                modifier = Modifier.graphicsLayer { translationX = counterShake.value }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentMessage,
                color = DangerColor.copy(alpha = guiltAlpha.value),
                fontSize = 12.sp, letterSpacing = 1.sp
            )
            if (goal > 0) {
                Spacer(modifier = Modifier.height(24.dp))
                LinearProgressIndicator(
                    progress = { (count.toFloat() / goal).coerceIn(0f, 1f) },
                    modifier = Modifier.width(140.dp),
                    color = if (count >= goal) DangerColor else AccentColor,
                    trackColor = SubduedColor.copy(alpha = 0.15f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                val remaining = goal - count
                Text(
                    text = if (remaining >= 0) "$remaining left" else "+${-remaining} over",
                    color = if (remaining < 0) DangerColor else SubduedColor,
                    fontSize = 12.sp, letterSpacing = 1.sp
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            FloatingActionButton(
                onClick = { pressKey++; haptic.performHapticFeedback(HapticFeedbackType.LongPress); onIncrement() },
                containerColor = fabColor,
                contentColor = Color.Black, shape = CircleShape,
                modifier = Modifier.size(80.dp).graphicsLayer { scaleX = fabScale.value; scaleY = fabScale.value }
            ) {
                Text(text = "+", fontSize = 36.sp, fontWeight = FontWeight.Light, lineHeight = 36.sp)
            }
            TextButton(onClick = onDecrement) {
                Text(text = "undo", color = SubduedColor, fontSize = 13.sp, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun LandscapeCounterLayout(
    count: Int,
    goal: Int,
    streak: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onShowGoalDialog: () -> Unit,
    onShowHistory: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var pressKey by remember { mutableIntStateOf(0) }
    val fabScale = remember { Animatable(1f) }
    val counterShake = remember { Animatable(0f) }
    val guiltAlpha = remember { Animatable(0f) }
    val guiltFraction = if (goal > 0) (count.toFloat() / goal).coerceIn(0f, 1f) else 0f
    val fabColor = lerp(AccentColor, DangerColor, guiltFraction)
    val currentMessage = guiltMessages[(count - 1).coerceAtLeast(0) % guiltMessages.size]

    CounterAnimationEffects(pressKey, fabScale, counterShake, guiltAlpha)

    Column(
        modifier = Modifier.fillMaxSize().background(BackgroundColor).systemBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🔥 $streak day streak",
                color = if (streak > 0) AccentColor else SubduedColor.copy(alpha = 0.4f),
                fontSize = 11.sp, letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onShowHistory) { Text("···", color = SubduedColor, fontSize = 16.sp) }
            TextButton(onClick = onShowGoalDialog) {
                Text(
                    text = if (goal > 0) "/ $goal" else "set goal",
                    color = if (goal > 0) AccentColor else SubduedColor,
                    fontSize = 12.sp, letterSpacing = 1.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = count.toString(),
                    color = lerp(TextColor, DangerColor, guiltAlpha.value),
                    fontSize = 80.sp,
                    fontWeight = if (guiltAlpha.value > 0.05f) FontWeight.Bold else FontWeight.Thin,
                    lineHeight = 80.sp,
                    modifier = Modifier.graphicsLayer { translationX = counterShake.value }
                )
                Text(
                    text = currentMessage,
                    color = DangerColor.copy(alpha = guiltAlpha.value),
                    fontSize = 11.sp, letterSpacing = 1.sp
                )
                if (goal > 0) {
                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { (count.toFloat() / goal).coerceIn(0f, 1f) },
                        modifier = Modifier.width(140.dp),
                        color = if (count >= goal) DangerColor else AccentColor,
                        trackColor = SubduedColor.copy(alpha = 0.15f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val remaining = goal - count
                    Text(
                        text = if (remaining >= 0) "$remaining left" else "+${-remaining} over",
                        color = if (remaining < 0) DangerColor else SubduedColor,
                        fontSize = 11.sp, letterSpacing = 1.sp
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                FloatingActionButton(
                    onClick = { pressKey++; haptic.performHapticFeedback(HapticFeedbackType.LongPress); onIncrement() },
                    containerColor = fabColor,
                    contentColor = Color.Black, shape = CircleShape,
                    modifier = Modifier.size(72.dp).graphicsLayer { scaleX = fabScale.value; scaleY = fabScale.value }
                ) {
                    Text(text = "+", fontSize = 32.sp, fontWeight = FontWeight.Light, lineHeight = 32.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onDecrement) {
                    Text(text = "undo", color = SubduedColor, fontSize = 13.sp, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
fun WeekHistoryChart(history: List<Pair<LocalDate, Int>>, goal: Int) {
    val today = LocalDate.now()
    val maxCount = history.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val barHeight = 56.dp
    val labelHeight = 18.dp

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            history.forEach { (date, count) ->
                val isToday = date == today
                val fraction = (count.toFloat() / maxCount).coerceAtLeast(0.04f)
                val dayLabel = date.format(DateTimeFormatter.ofPattern("EEE")).take(1)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.width(28.dp)
                ) {
                    Canvas(modifier = Modifier.width(14.dp).height(barHeight)) {
                        val h = size.height * fraction
                        drawRect(
                            color = AccentColor,
                            topLeft = Offset(0f, size.height - h),
                            size = Size(size.width, h)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dayLabel,
                        color = if (isToday) TextColor else SubduedColor,
                        fontSize = 10.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.height(labelHeight)
                    )
                }
            }
        }

        if (goal > 0) {
            val goalFraction = (goal.toFloat() / maxCount).coerceIn(0f, 1f)
            Canvas(
                modifier = Modifier.fillMaxWidth().height(barHeight).align(Alignment.TopStart)
            ) {
                val y = size.height * (1f - goalFraction)
                drawLine(
                    color = DangerColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }
    }
}

@Composable
fun TrendsScreen(goal: Int) {
    val context = LocalContext.current
    var trendData by remember { mutableStateOf(emptyList<HistoryEntry>()) }
    var days by rememberSaveable { mutableIntStateOf(7) }
    var windowOffset by rememberSaveable { mutableIntStateOf(0) }
    var dragTranslation by remember { mutableFloatStateOf(0f) }
    var selectedEntry by remember { mutableStateOf<HistoryEntry?>(null) }
    val screenWidthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    LaunchedEffect(days) { windowOffset = 0 }

    LaunchedEffect(days, windowOffset) {
        val endDate = LocalDate.now().minusDays(windowOffset.toLong())
        trendData = loadHistoryWithGoals(context, days, endDate)
    }

    selectedEntry?.let { entry ->
        val prompt = when {
            entry.goal > 0 && entry.count <= entry.goal -> "you stayed under your target. how did it go?"
            entry.goal > 0 && entry.count > entry.goal  -> "you went over your target. what happened?"
            else                                         -> "no target set that day. how was it?"
        }
        NoteDialog(
            date = entry.date,
            currentNote = entry.note,
            prompt = prompt,
            onSave = { note ->
                saveNote(context, entry.date, note)
                trendData = loadHistoryWithGoals(context, days, LocalDate.now().minusDays(windowOffset.toLong()))
                selectedEntry = null
            },
            onDismiss = { selectedEntry = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .systemBarsPadding()
            .padding(top = 40.dp)
            .pointerInput(days, screenWidthPx) {
                val pixelsPerDay = screenWidthPx / days
                var dragAccum = 0f
                var dragStartOffset = 0
                detectHorizontalDragGestures(
                    onDragStart = { dragAccum = 0f; dragStartOffset = windowOffset; dragTranslation = 0f },
                    onDragEnd = {
                        val daysDelta = (-dragAccum / pixelsPerDay).roundToInt()
                        windowOffset = (dragStartOffset + daysDelta).coerceAtLeast(0)
                        dragTranslation = 0f
                        dragAccum = 0f
                    },
                    onDragCancel = { dragTranslation = 0f; dragAccum = 0f; windowOffset = dragStartOffset }
                ) { _, amount ->
                    dragAccum += amount
                    dragTranslation = dragAccum
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("trends", color = SubduedColor, fontSize = 11.sp, letterSpacing = 3.sp)

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                listOf(7, 30).forEach { d ->
                    Text(
                        text = "${d}d",
                        color = if (days == d) TextColor else SubduedColor.copy(alpha = 0.4f),
                        fontSize = 12.sp, letterSpacing = 1.sp,
                        fontWeight = if (days == d) FontWeight.Normal else FontWeight.Thin,
                        modifier = Modifier.clickable { days = d }.padding(8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (windowOffset > 0) {
                Text(
                    text = "today",
                    color = SubduedColor.copy(alpha = 0.5f),
                    fontSize = 11.sp, letterSpacing = 1.sp,
                    modifier = Modifier.clickable { windowOffset = 0 }.padding(8.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { translationX = dragTranslation },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            if (trendData.isNotEmpty()) {
                val avg = trendData.map { it.count }.average().roundToInt()
                val onTarget = trendData.count { it.goal > 0 && it.count <= it.goal }
                val worst = trendData.maxOfOrNull { it.count } ?: 0
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TrendsStat(value = avg.toString(), label = "avg / day")
                    TrendsStat(value = onTarget.toString(), label = "on target")
                    TrendsStat(value = worst.toString(), label = "worst day")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (trendData.isNotEmpty()) {
                TrendsLineChart(
                    history = trendData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 20.dp),
                    onBarTap = { selectedEntry = it }
                )
            }
        }
    }
}

@Composable
private fun TrendsStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TextColor, fontSize = 32.sp, fontWeight = FontWeight.Thin)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, color = SubduedColor, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun TrendsLineChart(
    history: List<HistoryEntry>,
    modifier: Modifier = Modifier,
    onBarTap: ((HistoryEntry) -> Unit)? = null
) {
    if (history.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val maxVal = maxOf(
        history.maxOfOrNull { it.count } ?: 0,
        history.maxOfOrNull { it.goal } ?: 0,
        1
    )
    val n = history.size
    val hasGoals = history.any { it.goal > 0 }
    val labelStyle = TextStyle(color = SubduedColor, fontSize = 9.sp)
    val density = LocalDensity.current
    val leftMarginPx = with(density) { 36.dp.toPx() }
    val bottomMarginPx = with(density) { 36.dp.toPx() }
    val topMarginPx = with(density) { 20.dp.toPx() }

    Canvas(modifier = modifier.then(
        if (onBarTap != null) Modifier.pointerInput(history) {
            detectTapGestures { offset ->
                val chartLeft = leftMarginPx
                val chartRight = size.width.toFloat()
                val chartBottom = size.height - bottomMarginPx
                if (offset.x >= chartLeft && offset.y in topMarginPx..chartBottom) {
                    val chartW = chartRight - chartLeft
                    val slotW = chartW / n
                    val i = ((offset.x - chartLeft) / slotW).toInt().coerceIn(0, n - 1)
                    onBarTap(history[i])
                }
            }
        } else Modifier
    )) {
        val chartLeft = leftMarginPx
        val chartRight = size.width
        val chartBottom = size.height - bottomMarginPx
        val topMargin = topMarginPx
        val chartW = chartRight - chartLeft
        val chartH = chartBottom - topMargin

        val slotW = chartW / n
        val barW = slotW * 0.55f
        fun xCenter(i: Int) = chartLeft + (i + 0.5f) * slotW
        fun yFor(v: Int) = topMargin + (1f - v.toFloat() / maxVal) * chartH
        fun yForF(v: Float) = topMargin + (1f - v / maxVal) * chartH

        val goalDash = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)
        val avgDash  = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f)

        for (i in 0..4) {
            val value = (maxVal.toFloat() * i / 4).roundToInt()
            val y = yFor(value)
            drawLine(Color(0xFF181818), Offset(chartLeft, y), Offset(chartRight, y), 0.5.dp.toPx())
            val label = textMeasurer.measure(value.toString(), labelStyle)
            drawText(label, topLeft = Offset(
                chartLeft - label.size.width - 4.dp.toPx(),
                y - label.size.height / 2f
            ))
        }

        val interval = when { n <= 7 -> 1; else -> 5 }
        history.forEachIndexed { i, entry ->
            if (i % interval == 0 || i == n - 1) {
                val label = textMeasurer.measure(
                    entry.date.format(DateTimeFormatter.ofPattern("M/d")), labelStyle)
                drawText(label, topLeft = Offset(
                    (xCenter(i) - label.size.width / 2f).coerceIn(0f, size.width - label.size.width),
                    chartBottom + 6.dp.toPx()
                ))
            }
        }

        history.forEachIndexed { i, entry ->
            val barColor = when {
                entry.goal > 0 && entry.count <= entry.goal -> Color(0xFF4CAF50)
                entry.goal > 0 && entry.count > entry.goal -> DangerColor
                else -> AccentColor
            }
            val top = yFor(entry.count)
            drawRect(
                color = barColor.copy(alpha = 0.8f),
                topLeft = Offset(xCenter(i) - barW / 2f, top),
                size = Size(barW, chartBottom - top)
            )
        }

        val avgVal = history.map { it.count }.average().toFloat()
        val avgY = yForF(avgVal)
        drawLine(
            color = Color(0xFF3A3A3A),
            start = Offset(chartLeft, avgY),
            end = Offset(chartRight, avgY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = avgDash
        )

        if (hasGoals) {
            for (i in 0 until n - 1) {
                if (history[i].goal > 0 && history[i + 1].goal > 0) {
                    drawLine(
                        color = DangerColor,
                        start = Offset(xCenter(i), yFor(history[i].goal)),
                        end = Offset(xCenter(i + 1), yFor(history[i + 1].goal)),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = goalDash
                    )
                }
            }
        }

        if (n <= 7) {
            val barLabelStyle = TextStyle(color = SubduedColor, fontSize = 9.sp)
            history.forEachIndexed { i, entry ->
                val text = if (entry.goal > 0) "${entry.count}/${entry.goal}" else "${entry.count}"
                val label = textMeasurer.measure(text, barLabelStyle)
                val top = yFor(entry.count)
                drawText(label, topLeft = Offset(
                    xCenter(i) - label.size.width / 2f,
                    top - label.size.height - 3.dp.toPx()
                ))
            }
        }

        history.forEachIndexed { i, entry ->
            if (entry.note.isNotEmpty()) {
                drawCircle(
                    color = AccentColor.copy(alpha = 0.6f),
                    radius = 2.dp.toPx(),
                    center = Offset(xCenter(i), chartBottom + 6.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun GoalDialog(
    currentGoal: Int,
    onSave: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf(if (currentGoal > 0) currentGoal.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text("daily goal", color = TextColor, fontSize = 16.sp, letterSpacing = 1.sp) },
        text = {
            Column {
                Text("max cigarettes per day", color = SubduedColor, fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp))
                BasicTextField(
                    value = input,
                    onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) input = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(color = TextColor, fontSize = 28.sp,
                        fontWeight = FontWeight.Thin, textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center) {
                            if (input.isEmpty()) Text("0", color = SubduedColor, fontSize = 28.sp,
                                fontWeight = FontWeight.Thin, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth())
                            inner()
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(input.toIntOrNull() ?: 0) }) {
                Text("save", color = AccentColor, letterSpacing = 1.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onClear) { Text("clear", color = SubduedColor, letterSpacing = 1.sp) }
        }
    )
}

@Composable
fun NumberWheelPicker(
    value: Int,
    range: IntRange,
    label: String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = 44.dp
    val visibleCount = 5
    val paddingCount = visibleCount / 2

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (value - range.first).coerceIn(0, range.count() - 1)
    )
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val newValue = (range.first + listState.firstVisibleItemIndex).coerceIn(range)
            onValueChange(newValue)
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = SubduedColor, fontSize = 10.sp, letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 6.dp))
        Box(modifier = Modifier.fillMaxWidth().height(itemHeight * visibleCount)) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(itemHeight)
                    .background(SubduedColor.copy(alpha = 0.07f))
            )
            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(paddingCount) { Box(Modifier.height(itemHeight)) }
                items(range.count()) { index ->
                    val dist by remember { derivedStateOf { abs(index - listState.firstVisibleItemIndex) } }
                    val alpha = when (dist) { 0 -> 1f; 1 -> 0.4f; else -> 0.12f }
                    val size = when (dist) { 0 -> 26.sp; 1 -> 17.sp; else -> 13.sp }
                    val weight = if (dist == 0) FontWeight.Normal else FontWeight.Light
                    Box(
                        Modifier.height(itemHeight).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (range.first + index).toString(),
                            color = TextColor.copy(alpha = alpha),
                            fontSize = size,
                            fontWeight = weight
                        )
                    }
                }
                items(paddingCount) { Box(Modifier.height(itemHeight)) }
            }
        }
    }
}

@Composable
fun NoteDialog(
    date: LocalDate,
    currentNote: String,
    prompt: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val dateLabel = date.format(DateTimeFormatter.ofPattern("MMMM d")).lowercase()
    var input by remember { mutableStateOf(currentNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text(dateLabel, color = TextColor, fontSize = 16.sp, letterSpacing = 1.sp) },
        text = {
            Column {
                Text(prompt, color = SubduedColor, fontSize = 11.sp,
                    letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = TextStyle(color = TextColor, fontSize = 14.sp, lineHeight = 20.sp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            if (input.isEmpty()) Text(
                                "stress, boredom, social...",
                                color = SubduedColor.copy(alpha = 0.5f), fontSize = 14.sp
                            )
                            inner()
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(input.trim()) }) {
                Text("save", color = AccentColor, letterSpacing = 1.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("cancel", color = SubduedColor, letterSpacing = 1.sp)
            }
        }
    )
}

@Composable
fun EditDayDialog(
    date: LocalDate,
    currentCount: Int,
    currentGoal: Int,
    currentNote: String,
    onSave: (count: Int, goal: Int, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var count by remember { mutableIntStateOf(currentCount) }
    var goal by remember { mutableIntStateOf(currentGoal) }
    var noteInput by remember { mutableStateOf(currentNote) }
    val dateLabel = date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")).lowercase()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text(dateLabel, color = TextColor, fontSize = 16.sp, letterSpacing = 1.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumberWheelPicker(
                        value = count, range = 0..99, label = "cigarettes",
                        onValueChange = { count = it }, modifier = Modifier.weight(1f)
                    )
                    NumberWheelPicker(
                        value = goal, range = 0..99, label = "target",
                        onValueChange = { goal = it }, modifier = Modifier.weight(1f)
                    )
                }
                Column {
                    Text("note", color = SubduedColor, fontSize = 10.sp,
                        letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 6.dp))
                    BasicTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        textStyle = TextStyle(color = TextColor, fontSize = 13.sp, lineHeight = 18.sp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                if (noteInput.isEmpty()) Text("why today?",
                                    color = SubduedColor.copy(alpha = 0.5f), fontSize = 13.sp)
                                inner()
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(count, goal, noteInput.trim()) }) {
                Text("save", color = AccentColor, letterSpacing = 1.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("cancel", color = SubduedColor, letterSpacing = 1.sp) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryDialog(
    currentGoal: Int,
    onSave: (LocalDate, Int, Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var count by remember { mutableIntStateOf(0) }
    var goal by remember { mutableIntStateOf(currentGoal) }
    var noteInput by remember { mutableStateOf("") }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("ok", color = AccentColor) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("cancel", color = SubduedColor)
                }
            }
        ) { DatePicker(state = datePickerState) }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Color(0xFF1A1A1A),
            title = { Text("add entry", color = TextColor, fontSize = 16.sp, letterSpacing = 1.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = selectedDate?.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
                                ?: "select date",
                            color = if (selectedDate != null) TextColor else SubduedColor,
                            fontSize = 15.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NumberWheelPicker(
                            value = count, range = 0..99, label = "cigarettes",
                            onValueChange = { count = it }, modifier = Modifier.weight(1f)
                        )
                        NumberWheelPicker(
                            value = goal, range = 0..99, label = "target",
                            onValueChange = { goal = it }, modifier = Modifier.weight(1f)
                        )
                    }
                    Column {
                        Text("note", color = SubduedColor, fontSize = 10.sp,
                            letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 6.dp))
                        BasicTextField(
                            value = noteInput,
                            onValueChange = { noteInput = it },
                            textStyle = TextStyle(color = TextColor, fontSize = 13.sp, lineHeight = 18.sp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    if (noteInput.isEmpty()) Text("why today?",
                                        color = SubduedColor.copy(alpha = 0.5f), fontSize = 13.sp)
                                    inner()
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val date = selectedDate ?: return@TextButton
                    onSave(date, count, goal, noteInput.trim())
                }) {
                    Text("save",
                        color = if (selectedDate != null) AccentColor else SubduedColor,
                        letterSpacing = 1.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("cancel", color = SubduedColor, letterSpacing = 1.sp)
                }
            }
        )
    }
}

@Composable
fun HistoryEditorScreen(goal: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var allHistory by remember { mutableStateOf(emptyList<HistoryEntry>()) }
    var editTarget by remember { mutableStateOf<HistoryEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { allHistory = loadAllHistory(context) }
    BackHandler { onDismiss() }

    Box(
        modifier = Modifier.fillMaxSize().background(BackgroundColor).systemBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("history", color = TextColor, fontSize = 20.sp,
                fontWeight = FontWeight.Thin, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { showAddDialog = true }) {
                Text("+", color = AccentColor, fontSize = 20.sp)
            }
            TextButton(onClick = onDismiss) {
                Text("close", color = SubduedColor, fontSize = 13.sp, letterSpacing = 1.sp)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 72.dp),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp)
        ) {
            items(allHistory) { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editTarget = entry }
                        .padding(vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                            color = TextColor, fontSize = 15.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = entry.count.toString(),
                                color = if (entry.goal > 0 && entry.count > entry.goal)
                                    DangerColor else AccentColor,
                                fontSize = 15.sp
                            )
                            if (entry.goal > 0) {
                                Text("/ ${entry.goal}", color = SubduedColor, fontSize = 13.sp)
                            }
                        }
                    }
                    if (entry.note.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = entry.note,
                            color = SubduedColor, fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = SubduedColor.copy(alpha = 0.15f))
                }
            }
        }
    }

    if (showAddDialog) {
        AddEntryDialog(
            currentGoal = goal,
            onSave = { date, count, entryGoal, note ->
                saveHistoryEntry(context, date, count, entryGoal)
                saveNote(context, date, note)
                allHistory = loadAllHistory(context)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    editTarget?.let { entry ->
        EditDayDialog(
            date = entry.date,
            currentCount = entry.count,
            currentGoal = entry.goal,
            currentNote = entry.note,
            onSave = { newCount, newGoal, newNote ->
                saveHistoryEntry(context, entry.date, newCount, newGoal)
                saveNote(context, entry.date, newNote)
                allHistory = loadAllHistory(context)
                editTarget = null
            },
            onDismiss = { editTarget = null }
        )
    }
}
