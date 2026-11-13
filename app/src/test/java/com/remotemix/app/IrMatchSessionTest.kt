package com.remotemix.app

import org.junit.Assert.*
import org.junit.Test

class IrMatchSessionTest {
    private fun session() = IrMatchSession(listOf(
        IrMatchCandidate("a", "same-power", "vol+"),
        IrMatchCandidate("b", "same-power", "vol+"),
        IrMatchCandidate("c", "other-power", "up")
    ))

    @Test fun powerYesNarrowsToVariantsAndSecondKeyChoosesCode() {
        val match = session()
        assertEquals(2, match.totalGroups)
        assertEquals("a", match.current()!!.codeId)
        match.markSent()
        assertNull(match.answer(true))
        assertEquals(IrMatchPhase.VERIFY, match.current()!!.phase)
        match.markSent()
        assertNull(match.answer(false))
        assertEquals("b", match.current()!!.codeId)
        match.markSent()
        assertEquals("b", match.answer(true))
    }

    @Test fun noResponseSkipsSharedPowerAndBackRestoresPreviousProbe() {
        val match = session()
        match.markSent()
        assertNull(match.answer(false))
        assertEquals("c", match.current()!!.codeId)
        assertTrue(match.back())
        assertEquals("a", match.current()!!.codeId)
        assertFalse(match.current()!!.sent)
    }

    @Test fun skipAndExhaustionDoNotSelectAnUntestedCode() {
        val match = session()
        match.skip()
        assertEquals("c", match.current()!!.codeId)
        match.skip()
        assertNull(match.current())
    }

    @Test(expected = IllegalStateException::class)
    fun answerRequiresAnActualTransmission() { session().answer(true) }

    @Test fun restoreKeepsProgressButRequiresRetransmission() {
        val before = session()
        before.markSent()
        before.answer(true)
        before.markSent()
        val restored = session()
        restored.restore(before.snapshot())
        assertEquals(IrMatchPhase.VERIFY, restored.current()!!.phase)
        assertFalse(restored.current()!!.sent)
        assertTrue(restored.back())
        assertEquals(IrMatchPhase.POWER, restored.current()!!.phase)
    }
}
