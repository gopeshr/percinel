package gopesh.percinel

import gopesh.percinel.data.UpdateChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The version comparison decides whether the update banner shows — the one bit most likely
 *  to silently break (e.g. lexical vs numeric compare). */
class UpdateCheckerTest {

    @Test
    fun newer_patch_and_minor() {
        assertTrue(UpdateChecker.isNewer("1.31", "1.30"))
        assertTrue(UpdateChecker.isNewer("1.31", "1.24"))
        assertTrue(UpdateChecker.isNewer("2.0", "1.99"))
    }

    @Test
    fun not_newer_when_same_or_older() {
        assertFalse(UpdateChecker.isNewer("1.30", "1.30")) // same → no banner
        assertFalse(UpdateChecker.isNewer("1.29", "1.30"))
        assertFalse(UpdateChecker.isNewer("1.9", "1.10"))  // installed is actually newer
    }

    @Test
    fun compares_numerically_not_lexically() {
        // The classic trap: "1.10" is NEWER than "1.9", even though "1.9" > "1.10" as strings.
        assertTrue(UpdateChecker.isNewer("1.10", "1.9"))
        assertTrue(UpdateChecker.isNewer("1.100", "1.99"))
    }

    @Test
    fun handles_differing_segment_counts() {
        assertTrue(UpdateChecker.isNewer("1.2.1", "1.2"))   // 1.2.1 > 1.2
        assertFalse(UpdateChecker.isNewer("1.2", "1.2.1"))  // 1.2 < 1.2.1
        assertFalse(UpdateChecker.isNewer("1.2.0", "1.2"))  // 1.2.0 == 1.2 → not newer
    }
}
