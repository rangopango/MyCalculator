
package com.sabil.calculator

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay

private val Bg = Color(0xFF101014)
private val CardBg = Color(0xFF222229)
private val Orange = Color(0xFFFF9800)
private val Green = Color(0xFF4CAF50)
private val Muted = Color(0xFF9E9EA7)
private val Border = Color(0xFF34343C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CalculatorApp() }
    }
}

private fun fmt(n: Double): String {
    if (!n.isFinite()) return "Error"
    return BigDecimal.valueOf(n).stripTrailingZeros().toPlainString()
}

// Accepts both Indian grouping (10,00,000) and international grouping (100,000,000).
private fun parseAmount(raw: String): Double? =
    raw.trim().replace(",", "").replace(" ", "").toDoubleOrNull()?.takeIf { it.isFinite() }

private fun formatMoney(value: Double): String {
    val numberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }
    return "₹${numberFormat.format(value)}"
}

private fun dateTime(timestamp: Long): String =
    SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()).format(Date(timestamp))

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
            value = if (op == '*') value * next else {
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
            require(i < s.length && s[i] == ')') { "Missing closing parenthesis" }
            i++
            return value
        }
        val start = i
        var decimalFound = false
        while (i < s.length) {
            val c = s[i]
            when {
                c.isDigit() -> i++
                c == '.' && !decimalFound -> {
                    decimalFound = true
                    i++
                }
                else -> break
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

private data class Calculation(
    val id: String,
    val expression: String,
    val result: String,
    val remark: String,
    val timestamp: Long,
    val project: String
)

private data class DeletedCalculation(
    val record: Calculation,
    val deletedAt: Long
)

private const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000

private data class Project(
    val id: String,
    val name: String,
    val budget: Double
)

private fun loadCalculations(context: Context): List<Calculation> = try {
    val json = JSONArray(
        context.getSharedPreferences("mycalc", Context.MODE_PRIVATE)
            .getString("history", "[]")
    )
    (0 until json.length()).map { i ->
        val o = json.getJSONObject(i)
        Calculation(
            id = o.optString("id"),
            expression = o.optString("expression"),
            result = o.optString("result"),
            remark = o.optString("remark"),
            timestamp = o.optLong("timestamp"),
            project = o.optString("project", "General")
        )
    }
} catch (_: Exception) {
    emptyList()
}

private fun saveCalculations(context: Context, records: List<Calculation>) {
    val json = JSONArray()
    records.forEach { record ->
        json.put(JSONObject().apply {
            put("id", record.id)
            put("expression", record.expression)
            put("result", record.result)
            put("remark", record.remark)
            put("timestamp", record.timestamp)
            put("project", record.project)
        })
    }
    context.getSharedPreferences("mycalc", Context.MODE_PRIVATE)
        .edit().putString("history", json.toString()).apply()
}

private fun loadDeletedCalculations(context: Context): List<DeletedCalculation> = try {
    val cutoff = System.currentTimeMillis() - TRASH_RETENTION_MS
    val json = JSONArray(
        context.getSharedPreferences("mycalc", Context.MODE_PRIVATE)
            .getString("trash", "[]")
    )
    (0 until json.length()).mapNotNull { i ->
        val o = json.getJSONObject(i)
        val record = Calculation(
            id = o.optString("id"),
            expression = o.optString("expression"),
            result = o.optString("result"),
            remark = o.optString("remark"),
            timestamp = o.optLong("timestamp"),
            project = o.optString("project", "General")
        )
        val deletedAt = o.optLong("deletedAt")
        if (deletedAt >= cutoff) DeletedCalculation(record, deletedAt) else null
    }
} catch (_: Exception) {
    emptyList()
}

private fun saveDeletedCalculations(context: Context, deleted: List<DeletedCalculation>) {
    val cutoff = System.currentTimeMillis() - TRASH_RETENTION_MS
    val json = JSONArray()
    deleted.filter { it.deletedAt >= cutoff }.forEach { item ->
        val record = item.record
        json.put(JSONObject().apply {
            put("id", record.id)
            put("expression", record.expression)
            put("result", record.result)
            put("remark", record.remark)
            put("timestamp", record.timestamp)
            put("project", record.project)
            put("deletedAt", item.deletedAt)
        })
    }
    context.getSharedPreferences("mycalc", Context.MODE_PRIVATE)
        .edit().putString("trash", json.toString()).apply()
}

private fun loadProjects(context: Context): List<Project> = try {
    val json = JSONArray(
        context.getSharedPreferences("mycalc", Context.MODE_PRIVATE)
            .getString("projects", "[]")
    )
    (0 until json.length()).map { i ->
        val o = json.getJSONObject(i)
        Project(o.optString("id"), o.optString("name"), o.optDouble("budget"))
    }
} catch (_: Exception) {
    emptyList()
}

private fun saveProjects(context: Context, projects: List<Project>) {
    val json = JSONArray()
    projects.forEach { project ->
        json.put(JSONObject().apply {
            put("id", project.id)
            put("name", project.name)
            put("budget", project.budget)
        })
    }
    context.getSharedPreferences("mycalc", Context.MODE_PRIVATE)
        .edit().putString("projects", json.toString()).apply()
}

@Composable
fun CalculatorApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var history by remember { mutableStateOf(loadCalculations(context)) }
    var projects by remember { mutableStateOf(loadProjects(context)) }
    var trash by remember { mutableStateOf(loadDeletedCalculations(context)) }

    // Kept for the current app session. A fresh app launch starts at General.
    var selectedProject by remember { mutableStateOf("General") }
    var page by remember { mutableStateOf("Calculator") }
    var display by remember { mutableStateOf("0") }
    var input by remember { mutableStateOf("") }
    var expression by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }
    var resultShown by remember { mutableStateOf(false) }
    var lastSavedRecordId by remember { mutableStateOf<String?>(null) }
    var lastSavedSource by remember { mutableStateOf("") }
    var saveNotification by remember { mutableStateOf("") }

    var showProjectDialog by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }
    var projectBudget by remember { mutableStateOf("") }
    var projectError by remember { mutableStateOf("") }
    var projectToDelete by remember { mutableStateOf<Project?>(null) }
    var projectToEdit by remember { mutableStateOf<Project?>(null) }
    var editProjectName by remember { mutableStateOf("") }
    var editProjectBudget by remember { mutableStateOf("") }
    var editProjectError by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Calculation?>(null) }
    var recordToDelete by remember { mutableStateOf<Calculation?>(null) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var showEmptyTrashConfirm by remember { mutableStateOf(false) }

    fun clearCalculator() {
        display = "0"
        input = ""
        expression = ""
        remark = ""
        resultShown = false
        lastSavedRecordId = null
        lastSavedSource = ""
        saveNotification = ""
    }

    fun showSaveNotification(record: Calculation) {
        if (record.remark.isBlank() && record.project == "General") {
            saveNotification = ""
            return
        }
        saveNotification = buildString {
            if (record.project != "General") append("Saved to ${record.project}")
            else append("Calculation saved")
            if (record.remark.isNotBlank()) {
                if (record.project != "General") append(" · ")
                else append(" · ")
                append("Remark: ${record.remark}")
            }
        }
    }

    fun press(key: String) {
        when (key) {
            "AC" -> clearCalculator()
            "⌫" -> {
                if (resultShown || display == "Error") {
                    clearCalculator()
                } else {
                    input = input.dropLast(1)
                    display = input.ifEmpty { "0" }
                }
            }
            "=" -> {
                try {
                    val source = when {
                        resultShown && lastSavedSource.isNotBlank() -> lastSavedSource
                        input.isNotBlank() -> input
                        else -> ""
                    }
                    if (source.isNotBlank()) {
                        val result = fmt(evaluate(source))
                        val currentRemark = remark.trim()
                        val currentProject = selectedProject
                        val fullExpression = "$source = $result"
                        val existing = lastSavedRecordId?.let { id ->
                            history.firstOrNull { it.id == id }
                        }

                        val sameCalculation = resultShown &&
                                existing != null &&
                                existing.expression == fullExpression &&
                                existing.result == result
                        val unchanged = sameCalculation &&
                                existing!!.remark == currentRemark &&
                                existing.project == currentProject

                        when {
                            // Repeated '=' with no changes must never create duplicates.
                            unchanged -> Unit

                            // An incomplete record can be completed in place while the
                            // displayed calculation is still the current pending entry.
                            sameCalculation && existing != null &&
                                    (existing.remark.isBlank() || existing.project == "General") -> {
                                val updated = existing.copy(
                                    remark = currentRemark,
                                    project = currentProject
                                )
                                history = history.map { if (it.id == existing.id) updated else it }
                                lastSavedRecordId = updated.id
                                lastSavedSource = source
                                showSaveNotification(updated)
                            }

                            // A changed remark/project on a completed record is a new entry.
                            else -> {
                                val record = Calculation(
                                    id = UUID.randomUUID().toString(),
                                    expression = fullExpression,
                                    result = result,
                                    remark = currentRemark,
                                    timestamp = System.currentTimeMillis(),
                                    project = currentProject
                                )
                                history = listOf(record) + history
                                lastSavedRecordId = record.id
                                lastSavedSource = source
                                showSaveNotification(record)
                            }
                        }

                        expression = "$source ="
                        display = result
                        input = result
                        resultShown = true
                    }
                } catch (_: Exception) {
                    expression = input
                    display = "Error"
                    resultShown = true
                }
            }
            "%" -> {
                try {
                    val source = input.ifEmpty { if (display == "Error") "" else display }
                    if (source.isNotBlank()) {
                        // Editing the calculated value starts a different calculation.
                        if (resultShown) {
                            lastSavedRecordId = null
                            lastSavedSource = ""
                        }
                        input = fmt(evaluate(source) / 100.0)
                        display = input
                        expression = ""
                        resultShown = false
                    }
                } catch (_: Exception) {
                    // Leave the calculator unchanged for invalid percentage input.
                }
            }
            "±" -> {
                if (display != "0" && display != "Error") {
                    if (resultShown) {
                        lastSavedRecordId = null
                        lastSavedSource = ""
                    }
                    input = if (input.startsWith("-")) input.drop(1) else "-$input"
                    display = input
                    resultShown = false
                }
            }
            else -> {
                if (resultShown || display == "Error") {
                    // Starting another calculation finalizes the previous saved record.
                    lastSavedRecordId = null
                    lastSavedSource = ""
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

    LaunchedEffect(history) { saveCalculations(context, history) }
    LaunchedEffect(projects) { saveProjects(context, projects) }
    LaunchedEffect(trash) { saveDeletedCalculations(context, trash) }
    LaunchedEffect(saveNotification) {
        if (saveNotification.isNotBlank()) {
            delay(1000)
            saveNotification = ""
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Bg).padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("My", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                    Text("Calculator", color = Orange, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                }
                Text("Developed by Masum Mahmud", color = Muted, fontSize = 14.sp)
            }
            TextButton(onClick = { page = "About" }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("About", color = Orange, fontSize = 15.sp)
            }
        }

        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Calculator", "History", "Projects").forEach { item ->
                Button(
                    onClick = { page = item },
                    modifier = Modifier.weight(1f).height(46.dp),
                    contentPadding = PaddingValues(horizontal = 3.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (page == item) Orange else CardBg,
                        contentColor = Color.White
                    )
                ) {
                    Text(item, fontSize = 12.sp, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (page) {
                "Calculator" -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text("CALCULATOR", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))

                        var projectMenu by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { projectMenu = true },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                            ) {
                                Text("Save under: $selectedProject  ▾", color = Color.White, fontSize = 13.sp)
                            }
                            DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("General history") },
                                    onClick = { selectedProject = "General"; projectMenu = false }
                                )
                                projects.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.name) },
                                        onClick = { selectedProject = p.name; projectMenu = false }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("+ Create project") },
                                    onClick = {
                                        projectMenu = false
                                        projectName = ""
                                        projectBudget = ""
                                        projectError = ""
                                        showProjectDialog = true
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(7.dp))
                        OutlinedTextField(
                            value = remark,
                            onValueChange = { remark = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp, max = 56.dp),
                            placeholder = { Text("Add remark", fontSize = 15.sp) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Orange,
                                unfocusedBorderColor = Border,
                                focusedPlaceholderColor = Muted,
                                unfocusedPlaceholderColor = Muted,
                                cursorColor = Orange
                            )
                        )

                        // Only show a brief green confirmation when a remark or project is used.
                        if (saveNotification.isNotBlank()) {
                            Spacer(Modifier.height(5.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF193526),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF34784D))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("✓", color = Color(0xFF65D98B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(saveNotification, color = Color.White, fontSize = 11.sp, maxLines = 2)
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                expression,
                                color = Muted,
                                fontSize = 19.sp,
                                textAlign = TextAlign.End,
                                maxLines = 2
                            )
                            Text(
                                display,
                                color = Color.White,
                                fontSize = 40.sp,
                                textAlign = TextAlign.End,
                                maxLines = 2
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        val rows = listOf(
                            listOf("AC", "⌫", "%", "÷"),
                            listOf("7", "8", "9", "×"),
                            listOf("4", "5", "6", "−"),
                            listOf("1", "2", "3", "+"),
                            listOf("±", "0", ".", "=")
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            rows.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    row.forEach { key ->
                                        Button(
                                            onClick = { press(key) },
                                            modifier = Modifier.weight(1f).height(56.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = RoundedCornerShape(15.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = when (key) {
                                                    "÷", "×", "−", "+" -> Orange
                                                    "=" -> Green
                                                    "AC", "⌫", "%", "±" -> Color(0xFF34343C)
                                                    else -> CardBg
                                                },
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Text(key, fontSize = 21.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "History" -> {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("History", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { page = "Recently Deleted" }, contentPadding = PaddingValues(horizontal = 5.dp)) {
                                Text("Recently Deleted (${trash.size})", color = Orange, fontSize = 11.sp)
                            }
                        }
                        Text("Tap a record to edit its remark or project.", color = Muted, fontSize = 12.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showClearHistoryConfirm = true }, enabled = history.isNotEmpty()) {
                                Text("Clear All History", color = if (history.isNotEmpty()) Color(0xFFFF7777) else Muted, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        if (history.isEmpty()) {
                            Text("No calculations saved yet.", color = Muted)
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 12.dp)
                            ) {
                                items(history, key = { it.id }) { record ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().clickable { editing = record },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = CardBg)
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(record.expression, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                            Text("Result: ${record.result}", color = Green, fontSize = 14.sp)
                                            Text(record.remark.ifBlank { "No remark" }, color = Orange, fontSize = 13.sp)
                                            Text(dateTime(record.timestamp), color = Muted, fontSize = 11.sp)
                                            if (record.project != "General") {
                                                Text("Project: ${record.project}", color = Muted, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "Recently Deleted" -> {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Recently Deleted", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { page = "History" }) { Text("Back", color = Orange) }
                        }
                        Text("Records are kept for 30 days, then deleted permanently.", color = Muted, fontSize = 12.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                if (trash.isNotEmpty()) {
                                    history = trash.map { it.record } + history
                                    trash = emptyList()
                                }
                            }, enabled = trash.isNotEmpty()) { Text("Restore All", color = Green, fontSize = 12.sp) }
                            TextButton(onClick = { showEmptyTrashConfirm = true }, enabled = trash.isNotEmpty()) {
                                Text("Empty Bin", color = if (trash.isNotEmpty()) Color(0xFFFF7777) else Muted, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        if (trash.isEmpty()) {
                            Text("Recently deleted records will appear here.", color = Muted)
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 12.dp)
                            ) {
                                items(trash, key = { it.record.id }) { item ->
                                    val record = item.record
                                    val daysLeft = ((TRASH_RETENTION_MS - (System.currentTimeMillis() - item.deletedAt)) / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = CardBg)
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(record.expression, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                            Text("Result: ${record.result}", color = Green, fontSize = 13.sp)
                                            Text(record.remark.ifBlank { "No remark" }, color = Orange, fontSize = 12.sp)
                                            Text("Deleted ${dateTime(item.deletedAt)} · $daysLeft day(s) left", color = Muted, fontSize = 10.sp)
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                                TextButton(onClick = {
                                                    history = listOf(record) + history
                                                    trash = trash.filterNot { it.record.id == record.id }
                                                }) { Text("Restore", color = Green) }
                                                TextButton(onClick = {
                                                    trash = trash.filterNot { it.record.id == record.id }
                                                }) { Text("Delete forever", color = Color(0xFFFF7777), fontSize = 11.sp) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "Projects" -> {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Projects", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Button(
                                onClick = {
                                    projectName = ""
                                    projectBudget = ""
                                    projectError = ""
                                    showProjectDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Green),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) { Text("+ New") }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (projects.isEmpty()) {
                            Text("Create a project to track a budget, such as Travel or Shopping.", color = Muted)
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 12.dp)
                            ) {
                                items(projects, key = { it.id }) { project ->
                                    val expenses = history.filter { it.project == project.name }
                                    val spent = expenses.sumOf { it.result.toDoubleOrNull() ?: 0.0 }
                                    val remaining = project.budget - spent
                                    val progress = if (project.budget > 0) (spent / project.budget).toFloat().coerceIn(0f, 1f) else 0f
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = CardBg)
                                    ) {
                                        Column(Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(project.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    TextButton(onClick = {
                                                        val report = buildString {
                                                            appendLine("MyCalculator — Project Summary")
                                                            appendLine("Project: ${project.name}")
                                                            appendLine("Budget: ${formatMoney(project.budget)}")
                                                            appendLine("Spent: ${formatMoney(spent)}")
                                                            appendLine("Remaining: ${formatMoney(remaining)}")
                                                            appendLine()
                                                            appendLine("Expenses:")
                                                            if (expenses.isEmpty()) appendLine("No expense records yet.")
                                                            expenses.forEachIndexed { index, expense ->
                                                                appendLine("${index + 1}. ${expense.expression}")
                                                                appendLine("   Amount: ${expense.result}")
                                                                appendLine("   Remark: ${expense.remark.ifBlank { "No remark" }}")
                                                                appendLine("   Date: ${dateTime(expense.timestamp)}")
                                                                appendLine()
                                                            }
                                                            appendLine("Shared from MyCalculator")
                                                        }
                                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                            type = "text/plain"
                                                            putExtra(Intent.EXTRA_SUBJECT, "${project.name} — Project Summary")
                                                            putExtra(Intent.EXTRA_TEXT, report)
                                                        }
                                                        context.startActivity(Intent.createChooser(sendIntent, "Share project"))
                                                    }) {
                                                        Text("Share", color = Green, fontSize = 12.sp)
                                                    }
                                                    TextButton(onClick = {
                                                        projectToEdit = project
                                                        editProjectName = project.name
                                                        editProjectBudget = BigDecimal.valueOf(project.budget).stripTrailingZeros().toPlainString()
                                                        editProjectError = ""
                                                    }) {
                                                        Text("Edit", color = Orange, fontSize = 12.sp)
                                                    }
                                                    TextButton(onClick = { projectToDelete = project }) {
                                                        Text("Delete", color = Color(0xFFFF7777), fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                            Text("Budget: ${formatMoney(project.budget)}", color = Color.White, fontSize = 13.sp)
                                            Spacer(Modifier.height(3.dp))
                                            Text("Spent: ${formatMoney(spent)}", color = Orange, fontSize = 13.sp)
                                            Text("Remaining: ${formatMoney(remaining)}", color = if (remaining < 0) Color.Red else Green, fontSize = 13.sp)
                                            Spacer(Modifier.height(8.dp))
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier.fillMaxWidth().height(7.dp),
                                                color = Green,
                                                trackColor = Color(0xFF414149)
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text("${expenses.size} record(s)", color = Muted, fontSize = 11.sp)
                                            expenses.forEach { expense ->
                                                HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp), color = Border)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    Column(
                                                        modifier = Modifier.weight(1f).clickable { editing = expense }
                                                    ) {
                                                        Text(expense.expression, color = Color.White, fontSize = 13.sp)
                                                        Text("Amount: ${expense.result}", color = Green, fontSize = 12.sp)
                                                        Text("Remark: ${expense.remark.ifBlank { "No remark" }}", color = Muted, fontSize = 12.sp)
                                                        Text(dateTime(expense.timestamp), color = Muted, fontSize = 10.sp)
                                                    }
                                                    TextButton(
                                                        onClick = { recordToDelete = expense },
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                                    ) {
                                                        Text("Delete", color = Color(0xFFFF7777), fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "About" -> {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MyCalculator", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Calculate. Organize. Manage.", color = Muted, fontSize = 14.sp)
                        Spacer(Modifier.height(26.dp))
                        Text("Developed by", color = Muted, fontSize = 14.sp)
                        Text("Masum Mahmud", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Text("Version 2.0.0", color = Orange, fontSize = 14.sp)
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { page = "Calculator" }, colors = ButtonDefaults.buttonColors(containerColor = Orange)) {
                            Text("Back to Calculator")
                        }
                    }
                }
            }
        }
    }

    if (showProjectDialog) {
        AlertDialog(
            onDismissRequest = { showProjectDialog = false },
            title = { Text("Create project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it; projectError = "" },
                        label = { Text("Project name") },
                        placeholder = { Text("Enter project name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = projectBudget,
                        onValueChange = { projectBudget = it; projectError = "" },
                        label = { Text("Budget amount") },
                        placeholder = { Text("10,00,000 or 100,000,000") },
                        supportingText = { Text("Indian and international comma formats are accepted.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        singleLine = true
                    )
                    if (projectError.isNotBlank()) {
                        Text(projectError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // Trim only when saving, so typing spaces normally never blocks input.
                    val name = projectName.trim()
                    val budget = parseAmount(projectBudget)
                    when {
                        name.isBlank() -> projectError = "Enter a project name."
                        projects.any { it.name.equals(name, ignoreCase = true) } -> projectError = "A project with this name already exists."
                        budget == null || budget < 0 -> projectError = "Enter a valid budget amount."
                        else -> {
                            val project = Project(UUID.randomUUID().toString(), name, budget)
                            projects = projects + project
                            selectedProject = name
                            showProjectDialog = false
                            projectError = ""
                        }
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showProjectDialog = false }) { Text("Cancel") } }
        )
    }

    projectToEdit?.let { original ->
        AlertDialog(
            onDismissRequest = { projectToEdit = null },
            title = { Text("Edit project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editProjectName,
                        onValueChange = { editProjectName = it; editProjectError = "" },
                        label = { Text("Project name") },
                        placeholder = { Text("Enter project name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editProjectBudget,
                        onValueChange = { editProjectBudget = it; editProjectError = "" },
                        label = { Text("Budget amount") },
                        placeholder = { Text("10,00,000 or 100,000,000") },
                        supportingText = { Text("Indian and international comma formats are accepted.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        singleLine = true
                    )
                    if (editProjectError.isNotBlank()) {
                        Text(editProjectError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = editProjectName.trim()
                    val newBudget = parseAmount(editProjectBudget)
                    when {
                        newName.isBlank() -> editProjectError = "Enter a project name."
                        projects.any { it.id != original.id && it.name.equals(newName, ignoreCase = true) } ->
                            editProjectError = "A project with this name already exists."
                        newBudget == null || newBudget < 0 -> editProjectError = "Enter a valid budget amount."
                        else -> {
                            projects = projects.map { project ->
                                if (project.id == original.id) project.copy(name = newName, budget = newBudget) else project
                            }
                            history = history.map { record ->
                                if (record.project == original.name) record.copy(project = newName) else record
                            }
                            trash = trash.map { item ->
                                if (item.record.project == original.name) item.copy(record = item.record.copy(project = newName)) else item
                            }
                            if (selectedProject == original.name) selectedProject = newName
                            projectToEdit = null
                            editProjectError = ""
                        }
                    }
                }) { Text("Save changes") }
            },
            dismissButton = {
                TextButton(onClick = { projectToEdit = null }) { Text("Cancel") }
            }
        )
    }

    projectToDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete project?") },
            text = {
                Text("Do you want to delete \"${project.name}\"? Its saved calculations will remain in History and be moved to General.")
            },
            confirmButton = {
                TextButton(onClick = {
                    projects = projects.filterNot { it.id == project.id }
                    history = history.map { record ->
                        if (record.project == project.name) record.copy(project = "General") else record
                    }
                    trash = trash.map { item ->
                        if (item.record.project == project.name) item.copy(record = item.record.copy(project = "General")) else item
                    }
                    if (selectedProject == project.name) selectedProject = "General"
                    projectToDelete = null
                }) { Text("Yes, delete", color = Color(0xFFD32F2F)) }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) { Text("No, cancel") }
            }
        )
    }

    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("Delete calculation?") },
            text = {
                Text("Move \"${record.expression}\" to Recently Deleted? You can restore it for 30 days.")
            },
            confirmButton = {
                TextButton(onClick = {
                    history = history.filterNot { it.id == record.id }
                    trash = listOf(DeletedCalculation(record, System.currentTimeMillis())) + trash.filterNot { it.record.id == record.id }
                    recordToDelete = null
                }) { Text("Move to Recently Deleted", color = Color(0xFFD32F2F)) }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) { Text("No, cancel") }
            }
        )
    }

    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text("Clear All History?") },
            text = { Text("Move all ${history.size} calculation record(s) to Recently Deleted? You can restore them for 30 days. Projects and budgets will remain unchanged.") },
            confirmButton = {
                TextButton(onClick = {
                    val now = System.currentTimeMillis()
                    val existingIds = trash.map { it.record.id }.toSet()
                    trash = history.filterNot { it.id in existingIds }.map { DeletedCalculation(it, now) } + trash
                    history = emptyList()
                    showClearHistoryConfirm = false
                    page = "History"
                }) { Text("Yes, Clear All", color = Color(0xFFD32F2F)) }
            },
            dismissButton = { TextButton(onClick = { showClearHistoryConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showEmptyTrashConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirm = false },
            title = { Text("Empty Recently Deleted?") },
            text = { Text("Permanently delete all ${trash.size} record(s)? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    trash = emptyList()
                    showEmptyTrashConfirm = false
                }) { Text("Yes, Delete Forever", color = Color(0xFFD32F2F)) }
            },
            dismissButton = { TextButton(onClick = { showEmptyTrashConfirm = false }) { Text("Cancel") } }
        )
    }

    editing?.let { original ->
        var editedRemark by remember(original.id) { mutableStateOf(original.remark) }
        var editedProject by remember(original.id) { mutableStateOf(original.project) }
        var editProjectMenu by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit calculation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(original.expression, fontWeight = FontWeight.SemiBold)
                    Text("Result: ${original.result}", color = Green)
                    Text(dateTime(original.timestamp), color = Muted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = editedRemark,
                        onValueChange = { editedRemark = it },
                        label = { Text("Remark") },
                        placeholder = { Text("Add remark") },
                        singleLine = true
                    )
                    Box {
                        OutlinedButton(onClick = { editProjectMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Project: $editedProject")
                        }
                        DropdownMenu(expanded = editProjectMenu, onDismissRequest = { editProjectMenu = false }) {
                            DropdownMenuItem(text = { Text("General history") }, onClick = { editedProject = "General"; editProjectMenu = false })
                            projects.forEach { project ->
                                DropdownMenuItem(text = { Text(project.name) }, onClick = { editedProject = project.name; editProjectMenu = false })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    history = history.map { record ->
                        if (record.id == original.id) record.copy(remark = editedRemark.trim(), project = editedProject) else record
                    }
                    editing = null
                }) { Text("Save changes") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        history = history.filterNot { it.id == original.id }
                        editing = null
                    }) { Text("Delete record", color = Color(0xFFD32F2F)) }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            }
        )
    }
}
