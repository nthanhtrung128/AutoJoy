package com.vietha.autojoy

import org.junit.Assert.*
import org.junit.Test

class SessionGateTest {
    @Test fun rejectsOverlappingRun() {
        val g = SessionGate(); val token = g.begin()!!
        assertNull(g.begin()); assertTrue(g.owns(token))
    }
    @Test fun stopInvalidatesScheduledCallbacks() {
        val g = SessionGate(); val old = g.begin()!!
        g.cancel(); assertFalse(g.owns(old)); assertFalse(g.isBusy)
    }
    @Test fun oldCompletionCannotFinishNewRun() {
        val g = SessionGate(); val old = g.begin()!!
        g.cancel(); val next = g.begin()!!
        assertFalse(g.finish(old)); assertTrue(g.owns(next))
    }
    @Test fun completionIsAcceptedOnce() {
        val g = SessionGate(); val token = g.begin()!!
        assertTrue(g.finish(token)); assertFalse(g.finish(token)); assertNotNull(g.begin())
    }
}
