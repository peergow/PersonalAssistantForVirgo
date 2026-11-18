package com.disiplinai.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.disiplinai.R
import com.disiplinai.data.Store
import com.disiplinai.logic.PolicyEngine
import com.disiplinai.logic.Util
import com.disiplinai.ui.BlockActivity

/**
 * Penegak disiplin di sisi sistem. Mendeteksi aplikasi di layar, menanyakan PolicyEngine,
 * lalu menendang pengguna ke Home dan menampilkan layar blokir.
 */
class BlockerService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var currentPkg: String? = null
    private var lastBlock = 0L
    private var lastGuard = 0L
    private var tickCount = 0
    private var ignored: Set<String> = emptySet()

    private val ticker = object : Runnable {
        override fun run() {
            try {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                if (pm.isInteractive) currentPkg?.let { enforce(it) }
                if (++tickCount % 12 == 0) refreshIgnored()
            } catch (e: Exception) { /* jangan sampai layanan mati */ }
            handler.postDelayed(this, 5000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        refreshIgnored()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun refreshIgnored() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        ignored = (imm.enabledInputMethodList.map { it.packageName } +
            listOf("com.android.systemui", packageName)).toSet()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return
        val pkg = ev.packageName?.toString() ?: return
        when (ev.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg in ignored) return
                currentPkg = pkg
                guardSettings(pkg)
                enforce(pkg)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (pkg in GUARDED) guardSettings(pkg)
            }
        }
    }

    private fun enforce(pkg: String) {
        val v = PolicyEngine.check(this, pkg)
        if (!v.blocked) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlock < 2000) return
        lastBlock = now
        Store.get(this).recordBlock()
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({
            try {
                startActivity(
                    Intent(this, BlockActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        .putExtra("pkg", pkg)
                        .putExtra("reason", v.reason)
                        .putExtra("freeAt", v.freeAt)
                )
            } catch (e: Exception) { /* minimal: pengguna sudah ditendang ke Home */ }
        }, 250)
    }

    /**
     * Mode strict: saat sesi fokus / jam tidur, halaman Pengaturan atau pemasang paket yang memuat
     * nama aplikasi ini (aksesibilitas, info aplikasi, uninstall) langsung ditutup.
     */
    private fun guardSettings(pkg: String) {
        if (pkg !in GUARDED) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastGuard < 600) return
        lastGuard = now
        val store = Store.get(this)
        if (!store.config.value.strictMode) return
        val active = store.focusEnd.value > System.currentTimeMillis() ||
            PolicyEngine.inSleep(Util.nowMin(), store.profile.value)
        if (!active) return
        val root = rootInActiveWindow ?: return
        val found = !root.findAccessibilityNodeInfosByText(getString(R.string.app_name)).isNullOrEmpty()
        if (found) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            Toast.makeText(this, "Mode strict: pengaturan Disiplin AI terkunci sekarang.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }

    companion object {
        private val GUARDED = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller"
        )
    }
}
