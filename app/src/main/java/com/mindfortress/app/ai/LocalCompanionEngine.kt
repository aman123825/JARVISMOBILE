package com.mindfortress.app.ai

import android.content.Context
import com.mindfortress.app.data.PreferencesManager
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

data class CompanionResponse(
    val replyText: String,
    val speechText: String = "",
    val executedAction: String? = null,
    val suggestedPrompt: String? = null,
    /** Non-null means the UI must show an approval dialog before executing this action. */
    val pendingAction: DeviceActionProposal? = null
)

class LocalCompanionEngine(private val context: Context) {

    val actionExecutor = DeviceActionExecutor(context)
    val sovereignBrain = OnDeviceSovereignBrain(context)
    private val prefs = PreferencesManager.getInstance(context)

    // Checks if a local LLM weight file exists in internal/external storage (e.g. gemma-2b.bin / model.bin)
    fun getLocalModelFile(): File? {
        return sovereignBrain.getLocalGgufModel()
    }

    /**
     * Normal entry point. Potential device side effects are converted to proposals
     * and never execute until the HUD routes an explicit user approval.
     */
    fun processInput(userInput: String, speakOutput: Boolean = true): CompanionResponse {
        val proposal = PermissionFirstActionBroker.propose(userInput)
        if (proposal != null) {
            return CompanionResponse(
                replyText = "Approval required: ${proposal.summary}",
                speechText = "",
                executedAction = "ACTION_PENDING_APPROVAL",
                pendingAction = proposal
            )
        }
        return processInputInternal(userInput, speakOutput)
    }

    /**
     * Approved-action entry point. Call only from the explicit approval handler.
     * Keeping the bypass separate prevents the normal input path from executing
     * app-launch or focus-lock commands directly.
     */
    fun processApprovedInput(userInput: String, speakOutput: Boolean = true): CompanionResponse =
        processInputInternal(userInput, speakOutput, allowApprovedDeviceActions = true)

    private fun processInputInternal(
        userInput: String,
        speakOutput: Boolean,
        allowApprovedDeviceActions: Boolean = false
    ): CompanionResponse {
        val clean = userInput.trim()
        val lower = clean.lowercase()

        // 1. Process via On-Device Sovereign Brain First (100% Local, 0ms Network Dependency)
        val sovereignResult = sovereignBrain.processSovereignInput(
            clean,
            allowDeviceActions = allowApprovedDeviceActions
        )
        if (sovereignResult.actionTag != null && sovereignResult.actionTag != "SOVEREIGN_STANDBY") {
            if (speakOutput && sovereignResult.speechText.isNotEmpty()) {
                actionExecutor.speak(sovereignResult.speechText)
            }
            return CompanionResponse(
                replyText = sovereignResult.replyMarkdown,
                speechText = sovereignResult.speechText,
                executedAction = sovereignResult.actionTag,
                suggestedPrompt = sovereignResult.suggestedPrompt
            )
        }

        // 2. Direct Cloud Frontier API (if API Key provided by user)
        if (prefs.frontierApiKey.isNotBlank() && prefs.frontierProvider != "PC_BRAIN") {
            val directCloud = queryDirectCloudApi(clean)
            if (!directCloud.isNullOrBlank()) {
                val concise = directCloud.lines().filter { it.isNotBlank() && !it.startsWith("#") }.take(2).joinToString(" ")
                if (speakOutput && concise.isNotBlank()) {
                    actionExecutor.speak(concise)
                }
                return CompanionResponse(
                    replyText = directCloud,
                    speechText = concise,
                    executedAction = "CLOUD_FRONTIER_RESPONSE",
                    suggestedPrompt = "Tell me more about this"
                )
            }
        }

        // 3. Optional PC Brain Link (Only if explicitly enabled and accessible)
        if (prefs.frontierProvider == "PC_BRAIN" && prefs.brainServerUrl.isNotBlank()) {
            val remoteResp = queryRemoteBrain(clean)
            if (!remoteResp.isNullOrBlank()) {
                val concise = remoteResp.lines().filter { it.isNotBlank() && !it.startsWith("#") }.take(2).joinToString(" ")
                if (speakOutput && concise.isNotBlank()) {
                    actionExecutor.speak(concise)
                }
                return CompanionResponse(
                    replyText = remoteResp,
                    speechText = concise,
                    executedAction = "PC_BRAIN_RESPONSE",
                    suggestedPrompt = "Continue derivation"
                )
            }
        }

        // 4. Fallback to Sovereign Polymath Response (Guaranteed on-device execution)
        if (speakOutput && sovereignResult.speechText.isNotEmpty()) {
            actionExecutor.speak(sovereignResult.speechText)
        }
        return CompanionResponse(
            replyText = sovereignResult.replyMarkdown,
            speechText = sovereignResult.speechText,
            executedAction = "ON_DEVICE_SOVEREIGN",
            suggestedPrompt = sovereignResult.suggestedPrompt
        )
    }

