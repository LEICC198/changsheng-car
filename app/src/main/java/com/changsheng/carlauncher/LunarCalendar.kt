package com.changsheng.carlauncher

import android.icu.util.ChineseCalendar
import java.util.Date

object LunarCalendar {
    private val months = arrayOf("正月","二月","三月","四月","五月","六月","七月","八月","九月","十月","冬月","腊月")
    private val days = arrayOf(
        "","初一","初二","初三","初四","初五","初六","初七","初八","初九","初十",
        "十一","十二","十三","十四","十五","十六","十七","十八","十九","二十",
        "廿一","廿二","廿三","廿四","廿五","廿六","廿七","廿八","廿九","三十"
    )

    fun formatOfflineLunar(date: Date = Date()): String = try {
        val cal = ChineseCalendar(date)
        val m = cal.get(ChineseCalendar.MONTH)
        val isLeap = cal.get(ChineseCalendar.IS_LEAP_MONTH) == 1
        val d = cal.get(ChineseCalendar.DAY_OF_MONTH)
        "农历${if (isLeap) "闰" else ""}${months.getOrElse(m) { "" }}${days.getOrElse(d) { d.toString() }}"
    } catch (_: Exception) { "" }
}
