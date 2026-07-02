package com.github.kr328.clash.design.util

import android.graphics.Color

object ProxyDelayColor {
    private const val TIMEOUT = Short.MAX_VALUE.toInt()

    private val GOOD = Color.rgb(76, 175, 80)
    private val MEDIUM = Color.rgb(255, 152, 0)
    private val BAD = Color.rgb(244, 67, 54)
    private val TIMEOUT_COLOR = Color.rgb(158, 158, 158)

    private val GOOD_BG = Color.argb(26, 76, 175, 80)
    private val MEDIUM_BG = Color.argb(26, 255, 152, 0)
    private val BAD_BG = Color.argb(26, 244, 67, 54)

    fun getDelayTextColor(delay: Int): Int {
        return when {
            delay <= 0 || delay > TIMEOUT -> TIMEOUT_COLOR
            delay < 200 -> GOOD
            delay < 500 -> MEDIUM
            else -> BAD
        }
    }

    fun getDelayChipBackground(delay: Int): Int {
        return when {
            delay <= 0 || delay > TIMEOUT -> Color.TRANSPARENT
            delay < 200 -> GOOD_BG
            delay < 500 -> MEDIUM_BG
            else -> BAD_BG
        }
    }
}
