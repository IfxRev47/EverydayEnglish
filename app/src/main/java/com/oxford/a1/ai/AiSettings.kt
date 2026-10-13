package com.oxford.a1.ai

import android.content.Context

/** Persisted separately from vocabulary, skills and model files. */
data class AiSettings(
    val temperature: Float = 0.65f, val topP: Float = 0.9f, val topK: Int = 40,
    val repeatPenalty: Float = 1.1f, val maxTokens: Int = 384, val contextSize: Int = 2048,
    val threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
    val historyRounds: Int = 2, val autoSpeak: Boolean = false,
    val speechRate: Float = 0.85f, val speechPitch: Float = 1f
) {
    fun validate() {
        require(temperature.isFinite() && temperature in 0f..1.5f)
        require(topP.isFinite() && topP in 0.1f..1f && topK in 1..100)
        require(repeatPenalty.isFinite() && repeatPenalty in 1f..1.5f)
        require(maxTokens in 64..768 && contextSize in listOf(1024, 2048, 4096))
        require(threads in 1..8 && historyRounds in 0..2)
        require(speechRate.isFinite() && speechRate in 0.5f..1.5f)
        require(speechPitch.isFinite() && speechPitch in 0.8f..1.2f)
    }
    fun requiresReload(other: AiSettings) = contextSize != other.contextSize || threads != other.threads
}
class AiSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ai-settings", Context.MODE_PRIVATE)
    fun read(): AiSettings = runCatching {
        val d = AiSettings()
        AiSettings(prefs.getFloat("temperature", d.temperature), prefs.getFloat("topP", d.topP),
            prefs.getInt("topK", d.topK), prefs.getFloat("repeatPenalty", d.repeatPenalty),
            prefs.getInt("maxTokens", d.maxTokens), prefs.getInt("contextSize", d.contextSize),
            prefs.getInt("threads", d.threads), prefs.getInt("historyRounds", d.historyRounds),
            prefs.getBoolean("autoSpeak", d.autoSpeak), prefs.getFloat("speechRate", d.speechRate),
            prefs.getFloat("speechPitch", d.speechPitch)).also { it.validate() }
    }.getOrDefault(AiSettings())
    fun save(value: AiSettings) {
        value.validate()
        check(prefs.edit().putFloat("temperature", value.temperature).putFloat("topP", value.topP)
            .putInt("topK", value.topK).putFloat("repeatPenalty", value.repeatPenalty)
            .putInt("maxTokens", value.maxTokens).putInt("contextSize", value.contextSize)
            .putInt("threads", value.threads).putInt("historyRounds", value.historyRounds)
            .putBoolean("autoSpeak", value.autoSpeak).putFloat("speechRate", value.speechRate)
            .putFloat("speechPitch", value.speechPitch).commit()) { "参数保存失败" }
    }
}
