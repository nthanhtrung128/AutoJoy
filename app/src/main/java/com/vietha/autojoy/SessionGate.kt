package com.vietha.autojoy

/** Main-thread session ownership: callbacks from cancelled/finished runs cannot act. */
class SessionGate {
    private var serial = 0L
    private var current: Long? = null
    val isBusy get() = current != null
    fun begin(): Long? {
        if (isBusy) return null
        return (++serial).also { current = it }
    }
    fun owns(token: Long) = current == token
    fun finish(token: Long): Boolean {
        if (!owns(token)) return false
        current = null
        return true
    }
    fun cancel() { current = null; serial++ }
}
