package com.stark.jarvis.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.gson.Gson
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.stark.jarvis.JarvisConfig
import com.stark.jarvis.skills.AiActionResponse
import com.stark.jarvis.skills.SkillRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class HybridAiBrain(private val context: Context) {
    private val httpClient = OkHttpClient()
    private val gson = Gson()
    private var localLlm: LlmInference? = null

    init {
        try {
            val file = File(JarvisConfig.OFFLINE_MODEL_PATH)
            if (file.exists()) {
                localLlm = LlmInference.createFromOptions(context, LlmInference.LlmInferenceOptions.builder().setModelPath(JarvisConfig.OFFLINE_MODEL_PATH).setMaxTokens(256).build())
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun processCommand(userCommand: String): AiActionResponse = withContext(Dispatchers.IO) {
        SkillRegistry.findInstantOfflineMatch(userCommand)?.let { return@withContext it }
        return@withContext if (isOnline() && JarvisConfig.GEMINI_API_KEY != "YOUR_GEMINI_API_KEY_HERE") {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/${JarvisConfig.ONLINE_MODEL}:generateContent?key=${JarvisConfig.GEMINI_API_KEY}"
                val payload = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "${JarvisConfig.SYSTEM_PROMPT}\nUser: \"$userCommand\"")))))
                val request = Request.Builder().url(url).post(payload.toString().toRequestBody("application/json".toMediaType())).build()
                val raw = JSONObject(httpClient.newCall(request).execute().body?.string() ?: "").getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").replace("```json", "").replace("```", "").trim()
                gson.fromJson(raw, AiActionResponse::class.java)
            } catch (e: Exception) {
                localLlm?.let {
                    gson.fromJson(it.generateResponse("${JarvisConfig.SYSTEM_PROMPT}\nUser: $userCommand\nJSON:").replace("```json", "").replace("```", "").trim(), AiActionResponse::class.java)
                } ?: AiActionResponse(reply = "Offline protocols active, sir.")
            }
        } else {
            localLlm?.let {
                gson.fromJson(it.generateResponse("${JarvisConfig.SYSTEM_PROMPT}\nUser: $userCommand\nJSON:").replace("```json", "").replace("```", "").trim(), AiActionResponse::class.java)
            } ?: AiActionResponse(reply = "Sir, we are offline. Direct hardware controls are fully functional.")
        }
    }
}