    private fun queryRemoteBrain(prompt: String): String? {
        var result: String? = null
        val thread = Thread {
            try {
                // 1. Try PC Brain on Local Wi-Fi / Host Server
                val serverUrl = prefs.brainServerUrl.ifBlank { "http://10.0.2.2:8080" }
                val url = URL("${serverUrl.trimEnd('/')}/api/chat")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 2500
                conn.readTimeout = 7000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")

                val payload = JSONObject().put("message", prompt).toString()
                conn.outputStream.use { it.write(payload.toByteArray()) }

                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(resp)
                    if (json.has("response")) {
                        result = json.getString("response")
                    }
                }
            } catch (e: Exception) {
                // PC Brain unreachable, attempt direct cloud API
            }

            // 2. If PC Brain not reached, fallback to direct Cloud Frontier API if key is present
            if (result.isNullOrBlank()) {
                result = queryDirectCloudApi(prompt)
            }
        }
        thread.start()
        try {
            thread.join(8000)
        } catch (e: Exception) {
            // Timeout fallback
        }
        return result
    }

    private fun queryDirectCloudApi(prompt: String): String? {
        val apiKey = prefs.frontierApiKey.trim()
        if (apiKey.isEmpty()) return null
        val provider = prefs.frontierProvider.uppercase()

        return try {
            when {
                provider == "GROQ" || apiKey.startsWith("gsk_") -> {
                    val url = URL("https://api.groq.com/openai/v1/chat/completions")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 3500
                    conn.readTimeout = 9000
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Authorization", "Bearer $apiKey")
                    val systemPrompt = "You are JARVIS, Commander Vivek's elite personal AI and Chief of Staff. You speak with high-IQ clarity, precision, and MIT-level pedagogical depth. Master STEM domains (calculus, thermodynamics, CS, physics). Keep answers direct, punchy, and grounded in first principles."
                    val payload = JSONObject()
                        .put("model", "llama-3.3-70b-versatile")
                        .put("messages", org.json.JSONArray()
                            .put(JSONObject().put("role", "system").put("content", systemPrompt))
                            .put(JSONObject().put("role", "user").put("content", prompt)))
                    conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                    if (conn.responseCode == 200) {
                        val resp = conn.inputStream.bufferedReader().use { it.readText() }
                        JSONObject(resp).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    } else null
                }
                provider == "DEEPSEEK" || apiKey.startsWith("sk-") && provider == "DEEPSEEK" -> {
                    val url = URL("https://api.deepseek.com/chat/completions")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 3500
                    conn.readTimeout = 9000
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Authorization", "Bearer $apiKey")
                    val systemPrompt = "You are JARVIS, Commander Vivek's elite personal AI. High-IQ, MIT standard STEM polymath."
                    val payload = JSONObject()
                        .put("model", "deepseek-chat")
                        .put("messages", org.json.JSONArray()
                            .put(JSONObject().put("role", "system").put("content", systemPrompt))
                            .put(JSONObject().put("role", "user").put("content", prompt)))
                    conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                    if (conn.responseCode == 200) {
                        val resp = conn.inputStream.bufferedReader().use { it.readText() }
                        JSONObject(resp).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    } else null
                }
                provider == "OPENAI" || apiKey.startsWith("sk-") -> {
                    val url = URL("https://api.openai.com/v1/chat/completions")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 3500
                    conn.readTimeout = 9000
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Authorization", "Bearer $apiKey")
                    val systemPrompt = "You are JARVIS, Commander Vivek's elite personal AI and Chief of Staff. You speak with high-IQ clarity, precision, and MIT-level depth. Master STEM domains."
                    val payload = JSONObject()
                        .put("model", "gpt-4o-mini")
                        .put("messages", org.json.JSONArray()
                            .put(JSONObject().put("role", "system").put("content", systemPrompt))
                            .put(JSONObject().put("role", "user").put("content", prompt)))
                    conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                    if (conn.responseCode == 200) {
                        val resp = conn.inputStream.bufferedReader().use { it.readText() }
                        JSONObject(resp).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    } else null
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun fetchMorningBriefing(): String? {
        var result: String? = null
        val thread = Thread {
            try {
                val serverUrl = prefs.brainServerUrl.ifBlank { "http://10.0.2.2:8080" }
                val url = URL("${serverUrl.trimEnd('/')}/api/briefing/morning")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 3000
                conn.readTimeout = 6000
                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(resp)
                    if (json.has("briefing_text")) {
                        result = json.getString("briefing_text")
                    }
                }
            } catch (e: Exception) {
                // Silently fallback
            }
        }
        thread.start()
        try { thread.join(6500) } catch (e: Exception) {}
        return result
    }

    fun analyzeVisualInput(imageBase64: String, query: String? = null): String? {
        var result: String? = null
        val thread = Thread {
            try {
                val serverUrl = prefs.brainServerUrl.ifBlank { "http://10.0.2.2:8080" }
                val url = URL("${serverUrl.trimEnd('/')}/api/vision/analyze")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 4000
                conn.readTimeout = 12000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")

                val payload = JSONObject().put("image_base64", imageBase64)
                if (query != null) payload.put("query", query)

                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(resp)
                    if (json.has("whisper_summary")) {
                        result = json.getString("whisper_summary")
                    } else if (json.has("analysis")) {
                        result = json.getString("analysis")
                    }
                }
            } catch (e: Exception) {}
        }
        thread.start()
        try { thread.join(12500) } catch (e: Exception) {}
        return result
    }

    private fun extractMinutes(text: String): Int? {
        val regex = Regex("(\\d+)\\s*(?:min|minute|hour|hr)")
        val match = regex.find(text)
        if (match != null) {
            val num = match.groupValues[1].toIntOrNull() ?: 60
            return if (text.contains("hour") || text.contains("hr")) num * 60 else num
        }
        return null
    }

    private fun generateActiveRecallQuiz(topicText: String): String {
        return """
### Active Recall Diagnostic 🧠

Here is your challenge to test deep conceptual understanding:

**Problem:**
"Explain why multiplying two negative numbers yields a positive number using a concrete physical or geometric analogy (e.g., velocity and direction, or financial credit and debt removal) — WITHOUT simply quoting rules."

Take 60 seconds. Formulate your reasoning and reply back to me. Let's see how deep your mental model goes!
""".trimIndent()
    }

    private fun generateMistakeDrillOffline(): String {
        val drills = listOf(
            """
### Spaced Repetition Drill: Multivariable Calculus 📐
*Targeted Weak Spot: Divergence Theorem & Surface Integrals*

**Problem Challenge:**
Let F = (x^3, y^3, z^3). Calculate the flux integral of F . dS across the unit sphere x^2 + y^2 + z^2 = 1 using the Divergence Theorem. 
State the divergence div(F) first, then evaluate the triple volume integral in spherical coordinates.

Reply with your derived numerical flux or intermediate volume differential step.
""".trimIndent(),
            """
### Spaced Repetition Drill: Classical Thermodynamics 🌡️
*Targeted Weak Spot: Entropy Changes in Non-Isothermal Paths*

**Problem Challenge:**
An ideal gas with constant heat capacity Cv expands from (T_1, V_1) to (T_2, V_2). Derive the exact entropy change Delta S = S_2 - S_1 without assuming constant temperature.
Why is Delta S = Cv*ln(T_2/T_1) + R*ln(V_2/V_1) valid for any reversible or irreversible path between these equilibrium states?

State your thermodynamic reasoning.
""".trimIndent(),
            """
### Spaced Repetition Drill: Algorithmic Complexity & Dynamic Programming ⚡
*Targeted Weak Spot: State Transitions & Space Optimization*

**Problem Challenge:**
In the 0/1 Knapsack problem with weights W, explain why we iterate backwards when optimizing the DP array from 2D O(N * W) space to 1D O(W) space.
What exact bug occurs if you iterate forwards?

Formulate your explanation directly.
""".trimIndent()
        )
        return drills.random()
    }

    private fun generateCompassionateDisciplineResponse(text: String): String {
        return """
### Listen to me closely, my friend. 🤝

I hear that you feel tired or pulled toward quick dopamine. That feeling is not a weakness in your character; it is a **neurochemical trick**. 

Your brain's dopamine pathways want the easiest reward. If you give in now, the regret 30 minutes later will feel 10 times heavier than the friction of starting.

**Our 3-Step Re-alignment Protocol:**
1. **Drop the phone flat on a table.**
2. **Inhale deeply for 4 seconds, exhale for 6 seconds.** (Activates your parasympathetic brake).
3. **Commit to just 5 minutes.** You don't have to study for 5 hours. Just open the page and read 1 paragraph.

I will lock down your phone right now if you give the word. Are you ready?
""".trimIndent()
    }

    private fun generateSocraticExplanation(prompt: String): String {
        return """
### First-Principles Socratic Breakdown 💡

To truly master this at an elite standard, we do not memorize formulas; we uncover the **underlying mechanism**.

1. **The Axiom:** What is the simplest, most fundamental truth about this system that cannot be broken down further?
2. **The Mechanism:** How does energy, information, or state transform from the input to the output?
3. **The Socratic Question for You:** 
If you remove all technical jargon and had to explain this phenomenon to a 12-year-old using everyday objects, what analogy would you choose?

Tell me your intuition, and I will show you how to formalize it with mathematical rigor.
""".trimIndent()
    }
}
