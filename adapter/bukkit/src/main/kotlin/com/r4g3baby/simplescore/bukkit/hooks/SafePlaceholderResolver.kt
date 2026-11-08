package com.r4g3baby.simplescore.bukkit.hooks

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Contains failures that escape an expansion; fatal JVM errors are deliberately not caught. */
internal class SafePlaceholderResolver<P>(
    private val resolve: (String, P) -> String,
    private val report: (String, Throwable) -> Unit,
    private val clock: () -> Long = System::nanoTime
) {
    private val failures = ConcurrentHashMap<String, Long>()
    private val placeholders = Regex("%([^%_]+)_[^%]*%")
    private val retryAfter = TimeUnit.SECONDS.toNanos(30)

    fun replace(text: String, player: P): String = placeholders.replace(text) { match ->
        val identifier = match.groupValues[1].lowercase(Locale.ROOT)
        val now = clock()
        val failedAt = failures[identifier]
        if (failedAt != null && now - failedAt < retryAfter) return@replace match.value
        try {
            resolve(match.value, player).also { failures.remove(identifier) }
        } catch (exception: RuntimeException) {
            fail(identifier, now, exception)
            match.value
        } catch (error: LinkageError) {
            fail(identifier, now, error)
            match.value
        }
    }

    private fun fail(identifier: String, now: Long, error: Throwable) {
        val previous = failures.put(identifier, now)
        if (previous == null || now - previous >= retryAfter) report(identifier, error)
    }
}
