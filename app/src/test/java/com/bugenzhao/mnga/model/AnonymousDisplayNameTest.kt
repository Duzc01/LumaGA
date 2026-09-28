package com.bugenzhao.mnga.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Local JVM tests for [anonymousDisplayName].
 * Vectors mirror the Rust `test_anonymous_name` cases in
 * `rust/logic/service/src/user.rs` so the Kotlin port can't drift.
 */
class AnonymousDisplayNameTest {

    @Test
    fun `converts anony raw names to 6-char display names`() {
        assertEquals(
            "乙谢冯丑万翟",
            anonymousDisplayName("#anony_1161b2b5b7c68764251be6c35de7287b"),
        )
        assertEquals(
            "壬宫窦丁钱甄",
            anonymousDisplayName("#anony_8cec9b35cf118bfdbde7e28d6df94143"),
        )
        // Case from the bug report: quoted anonymous user showed the raw id.
        assertEquals(
            "辰陆牟巳车汪",
            anonymousDisplayName("#anony_e3cb53fc23b76f7f11eb3b7ce6616223"),
        )
    }

    @Test
    fun `returns null for non-anonymous names`() {
        assertNull(anonymousDisplayName("BugenZhao"))
        assertNull(anonymousDisplayName("#anony_bad"))
        assertNull(anonymousDisplayName("#anony_zzz"))
        assertNull(anonymousDisplayName(""))
    }
}
