package com.opencamera.core.media

import java.util.Locale

object ContentRecognitionTextMatcher {
    fun matchesAnyToken(texts: Iterable<String>, aliases: Iterable<String>): Boolean {
        val sourceTokens = texts.flatMap { it.toRecognitionTokens() }
        if (sourceTokens.isEmpty()) return false
        return aliases.any { alias ->
            val aliasTokens = alias.toRecognitionTokens()
            aliasTokens.isNotEmpty() && sourceTokens.containsTokenSequence(aliasTokens)
        }
    }

    fun matchesAnyToken(vararg texts: String, aliases: Iterable<String>): Boolean =
        matchesAnyToken(texts.asIterable(), aliases)

    private fun String.toRecognitionTokens(): List<String> =
        TOKEN_REGEX.findAll(lowercase(Locale.US))
            .map { it.value }
            .toList()

    private fun List<String>.containsTokenSequence(aliasTokens: List<String>): Boolean {
        if (aliasTokens.size > size) return false
        return windowed(aliasTokens.size).any { window -> window == aliasTokens }
    }

    private val TOKEN_REGEX = Regex("[a-z0-9]+")
}
