package com.example.smokingcounter

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val BackgroundColor = Color(0xFF0D0D0D)
private val TextColor = Color(0xFFFFFFFF)
private val SubduedColor = Color(0xFF666666)
private val AccentColor = Color(0xFFE8A020)
private val DangerColor = Color(0xFFCF4E4E)

private const val PREFS_NAME = "smoking_prefs"
private const val KEY_COUNT = "count"
private const val KEY_DATE = "last_date"
private const val KEY_GOAL = "goal"
private const val KEY_HISTORY_PREFIX = "history_"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmokingCounterApp()
        }
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
        val count = prefs.getInt("$KEY_HISTORY_PREFIX$date", 0)
        date to count
    }
}

private fun loadGoal(context: Context): Int {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getInt(KEY_GOAL, 0)
}

private fun saveGoal(context: Context, goal: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putInt(KEY_GOAL, goal).apply()
}

@Composable
fun SmokingCounterApp() {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(0) }
    var history by remember { mutableStateOf(emptyList<Pair<LocalDate, Int>>()) }
    var goal by remember { mutableIntStateOf(0) }
    var showGoalDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        count = loadCount(context)
        history = loadHistory(context)
        goal = loadGoal(context)
    }

    val today = LocalDate.now()
    val dayName = today.format(DateTimeFormatter.ofPattern("EEEE"))
    val dateName = today.format(DateTimeFormatter.ofPattern("MMMM d"))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        // Date in top-left
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 28.dp, top = 28.dp)
        ) {
            Text(text = dayName, color = SubduedColor, fontSize = 15.sp, fontWeight = FontWeight.Normal)
            Text(text = dateName, color = SubduedColor, fontSize = 15.sp, fontWeight = FontWeight.Normal)
        }

        // Goal button in top-right
        TextButton(
            onClick = { showGoalDialog = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp, top = 16.dp)
        ) {
            Text(
                text = if (goal > 0) "/ $goal" else "set goal",
                color = if (goal > 0) AccentColor else SubduedColor,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
        }

        // Counter + label + progress in center
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = count.toString(),
                color = if (goal > 0 && count > goal) DangerColor else TextColor,
                fontSize = 120.sp,
                fontWeight = FontWeight.Thin,
                lineHeight = 120.sp
            )
            Text(
                text = "cigarettes",
                color = SubduedColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 2.sp
            )
            if (goal > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { (count.toFloat() / goal).coerceIn(0f, 1f) },
                    modifier = Modifier.width(120.dp),
                    color = if (count >= goal) DangerColor else AccentColor,
                    trackColor = SubduedColor.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$count of $goal today",
                    color = SubduedColor,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        // History chart + buttons at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (history.isNotEmpty()) {
                WeekHistoryChart(history = history, goal = goal)
            }

            FloatingActionButton(
                onClick = {
                    count++
                    saveCount(context, count)
                    history = loadHistory(context)
                },
                containerColor = AccentColor,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier.size(80.dp)
            ) {
                Text(text = "+", fontSize = 36.sp, fontWeight = FontWeight.Light, lineHeight = 36.sp)
            }

            TextButton(
                onClick = {
                    if (count > 0) {
                        count--
                        saveCount(context, count)
                        history = loadHistory(context)
                    }
                }
            ) {
                Text(text = "undo", color = SubduedColor, fontSize = 13.sp, letterSpacing = 1.sp)
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
}

@Composable
fun WeekHistoryChart(history: List<Pair<LocalDate, Int>>, goal: Int) {
    val today = LocalDate.now()
    val maxCount = history.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val barHeight = 56.dp
    val labelHeight = 18.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        history.forEach { (date, count) ->
            val isToday = date == today
            val fraction = (count.toFloat() / maxCount).coerceAtLeast(0.04f)
            val barColor = when {
                goal > 0 && count > goal -> DangerColor
                isToday -> AccentColor
                else -> SubduedColor.copy(alpha = 0.45f)
            }
            val dayLabel = date.format(DateTimeFormatter.ofPattern("EEE")).take(1)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.width(28.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .width(14.dp)
                        .height(barHeight)
                ) {
                    val h = size.height * fraction
                    drawRect(
                        color = barColor,
                        topLeft = Offset(0f, size.height - h),
                        size = Size(size.width, h)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dayLabel,
                    color = if (isToday) TextColor else SubduedColor,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.height(labelHeight)
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
        title = {
            Text(text = "daily goal", color = TextColor, fontSize = 16.sp, letterSpacing = 1.sp)
        },
        text = {
            Column {
                Text(
                    text = "max cigarettes per day",
                    color = SubduedColor,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                BasicTextField(
                    value = input,
                    onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) input = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(
                        color = TextColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Thin,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (input.isEmpty()) {
                                Text(
                                    text = "0",
                                    color = SubduedColor,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Thin,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            inner()
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = input.toIntOrNull() ?: 0
                onSave(parsed)
            }) {
                Text(text = "save", color = AccentColor, letterSpacing = 1.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onClear) {
                Text(text = "clear", color = SubduedColor, letterSpacing = 1.sp)
            }
        }
    )
}
