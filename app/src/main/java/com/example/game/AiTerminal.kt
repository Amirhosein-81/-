package com.example.game

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class Message(
    val sender: String, // "AI" or "USER"
    val text: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTerminal(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var rawInput by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(
            Message("AI", "WELCOME TO THE SEGA AI TACTICAL OPERATOR CONSOLE.\nACTIVATE 'HIGH THINKING' MODE TO SOLVE COMBAT CHALLENGES."),
            Message("AI", "CHOOSE A TACTICAL OPTION BELOW OR TEXT YOUR STRATEGY QUERY DIRECTLY TO THE CROWN ADVISOR.")
        )
    }

    var isQuerying by remember { mutableStateOf(false) }
    var thinkingLog by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val predefinedQueries = listOf(
        "Axel Stone: Combos & Strengths?",
        "Blaze Fielding: Acrobatic counters?",
        "Mr. X: Ultimate boss strategy?",
        "Composer Yuzo Koshiro: Streets of Rage music legacy?"
    )

    fun sendStrategyQuery(promptText: String) {
        if (promptText.trim().isEmpty() || isQuerying) return
        
        messages.add(Message("USER", promptText))
        rawInput = ""
        isQuerying = true
        thinkingLog = "BOOTING SECURE NEURAL TRANSCEIVER (gemini-3.1-pro-preview)..."

        scope.launch {
            val responseText = queryGeminiThinking(promptText) { progress ->
                thinkingLog = progress
            }
            messages.add(Message("AI", responseText))
            isQuerying = false
            thinkingLog = ""
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF03080F),
                        Color(0xFF09121E)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header Console title block
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .background(Color(0xFF131F32), RoundedCornerShape(8.dp))
                        .testTag("terminal_back_button")
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Cyan)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "AI TACTICAL ADVISOR",
                        color = Color.Cyan,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "GRAND MASTER SECOGNITION SYSTEM",
                        color = Color.Magenta,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .background(Color(0xFF33001A), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFFFF007F), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "THINKING: HIGH",
                        color = Color(0xFFFF007F),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Scrollable messages monitor panel
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, Color(0xFF132A44), RoundedCornerShape(8.dp))
                    .background(Color(0xFF020408))
                    .padding(12.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    reverseLayout = false
                ) {
                    items(messages) { msg ->
                        Text(
                            text = if (msg.sender == "USER") "PLAYER: ${msg.text}" else "ADVISOR: ${msg.text}",
                            color = if (msg.sender == "USER") Color.Yellow else Color(0xFF00FFCC),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = if (msg.sender == "AI") FontWeight.Medium else FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (msg.sender == "USER") Color(0x1F223300) else Color(0x1300FFCC),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(8.dp)
                        )
                    }

                    if (isQuerying) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1105))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.Yellow,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = thinkingLog,
                                        color = Color.Yellow,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick suggested tactical query topics
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "SUGGESTED INTEL TRANSMISSIONS:",
                color = Color.Gray,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                predefinedQueries.take(2).forEach { query ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF0F1B2E), RoundedCornerShape(6.dp))
                            .border(1.5.dp, Color.Cyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .clickable { sendStrategyQuery(query) }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = query,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                predefinedQueries.drop(2).forEach { query ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF0F1B2E), RoundedCornerShape(6.dp))
                            .border(1.5.dp, Color.Cyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .clickable { sendStrategyQuery(query) }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = query,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Direct keyboard terminal input box
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it },
                    placeholder = { Text("ASK AI STRATEGY...", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                    textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF050E18),
                        unfocusedContainerColor = Color(0xFF02060C),
                        focusedBorderColor = Color.Cyan,
                        unfocusedBorderColor = Color(0xFF132A44)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("terminal_input_field")
                )

                IconButton(
                    onClick = { sendStrategyQuery(rawInput) },
                    modifier = Modifier
                        .background(Color.Cyan, RoundedCornerShape(8.dp))
                        .size(48.dp)
                        .testTag("terminal_send_button")
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black)
                }
            }
        }
    }
}

/**
 * Executes a high intelligence network strategy query to Gemini utilizing the
 * strict models/gemini-3.1-pro-preview with high thinkingLevel.
 */
suspend fun queryGeminiThinking(prompt: String, onProgress: (String) -> Unit): String = withContext(Dispatchers.IO) {
    val apiKey = BuildConfig.GEMINI_API_KEY
    if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
        return@withContext "ERROR: CHIP SECURITY KEY (GEMINI_API_KEY) NOT DETECTED.\nPLEASE INPUT API KEY IN THE SECRETS PANEL ON THE SIDEBAR."
    }

    onProgress("CONNECTING TO GRAND MASTER CENTRAL INTELLIGENCE...")
    val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-pro-preview:generateContent?key=$apiKey"

    // Construct request body with thinkingConfig set to high
    val requestJson = JSONObject().apply {
        put("contents", JSONArray().apply {
            put(JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "You are the Sega Game Advisor. Provide strategic game advice, tips, tricks or retro legacy knowledge about Streets of Rage / Bare Knuckle based on user prompt. Keep it visual and punchy. Prompt: $prompt")
                    })
                })
            })
        })
        put("generationConfig", JSONObject().apply {
            put("thinkingConfig", JSONObject().apply {
                // High thinking triggers deep reasoning cycle in gemini-3.1-pro-preview
                put("thinkingBudget", 2048)
            })
        })
    }

    val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
    val request = Request.Builder()
        .url(endpointUrl)
        .post(requestBody)
        .build()

    onProgress("THINKING MODE ACTIVATED (HIGH INTELLIGENCE reasoning)...")

    try {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            return@withContext "SEGA CENTRAL DATABASE OFFLINE. STATUS CODE: ${response.code}"
        }

        val body = response.body?.string() ?: return@withContext "RECEIVED EMPTY RESPONSE SIGNAL."
        val json = JSONObject(body)
        
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            return@withContext "AI ADVISOR WAS STUNNED BY THE QUERY. NO SOLUTION SIGNAL GENERATED."
        }

        val text = candidates.getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .optString("text")

        text.ifEmpty { "AI ADVISOR DID NOT GENERATE VERBAL INTEL FOR THE TRANSMISSION." }
    } catch (e: Exception) {
        e.printStackTrace()
        "TRANSMISSION FAULT ENCOUNTERED: ${e.message}"
    }
}
