package com.ctodrs.saqrcode.agent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Majlis chat branching. Editing or regenerating a message no longer throws the old answer away:
 * everything from that message on is kept as a branch, and a small "‹ 2/3 ›" switcher flips between them.
 * Every branch shares one [Branches] object through its first user message. Branches live in memory for the
 * session (a restart keeps only the branch you were on).
 */
class Branches {
    /** The conversation tail (from the branch's user message to the end) of each branch. The active one is stale while live. */
    val tails = mutableListOf<List<ChatItem>>()
    var active by mutableIntStateOf(0)
    var count by mutableIntStateOf(0)
}

object BranchOps {
    /** Keeps the tail from [idx] as a branch, truncates [items] to [idx] and opens a new empty branch. */
    fun fork(items: MutableList<ChatItem>, idx: Int): Branches {
        val old = items[idx] as ChatItem.User
        val b = old.branches ?: Branches().also { it.tails.add(emptyList()); it.count = 1; it.active = 0; old.branches = it }
        b.tails[b.active] = items.subList(idx, items.size).toList()
        while (items.size > idx) items.removeAt(items.size - 1)
        b.tails.add(emptyList()); b.active = b.tails.lastIndex; b.count = b.tails.size
        return b
    }

    /** Call after the new user message was appended at [idx]. */
    fun attach(items: List<ChatItem>, idx: Int, b: Branches): Boolean {
        val u = items.getOrNull(idx) as? ChatItem.User ?: return false
        u.branches = b
        return true
    }

    /** The send did not happen: put the previous branch back and drop the empty one. */
    fun undo(items: MutableList<ChatItem>, b: Branches) {
        val prev = b.tails.lastIndex - 1
        if (prev < 0) return
        items.addAll(b.tails[prev])
        b.tails.removeAt(b.tails.lastIndex); b.active = prev; b.count = b.tails.size
    }

    /** Flip to the next (+1) or previous (-1) branch of the user message at [idx]. */
    fun switch(items: MutableList<ChatItem>, idx: Int, delta: Int): Boolean {
        val u = items.getOrNull(idx) as? ChatItem.User ?: return false
        val b = u.branches ?: return false
        if (b.count < 2) return false
        b.tails[b.active] = items.subList(idx, items.size).toList()
        while (items.size > idx) items.removeAt(items.size - 1)
        b.active = ((b.active + delta) % b.count + b.count) % b.count
        items.addAll(b.tails[b.active])
        return true
    }
}
