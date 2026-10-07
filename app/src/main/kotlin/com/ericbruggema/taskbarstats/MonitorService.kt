package com.ericbruggema.taskbarstats

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/** Live-monitor: ververst de widget en een doorlopende melding elke 2 s zolang de gebruiker dit aanzet. */
class MonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            StatsWidget.update(this@MonitorService)
            StatusBarOverlay.update(this@MonitorService, Sampler.snapshot)
            Alerts.check(this@MonitorService, Sampler.snapshot)
            updateNotifications()
            UpdateChecker.checkIfDue(this@MonitorService) { UpdateChecker.notify(this@MonitorService, it) }
            handler.postDelayed(this, 2000)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // actie "Stop" in de melding
        if (intent?.action == ACTION_STOP) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT)
            // normale prioriteit (anders verbergt Android de melding op het lockscreen), maar zonder geluid of trilling
            .also { it.lockscreenVisibility = Notification.VISIBILITY_PUBLIC; it.setSound(null, null); it.enableVibration(false) })
        nm.deleteNotificationChannel("speed")
        // gewoon belang (zonder geluid): sommige telefoons tonen het icoon van stille meldingen niet in de statusbalk
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ICONS, getString(R.string.notif_channel_icons), NotificationManager.IMPORTANCE_DEFAULT)
            .also { it.setSound(null, null); it.enableVibration(false) })
        if (android.os.Build.VERSION.SDK_INT >= 29)
            startForeground(ID, build(mainItem()), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID, build(mainItem()))
        ShizukuCpu.init(this)
        if (!running) { running = true; Sampler.acquire(this); handler.post(tick) }
        return START_STICKY
    }

    /** Hoofdmelding, plus (optie) een melding per gekozen onderdeel met de waarde als statusbalkicoon. */
    private fun updateNotifications() {
        val nm = getSystemService(NotificationManager::class.java)
        val s = Sampler.snapshot
        val offline = s.netKind == NET_NONE && StatusBarOverlay.prefs(this).getBoolean("notif_online_only", false)
        val chosen = StatusItems.selected(this)
        val shown = if (offline || !StatusItems.iconsOn(this) || StatusItems.rotate(this)) emptyList() else chosen
        // het eerste onderdeel zit altijd op het icoon van de hoofdmelding (die melding moet er toch zijn), ook bij alleen de tekststrook
        nm.notify(ID, build(mainItem()))
        for ((i, id) in StatusItems.ALL.withIndex()) {
            if (id in shown.drop(1)) nm.notify(ID_ITEM + i, itemNotification(id, s)) else nm.cancel(ID_ITEM + i)
        }
    }

    /** Het onderdeel op het hoofdicoon: het eerste gekozen onderdeel, of de downloadsnelheid; nooit het vaste app-icoon. */
    private fun mainItem(): String {
        val sel = StatusItems.selected(this)
        // optioneel: één icoon dat langs alle gekozen onderdelen wisselt (om de ~4 s), voor toestellen die maar één icoon tonen
        if (StatusItems.rotate(this) && sel.size > 1) return sel[((System.currentTimeMillis() / 4000) % sel.size).toInt()]
        return sel.firstOrNull() ?: "down"
    }

    private fun itemNotification(id: String, s: Snapshot): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ICONS)
            .setSmallIcon(StatusItems.icon(StatusItems.face(id, s)))
            .setContentTitle(getString(StatusItems.nameRes(id))).setContentText(StatusItems.detail(id, s))
            .setGroup("item_$id").setVisibility(Notification.VISIBILITY_SECRET)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open).build()
    }

    private fun build(first: String? = null): Notification {
        val s = Sampler.snapshot
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, MonitorService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val icon = first?.let { StatusItems.icon(StatusItems.face(it, s)) } ?: Icon.createWithResource(this, R.drawable.ic_stat)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(icon)
            .setContentTitle("MEM ${Fmt.percent(s.memPercent)}   ↓ ${Fmt.rate(s.rxBps)}   ↑ ${Fmt.rate(s.txBps)}")
            .setContentText("Disk ${Fmt.percent(s.storagePercent)}   Ping ${s.pingMs?.let { "$it ms" } ?: "—"}")
            .setVisibility(Notification.VISIBILITY_PUBLIC) // ook op het lockscreen
            .addAction(Notification.Action.Builder(null, getString(R.string.notif_stop), stop).build())
            .setGroup("live").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        StatusBarOverlay.remove(this)
        getSystemService(NotificationManager::class.java).apply { for (i in StatusItems.ALL.indices) cancel(ID_ITEM + i) }
        if (running) { running = false; Sampler.release() }
        super.onDestroy()
    }

    companion object {
        private const val ID = 1
        private const val CHANNEL = "live"
        private const val CHANNEL_ICONS = "icons"
        private const val ID_ITEM = 30
        private const val ACTION_STOP = "com.ericbruggema.taskbarstats.STOP"
        @Volatile var running = false
            private set

        fun start(ctx: Context) { ctx.startForegroundService(Intent(ctx, MonitorService::class.java)) }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, MonitorService::class.java)) }
    }
}
