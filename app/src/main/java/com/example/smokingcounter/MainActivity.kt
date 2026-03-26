package com.example.smokingcounter

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val BackgroundColor = Color(0xFF0D0D0D)
private val TextColor = Color(0xFFFFFFFF)
private val SubduedColor = Color(0xFF666666)
private val AccentColor = Color(0xFFE8A020)

private const val PREFS_NAME = "smoking_prefs"
private const val KEY_COUNT = "count"
private const val KEY_DATE = "last_date"

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
    val today = LocalDate.now().toString()
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(KEY_COUNT, count)
        .putString(KEY_DATE, today)
        .apply()
}

@Composable
fun SmokingCounterApp() {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        count = loadCount(context)
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
            Text(
                text = dayName,
                color = SubduedColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal
            )
            Text(
                text = dateName,
                color = SubduedColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal
            )
        }

        // Counter + label in center
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = count.toString(),
                color = TextColor,
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
        }

        // Buttons at bottom center
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // + FAB
            FloatingActionButton(
                onClick = {
                    count++
                    saveCount(context, count)
                },
                containerColor = AccentColor,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier.size(80.dp)
            ) {
                Text(
                    text = "+",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Light,
                    lineHeight = 36.sp
                )
            }

            // − undo button
            TextButton(
                onClick = {
                    if (count > 0) {
                        count--
                        saveCount(context, count)
                    }
                }
            ) {
                Text(
                    text = "undo",
                    color = SubduedColor,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
