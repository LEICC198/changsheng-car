package com.changsheng.carlauncher

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var repo: AppRepository
    private lateinit var parking: ParkingManager
    private lateinit var carStage: Car3DStageView
    private var allApps: List<AppItem> = emptyList()
    private var dockPackages: MutableList<String> = mutableListOf()
    private lateinit var dockAdapter: DockAdapter
    private var lastGpsCoord: String = "离线 GPS 待定位"

    private val runningOrangeBorder = RunningOrangeBorderDrawable()
    private val wallpaperRes = intArrayOf(
        R.drawable.bg_wall_glacier,
        R.drawable.bg_wall_aurora,
        R.drawable.bg_wall_sunset,
        R.drawable.bg_wall_mint,
        R.drawable.bg_wall_silver,
        R.drawable.bg_wall_apricot
    )
    private val wallpaperNames = arrayOf("冰川海湾", "极光紫青", "日落珊瑚", "薄荷晴空", "云端银蓝", "暖阳米杏")

    private var countdownTimer: CountDownTimer? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            updateClock()
            updateParkingBadge()
            uiHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = AppRepository(this)
        parking = ParkingManager(this)
        carStage = findViewById(R.id.car3dStage)
        allApps = repo.loadAllLaunchableApps()
        dockPackages = repo.getDockPackages(allApps)

        setupDock()
        setupControls()
        applyWallpaper()
        updateClock()
        startGpsUpdates()

        // Power-off smart parking settlement on fresh car boot!
        val isFreshBoot = repo.shouldRunBootCountdown()
        if (isFreshBoot) {
            repo.consumeBootCountdown()
            if (parking.isParkingActive()) {
                parking.finishParking()?.let { showParkingSummaryBanner("启动车辆", it) }
            }
            startZlinkCountdown()
        }
        updateParkingBadge()
    }

    override fun onResume() {
        super.onResume()
        carStage.resumeAnimation()
        uiHandler.post(tickRunnable)
    }

    override fun onPause() {
        super.onPause()
        // Immediately stop 3D rendering when leaving launcher to save SC9853i GPU/RAM!
        carStage.pauseAnimation()
        runningOrangeBorder.stop()
        uiHandler.removeCallbacks(tickRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
        runningOrangeBorder.stop()
    }

    private fun setupDock() {
        val rvDock = findViewById<RecyclerView>(R.id.rvDock)
        rvDock.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        dockAdapter = DockAdapter(
            items = resolveDockItems(),
            onClick = { launchPackage(it.packageName) },
            onLongClick = { slotIndex, _ ->
                Toast.makeText(this, "选择应用替换此 Dock 位置", Toast.LENGTH_SHORT).show()
                showAppDrawer(replaceSlotIndex = slotIndex, bindNavMode = false)
            }
        )
        rvDock.adapter = dockAdapter
    }

    private fun resolveDockItems(): List<AppItem> =
        dockPackages.mapNotNull { pkg -> allApps.firstOrNull { it.packageName == pkg } }

    private fun setupControls() {
        findViewById<View>(R.id.btnClockCalendar).setOnClickListener { showMiniCalendar() }

        findViewById<View>(R.id.btnQuickPark).setOnClickListener {
            if (!parking.isParkingActive()) {
                parking.startParking("默认车位", lastGpsCoord)
                updateParkingBadge()
                Toast.makeText(this, "已开始停车计时并保存 GPS", Toast.LENGTH_SHORT).show()
            } else {
                showParkingDialog()
            }
        }
        findViewById<View>(R.id.btnParkHistory).setOnClickListener { showParkingDialog() }
        findViewById<View>(R.id.btnCloseSummary).setOnClickListener {
            findViewById<View>(R.id.parkSummaryBanner).visibility = View.GONE
        }

        findViewById<View>(R.id.btnWallpaper).setOnClickListener {
            repo.wallpaperIndex = (repo.wallpaperIndex + 1) % wallpaperRes.size
            applyWallpaper()
            Toast.makeText(this, "已切换壁纸：${wallpaperNames[repo.wallpaperIndex]}", Toast.LENGTH_SHORT).show()
        }

        val openSettings = View.OnClickListener { showTeslaSettings() }
        findViewById<View>(R.id.btnOpenSettings).setOnClickListener(openSettings)
        findViewById<View>(R.id.btnQuickSettings).setOnClickListener(openSettings)
        findViewById<View>(R.id.dockBrandBtn).setOnClickListener(openSettings)

        findViewById<View>(R.id.btnQuickOrigCar).setOnClickListener {
            repo.findAppByKeywords(allApps, listOf("原车功能", "原车信息"))?.let { launchPackage(it.packageName) }
                ?: Toast.makeText(this, "未找到原车功能应用", Toast.LENGTH_SHORT).show()
        }

        val switchAuto = findViewById<SwitchCompat>(R.id.switchAutoZlink)
        switchAuto.isChecked = repo.autoZlinkEnabled
        switchAuto.setOnCheckedChangeListener { _, checked ->
            repo.autoZlinkEnabled = checked
            if (!checked) cancelZlinkCountdown()
        }

        findViewById<View>(R.id.btnLaunchZlink).setOnClickListener { launchZlink() }
        findViewById<View>(R.id.btnLaunchZlinkNow).setOnClickListener { cancelZlinkCountdown(); launchZlink() }
        findViewById<View>(R.id.btnCancelCountdown).setOnClickListener { cancelZlinkCountdown() }

        findViewById<View>(R.id.btnLaunchAmap).setOnClickListener {
            val navPkg = repo.getBoundNavPackage(allApps)
            if (navPkg != null) launchPackage(navPkg)
            else Toast.makeText(this, "请先绑定高德地图应用", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btnBindAmap).setOnClickListener { showAppDrawer(null, true) }
        findViewById<View>(R.id.btnAppDrawer).setOnClickListener { showAppDrawer(null, false) }

        findViewById<View>(R.id.btnMediaPrev).setOnClickListener { dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        findViewById<View>(R.id.btnMediaPlayPause).setOnClickListener { dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        findViewById<View>(R.id.btnMediaNext).setOnClickListener { dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }

        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        findViewById<View>(R.id.btnVolDown).setOnClickListener {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        }
        findViewById<View>(R.id.btnVolUp).setOnClickListener {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        }
    }

    private fun updateParkingBadge() {
        val wrap = findViewById<View>(R.id.parkPillWrap)
        val tv = findViewById<TextView>(R.id.btnQuickPark)
        if (parking.isParkingActive()) {
            val sec = ((System.currentTimeMillis() - parking.getActiveStartTs()) / 1000L).coerceAtLeast(0L)
            tv.text = "● 停车 ${ParkingManager.formatHMS(sec)}"
            if (wrap.background !== runningOrangeBorder) {
                wrap.background = runningOrangeBorder
                runningOrangeBorder.start()
            }
        } else {
            tv.text = "● 停车计时"
            runningOrangeBorder.stop()
            wrap.setBackgroundResource(R.drawable.bg_park_idle)
        }
    }

    private fun showParkingSummaryBanner(reason: String, rec: ParkingRecord) {
        val banner = findViewById<View>(R.id.parkSummaryBanner)
        val tv = findViewById<TextView>(R.id.tvParkSummary)
        tv.text = "${reason}自动结算停车：${ParkingManager.formatDuration(rec.durationSec)} · ${rec.spot} (${rec.coord})"
        banner.visibility = View.VISIBLE
    }

    private fun showParkingDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_parking)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.62).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)

        val tvTimer = dialog.findViewById<TextView>(R.id.tvParkModalTimer)
        val etSpot = dialog.findViewById<EditText>(R.id.etParkSpot)
        val btnAction = dialog.findViewById<TextView>(R.id.btnParkAction)
        val btnClear = dialog.findViewById<TextView>(R.id.btnParkClear)
        val tvHistory = dialog.findViewById<TextView>(R.id.tvParkHistoryList)

        fun refreshDialog() {
            if (parking.isParkingActive()) {
                val sec = ((System.currentTimeMillis() - parking.getActiveStartTs()) / 1000L).coerceAtLeast(0L)
                tvTimer.text = ParkingManager.formatHMS(sec)
                etSpot.setText(parking.getActiveSpot())
                btnAction.text = "结束停车并结算"
            } else {
                tvTimer.text = "00:00:00"
                btnAction.text = "开始停车计时"
            }
            val list = parking.getHistory()
            tvHistory.text = if (list.isEmpty()) "暂无停车历史记录"
            else list.joinToString("\n\n") {
             "• ${it.spot}（${ParkingManager.formatDuration(it.durationSec)}）\n  ${it.startStr} → ${it.endStr} | GPS: ${it.coord}"
         }
        }

        btnAction.setOnClickListener {
            if (parking.isParkingActive()) {
                parking.finishParking()?.let { showParkingSummaryBanner("手动结束", it) }
            } else {
                parking.startParking(etSpot.text.toString().trim(), lastGpsCoord)
            }
            updateParkingBadge()
            refreshDialog()
        }
        btnClear.setOnClickListener {
            parking.clearHistory()
            refreshDialog()
        }
        refreshDialog()
        dialog.show()
    }

    private fun showMiniCalendar() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_calendar)
        val now = Date()
        dialog.findViewById<TextView>(R.id.tvCalHeader).text =
            "${SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA).format(now)} · ${LunarCalendar.formatOfflineLunar(now)}"
        dialog.show()
    }

    private fun showTeslaSettings() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_tesla_settings)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.65).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        dialog.findViewById<View>(R.id.btnSetCycleWall).setOnClickListener {
            repo.wallpaperIndex = (repo.wallpaperIndex + 1) % wallpaperRes.size
            applyWallpaper()
            Toast.makeText(this, "壁纸已切换：${wallpaperNames[repo.wallpaperIndex]}", Toast.LENGTH_SHORT).show()
        }
        dialog.findViewById<View>(R.id.btnSetBindNav).setOnClickListener { dialog.dismiss(); showAppDrawer(null, true) }
        dialog.findViewById<View>(R.id.btnSetOrigCar).setOnClickListener {
            dialog.dismiss()
            repo.findAppByKeywords(allApps, listOf("原车功能", "原车信息"))?.let { launchPackage(it.packageName) }
        }
        dialog.findViewById<View>(R.id.btnSetSwc).setOnClickListener {
            dialog.dismiss()
            repo.findAppByKeywords(allApps, listOf("方控", "steer", "swc"))?.let { launchPackage(it.packageName) }
        }
        dialog.findViewById<View>(R.id.btnSetAndroid).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
        dialog.findViewById<View>(R.id.btnSetLegacyCar).setOnClickListener {
            dialog.dismiss()
            repo.findAppByKeywords(allApps, listOf("车载设置", "nwd", "carset"))?.let { launchPackage(it.packageName) }
        }
        dialog.show()
    }

    private fun startZlinkCountdown() {
        val box = findViewById<View>(R.id.countdownBox)
        val tv = findViewById<TextView>(R.id.tvCountdownText)
        val pb = findViewById<ProgressBar>(R.id.pbCountdown)
        box.visibility = View.VISIBLE
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(3000, 50) {
            override fun onTick(ms: Long) {
                tv.text = "${((ms + 999) / 1000).coerceAtLeast(1)} 秒后自动进入 ZLINK..."
                pb.progress = ms.toInt()
            }
            override fun onFinish() { box.visibility = View.GONE; launchZlink() }
        }.start()
    }

    private fun cancelZlinkCountdown() {
        countdownTimer?.cancel(); countdownTimer = null
        findViewById<View>(R.id.countdownBox).visibility = View.GONE
    }

    private fun launchZlink() {
        val pkg = repo.findZlinkPackage(allApps)
        if (pkg != null) launchPackage(pkg)
        else Toast.makeText(this, "未检测到 ZLINK", Toast.LENGTH_SHORT).show()
    }

    private fun launchPackage(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
            ?: Toast.makeText(this, "无法启动：$pkg", Toast.LENGTH_SHORT).show()
    }

    private fun dispatchMediaKey(code: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun showAppDrawer(replaceSlotIndex: Int?, bindNavMode: Boolean) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_app_drawer)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.78).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        val title = dialog.findViewById<TextView>(R.id.tvDrawerTitle)
        val btnToggleHidden = dialog.findViewById<TextView>(R.id.btnToggleHidden)
        val rv = dialog.findViewById<RecyclerView>(R.id.rvDrawer)
        rv.layoutManager = GridLayoutManager(this, 5)
        var showHidden = false

        title.text = when {
            bindNavMode -> "选择你安装的第三方新版高德地图"
            replaceSlotIndex != null -> "选择应用替换 Dock 第 ${replaceSlotIndex + 1} 位"
            else -> "全部应用（长按可隐藏）"
        }

        lateinit var adapter: AppDrawerAdapter
        fun refresh() {
            val hidden = repo.getHiddenPackages()
            val list = if (showHidden || replaceSlotIndex != null || bindNavMode) allApps
            else allApps.filter { !hidden.contains(it.packageName) }
            adapter.update(list, hidden)
            btnToggleHidden.text = if (showHidden) "收起已隐藏" else "显示已隐藏(${hidden.size})"
        }

        adapter = AppDrawerAdapter(emptyList(), repo.getHiddenPackages(), { app ->
            when {
                bindNavMode -> {
                    repo.setBoundNavPackage(app.packageName)
                    findViewById<TextView>(R.id.tvBoundAmapPkg).text = "已绑定：${app.label}"
                    Toast.makeText(this, "主导航已绑定：${app.label}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                replaceSlotIndex != null && replaceSlotIndex in dockPackages.indices -> {
                    dockPackages[replaceSlotIndex] = app.packageName
                    repo.saveDockPackages(dockPackages)
                    dockAdapter.update(resolveDockItems())
                    dialog.dismiss()
                }
                else -> { dialog.dismiss(); launchPackage(app.packageName) }
            }
        }, { app ->
            if (replaceSlotIndex == null && !bindNavMode) {
                val nowHidden = repo.toggleHiddenPackage(app.packageName)
                Toast.makeText(this, if (nowHidden) "已隐藏：${app.label}" else "已恢复：${app.label}", Toast.LENGTH_SHORT).show()
                refresh()
            }
        })
        rv.adapter = adapter
        btnToggleHidden.setOnClickListener { showHidden = !showHidden; refresh() }
        refresh()
        dialog.show()
    }

    private fun applyWallpaper() {
        val idx = repo.wallpaperIndex.coerceIn(0, wallpaperRes.lastIndex)
        val light = idx >= 3
        findViewById<View>(R.id.rootContainer).setBackgroundResource(wallpaperRes[idx])
        val bgLeft = ContextCompat.getColor(this, if (light) R.color.light_bg_left else R.color.dark_bg_left)
        val textPrimary = ContextCompat.getColor(this, if (light) R.color.light_text_primary else R.color.dark_text_primary)
        val cardBg = if (light) R.drawable.bg_card_light else R.drawable.bg_card_dark
        findViewById<View>(R.id.topBar).setBackgroundColor(bgLeft)
        listOf(R.id.amapHeroCard, R.id.zlinkCard, R.id.quickCard, R.id.mediaCard).forEach {
            findViewById<View>(it).setBackgroundResource(cardBg)
        }
        listOf(R.id.tvClock, R.id.tvDate, R.id.tvAmapTitle, R.id.tvZlinkTitle, R.id.tvQuickTitle, R.id.tvMediaTitle).forEach {
            findViewById<TextView>(it).setTextColor(textPrimary)
        }
    }

    private fun updateClock() {
        val now = Date()
        findViewById<TextView>(R.id.tvClock).text = SimpleDateFormat("HH:mm", Locale.CHINA).format(now)
        findViewById<TextView>(R.id.tvDate).text = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA).format(now)
        findViewById<TextView>(R.id.tvLunar).text = LunarCalendar.formatOfflineLunar(now)
    }

    private fun startGpsUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
            return
        }
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this)
        } catch (_: Exception) {}
    }

    override fun onLocationChanged(location: Location) {
        val kmh = (location.speed * 3.6f).coerceAtLeast(0f)
        lastGpsCoord = String.format(Locale.US, "%.4f°N, %.4f°E", location.latitude, location.longitude)
        findViewById<TextView>(R.id.tvSpeed).text = kmh.toInt().toString()
        carStage.handbrakeOn = kmh < 1f
        carStage.speedKmh = kmh

        // Auto-settle active parking once the car starts moving (> 5 km/h)
        if (kmh >= 5f && parking.isParkingActive()) {
            parking.finishParking()?.let {
                showParkingSummaryBanner("车辆起步", it)
                updateParkingBadge()
            }
        }
    }
}
