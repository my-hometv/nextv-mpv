package com.softppj.nextv

/** Preserve every due reminder; only one simultaneous auto-tune can own playback. */
internal object EpgDuePolicy {
    data class Batch<T>(
        val automatic:T?,
        val automaticCount:Int,
        val reminders:List<T>
    )
    fun <T> partition(due:List<T>,isAutomatic:(T)->Boolean):Batch<T>{
        val auto=ArrayList<T>()
        val reminders=ArrayList<T>()
        due.forEach{if(isAutomatic(it))auto.add(it) else reminders.add(it)}
        // Follow existing schedule order: the most recently added auto-tune wins.
        return Batch(auto.lastOrNull(),auto.size,reminders)
    }
}
