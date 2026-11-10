package com.topentris.game

/**
 * Horizontal auto-shift. The first step is immediate, the next waits for DAS,
 * then steps repeat every ARR. ARR of 0 yields one step per frame.
 */
class DasController {
    private var direction = 0
    private var charged = false
    private var elapsedMs = 0.0
    private var arrMsAcc = 0.0

    fun advance(direction: Int, dtMs: Double, dasMs: Int, arrMs: Int): Int {
        if (direction == 0) {
            reset()
            return 0
        }
        if (direction != this.direction) {
            this.direction = direction
            charged = false
            elapsedMs = 0.0
            arrMsAcc = 0.0
            return 1
        }
        if (!charged) {
            elapsedMs += dtMs
            if (elapsedMs >= dasMs) {
                charged = true
                elapsedMs = 0.0
                arrMsAcc = 0.0
                return 1
            }
            return 0
        }
        if (arrMs <= 0) return 1
        arrMsAcc += dtMs
        var steps = 0
        while (arrMsAcc >= arrMs && steps < COLS) {
            arrMsAcc -= arrMs
            steps++
        }
        return steps
    }

    private fun reset() {
        direction = 0
        charged = false
        elapsedMs = 0.0
        arrMsAcc = 0.0
    }
}
