package com.r4g3baby.simplescore.bukkit.hooks

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SafePlaceholderResolverTest {
    @Test fun `failing expansion does not prevent healthy expansions or literal text`() {
        val reported = mutableListOf<String>()
        val resolver = SafePlaceholderResolver<String>(
            resolve = { token, player ->
                if (token.startsWith("%broken_")) error("broken expansion")
                if (token == "%healthy_name%") player else token
            },
            report = { identifier, _ -> reported.add(identifier) }
        )
        assertEquals("Hello Alex %broken_value% %unknown_value% 50%",
            resolver.replace("Hello %healthy_name% %broken_value% %unknown_value% 50%", "Alex"))
        assertEquals(listOf("broken"), reported)
    }

    @Test fun `failure pauses only the failing expansion and retries after 30 seconds`() {
        var now = 0L
        var attempts = 0
        var healthy = false
        var warnings = 0
        val resolver = SafePlaceholderResolver<Unit>(
            resolve = { _, _ -> attempts++; if (!healthy) error("broken") else "recovered" },
            report = { _, _ -> warnings++ }, clock = { now }
        )
        assertEquals("%broken_one% %broken_two%", resolver.replace("%broken_one% %broken_two%", Unit))
        now = TimeUnit.SECONDS.toNanos(29)
        resolver.replace("%BROKEN_other%", Unit)
        assertEquals(1, attempts)
        assertEquals(1, warnings)
        healthy = true
        now = TimeUnit.SECONDS.toNanos(30)
        assertEquals("recovered", resolver.replace("%broken_one%", Unit))
        assertEquals("recovered", resolver.replace("%broken_one%", Unit))
        assertEquals(3, attempts)
    }

    @Test fun `repeated failures log once per retry window`() {
        var now = 0L
        var warnings = 0
        val resolver = SafePlaceholderResolver<Unit>(
            resolve = { _, _ -> error("broken") }, report = { _, _ -> warnings++ }, clock = { now })
        repeat(10) { resolver.replace("%broken_value%", Unit) }
        assertEquals(1, warnings)
        now = TimeUnit.SECONDS.toNanos(30)
        repeat(10) { resolver.replace("%broken_value%", Unit) }
        assertEquals(2, warnings)
    }

    @Test fun `incompatible expansion is contained`() {
        val resolver = SafePlaceholderResolver<Unit>(
            resolve = { _, _ -> throw NoSuchMethodError("incompatible API") }, report = { _, _ -> })
        assertEquals("%broken_value%", resolver.replace("%broken_value%", Unit))
    }

    @Test fun `fatal JVM failures are not swallowed`() {
        val resolver = SafePlaceholderResolver<Unit>(
            resolve = { _, _ -> throw OutOfMemoryError("test") }, report = { _, _ -> })
        assertFailsWith<OutOfMemoryError> { resolver.replace("%broken_value%", Unit) }
    }

    @Test fun `results remain literal and are not parsed recursively or shared between players`() {
        val resolver = SafePlaceholderResolver<String>(
            resolve = { _, player -> player }, report = { _, _ -> })
        assertEquals("%other_value% $1", resolver.replace("%healthy_name%", "%other_value% $1"))
        assertEquals("Alex", resolver.replace("%healthy_name%", "Alex"))
        assertEquals("Sam", resolver.replace("%healthy_name%", "Sam"))
    }

    @Test fun `unrecognized syntax never calls external expansions`() {
        val resolver = SafePlaceholderResolver<Unit>(
            resolve = { _, _ -> error("should not be called") }, report = { _, _ -> error("unexpected warning") })
        val text = "plain 50% %missing% {bracket_name}"
        assertEquals(text, resolver.replace(text, Unit))
    }
}
