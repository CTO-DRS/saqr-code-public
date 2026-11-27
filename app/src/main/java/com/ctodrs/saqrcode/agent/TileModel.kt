package com.ctodrs.saqrcode.agent

/**
 * v0.1.24: what the Quick Settings tile shows. Pure so it is unit-tested; the TileService only
 * applies it. [runs] = (project, waitingForUser) for every live turn.
 */
object TileModel {
    enum class State { IDLE, RUNNING, WAITING }

    data class Model(val state: State, val label: String, val subtitle: String, val tapStops: Boolean)

    fun of(runs: List<Pair<String, Boolean>>, arabic: Boolean): Model {
        val label = if (arabic) "صقر" else "Saqr"
        if (runs.isEmpty()) return Model(State.IDLE, label, if (arabic) "جاهز" else "Idle", tapStops = false)
        val waiting = runs.firstOrNull { it.second }
        val head = waiting ?: runs.first()
        val more = runs.size - 1
        val name = head.first.take(18)
        return if (waiting != null)
            Model(State.WAITING, label, (if (arabic) "ينتظرك: " else "Needs you: ") + name + extra(more, arabic), tapStops = false)
        else
            Model(State.RUNNING, label, (if (arabic) "يشتغل: " else "Running: ") + name + extra(more, arabic), tapStops = true)
    }

    private fun extra(more: Int, arabic: Boolean) = if (more <= 0) "" else if (arabic) " +$more" else " +$more"
}
