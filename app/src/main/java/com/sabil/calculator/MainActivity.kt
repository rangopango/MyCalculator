
package com.sabil.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalculatorApp()
        }
    }
}

private fun fmt(n: Double): String {
    if (!n.isFinite()) return "Error"
    return BigDecimal.valueOf(n)
        .stripTrailingZeros()
        .toPlainString()
}

private class CalculatorParser(private val s: String) {
    private var i = 0

    fun parse(): Double {
        val result = expression()
        require(i == s.length) { "Invalid expression" }
        require(result.isFinite()) { "Invalid result" }
        return result
    }

    private fun expression(): Double {
        var value = term()

        while (i < s.length && (s[i] == '+' || s[i] == '-')) {
            val op = s[i++]
            val next = term()
            value = if (op == '+') value + next else value - next
        }

        return value
    }

    private fun term(): Double {
        var value = factor()

        while (i < s.length && (s[i] == '*' || s[i] == '/')) {
            val op = s[i++]
            val next = factor()

            value = if (op == '*') {
                value * next
            } else {
                require(next != 0.0) { "Division by zero" }
                value / next
            }
        }

        return value
    }

    private fun factor(): Double {
        if (i < s.length && (s[i] == '+' || s[i] == '-')) {
            val sign = if (s[i++] == '-') -1.0 else 1.0
            return sign * factor()
        }

        if (i < s.length && s[i] == '(') {
            i++
            val value = expression()
            require(i < s.length && s[i] == ')') {
                "Missing closing parenthesis"
            }
            i++
            return value
        }

        val start = i
        var decimalFound = false

        while (i < s.length) {
            val c = s[i]

            if (c.isDigit()) {
                i++
            } else if (c == '.' && !decimalFound) {
                decimalFound = true
                i++
            } else {
                break
            }
        }

        require(i > start) { "Invalid number" }
        return s.substring(start, i).toDouble()
    }
}

private fun evaluate(input: String): Double {
    val cleaned = input
        .replace("×", "*")
        .replace("÷", "/")
        .replace("−", "-")
        .replace(" ", "")

    return CalculatorParser(cleaned).parse()
}

@Composable
fun CalculatorApp() {
    var display by remember { mutableStateOf("0") }
    var expression by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var resultShown by remember { mutableStateOf(false) }

    fun clear() {
        display = "0"
        expression = ""
        input = ""
        resultShown = false
    }

    fun press(key: String) {
        when (key) {
            "AC" -> clear()

            "⌫" -> {
                if (resultShown || display == "Error") {
                    clear()
                } else {
                    input = input.dropLast(1)
                    display = input.ifEmpty { "0" }
                }
            }

            "=" -> {
                if (input.isNotBlank()) {
                    try {
                        val value = evaluate(input)
                        expression = "$input ="
                        display = fmt(value)
                        input = display
                        resultShown = true
                    } catch (_: Exception) {
                        expression = input
                        display = "Error"
                        resultShown = true
                    }
                }
            }

            "%" -> {
                try {
                    val source = input.ifEmpty {
                        if (display == "Error") "" else display
                    }

                    if (source.isNotBlank()) {
                        val value = evaluate(source) / 100.0
                        input = fmt(value)
                        display = input
                        expression = ""
                        resultShown = false
                    }
                } catch (_: Exception) {
                    // Ignore invalid percentage input.
                }
            }

            "±" -> {
                if (display != "0" && display != "Error") {
                    input = if (input.startsWith("-")) {
                        input.drop(1)
                    } else {
                        "-$input"
                    }
                    display = input
                    resultShown = false
                }
            }

            else -> {
                if (resultShown || display == "Error") {
                    if (key in listOf("+", "−", "×", "÷")) {
                        input = if (display == "Error") "" else display
                    } else {
                        input = ""
                        expression = ""
                    }
                    resultShown = false
                }

                input += key
                display = input
                expression = ""
            }
        }
    }

    val rows = listOf(
        listOf("AC", "⌫", "%", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("±", "0", ".", "=")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
            .padding(12.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            text = "MASUM MAHMUD",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "CALCULATOR",
            color = Color.Gray,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = expression,
            color = Color.Gray,
            fontSize = 20.sp,
            textAlign = TextAlign.End,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = display,
            color = Color.White,
            fontSize = 40.sp,
            textAlign = TextAlign.End,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))

        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { key ->
                    Button(
                        onClick = { press(key) },
                        modifier = Modifier
                            .weight(1f)
                            .height(66.dp)
                            .padding(vertical = 2.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (key) {
                                "÷", "×", "−", "+" ->
                                    Color(0xFFFF9800)

                                "=" -> Color(0xFF4CAF50)

                                "AC", "⌫", "%", "±" ->
                                    Color(0xFF34343C)

                                else -> Color(0xFF222229)
                            },
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = key,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
