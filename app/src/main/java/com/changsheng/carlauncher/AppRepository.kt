package com.changsheng.carlauncher

import android.content.Context
import android.content.Intent
import android.provider.Settings

class AppRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("changsheng_v5", Context.MODE_PRIVATE)

    fun loadAllLaunchableApps(): List<AppItem> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { AppItem(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .distinctBy { it.packageName }
            .sortedBy { it.label }
    }

    fun findAppByKeywords(apps: List<AppItem>, keywords: List<String>): AppItem? {
        for (kw in keywords) {
            apps.firstOrNull {
                it.label.contains(kw, ignoreCase = true) || it.packageName.contains(kw, ignoreCase = true)
            }?.let { return it }
        }
        return null
    }

    fun findZlinkPackage(apps: List<AppItem>): String? =
        findAppByKeywords(apps, listOf("zlink", "zjinnova", "carplay"))?.packageName

    fun getBoundNavPackage(apps: List<AppItem>): String? {
        val saved = prefs.getString("nav_pkg", null)
        if (saved != null && apps.any { it.packageName == saved }) return saved
        return findAppByKeywords(apps, listOf("高德", "amap"))?.packageName
    }

    fun setBoundNavPackage(pkg: String) = prefs.edit().putString("nav_pkg", pkg).apply()

    fun getDockPackages(allApps: List<AppItem>): MutableList<String> {
        val raw = prefs.getString("dock_pkgs", null)
        if (!raw.isNullOrBlank()) {
            val saved = raw.split(",").filter { pkg -> allApps.any { it.packageName == pkg } }
            if (saved.isNotEmpty()) return saved.toMutableList()
        }
        val defaults = listOf(
            listOf("高德", "amap"),
            listOf("zlink", "zjinnova"),
            listOf("蓝牙电话", "btphone", "bluetooth"),
            listOf("收音机", "radio"),
            listOf("原车功能", "原车信息"),
            listOf("蓝牙音乐", "btav", "music")
        )
        val res = mutableListOf<String>()
        for (keys in defaults) {
            findAppByKeywords(allApps, keys)?.let { if (!res.contains(it.packageName)) res.add(it.packageName) }
        }
        for (app in allApps) {
            if (res.size >= 6) break
            if (!res.contains(app.packageName)) res.add(app.packageName)
        }
        return res
    }

    fun saveDockPackages(pkgs: List<String>) = prefs.edit().putString("dock_pkgs", pkgs.joinToString(",")).apply()

    fun getHiddenPackages(): MutableSet<String> =
        prefs.getStringSet("hidden_pkgs", emptySet())?.toMutableSet() ?: mutableSetOf()

    fun toggleHiddenPackage(pkg: String): Boolean {
        val set = getHiddenPackages()
        val nowHidden = if (set.contains(pkg)) { set.remove(pkg); false } else { set.add(pkg); true }
        prefs.edit().putStringSet("hidden_pkgs", set).apply()
        return nowHidden
    }

    var autoZlinkEnabled: Boolean
        get() = prefs.getBoolean("auto_zlink", true)
        set(v) = prefs.edit().putBoolean("auto_zlink", v).apply()

    var wallpaperIndex: Int
        get() = prefs.getInt("wall_idx", 0)
        set(v) = prefs.edit().putInt("wall_idx", v).apply()

    fun markPendingBootCountdown(pending: Boolean) = prefs.edit().putBoolean("pending_boot", pending).apply()

    fun shouldRunBootCountdown(): Boolean {
        if (!autoZlinkEnabled) return false
        val cur = try { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) } catch (_: Exception) { -1 }
        val last = prefs.getInt("last_boot_count", -2)
        return prefs.getBoolean("pending_boot", false) || (cur != -1 && cur != last)
    }

    fun consumeBootCountdown() {
        val cur = try { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) } catch (_: Exception) { -1 }
        prefs.edit().putBoolean("pending_boot", false).putInt("last_boot_count", cur).apply()
    }
}
