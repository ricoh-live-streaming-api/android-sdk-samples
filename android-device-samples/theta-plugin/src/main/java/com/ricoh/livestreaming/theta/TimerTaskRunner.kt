package com.ricoh.livestreaming.theta

import java.util.*

class TimerTaskRunner {
    private val timerTaskList: MutableList<Pair<TimerTask, Long>> = mutableListOf()
    private val timers: MutableList<Timer> = mutableListOf()

    fun add(timerTask: TimerTask, interval: Long) {
        timerTaskList.add(Pair(timerTask, interval))
    }

    fun clear() {
        timerTaskList.clear()
        timers.clear()
    }

    fun start() {
        timerTaskList.forEach {
            val timer = Timer(true)
            timer.schedule(it.first, 0, it.second)
            this.timers.add(timer)
        }
    }

    fun stop() {
        timers.forEach {
            it.cancel()
        }
    }
}
