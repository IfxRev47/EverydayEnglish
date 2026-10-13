package com.oxford.a1.ai

/** Loaded only when the user requests inference; no server or network dependency. */
object LlamaNative {
    init { System.loadLibrary("ollama") }
    external fun reset()
    external fun cancel()
    external fun load(path: ByteArray, threads: Int, contextSize: Int)
    external fun generate(messages: Array<ByteArray>, skill: ByteArray, temperature: Float,
        topP: Float, topK: Int, repeatPenalty: Float, maxTokens: Int, listener: TextListener)
    external fun unload()
}
fun interface TextListener { fun onText(bytes: ByteArray) }
