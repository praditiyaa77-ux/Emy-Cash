package id.emypedia.emycash

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

class MainActivity : Activity() {
    private val BG = Color.WHITE
    private val SURFACE = Color.parseColor("#F5F7FA")
    private val INK = Color.parseColor("#0E1B2C")
    private val MUTED = Color.parseColor("#667085")
    private val FAINT = Color.parseColor("#98A2B3")
    private val LINE = Color.parseColor("#E6E9EE")
    private val OK = Color.parseColor("#0A7D55")
    private val AMBER = Color.parseColor("#B26A00")
    private val RED = Color.parseColor("#B42318")

    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var listBox: LinearLayout
    private lateinit var todayText: TextView
    private lateinit var todaySub: TextView
    private lateinit var historyPage: ScrollView
    private lateinit var settingsPage: ScrollView
    private lateinit var premiumPage: ScrollView
    private var devPage: ScrollView? = null
    private val pages = ArrayList<View>()
    private val navItems = ArrayList<TextView>()
    private lateinit var premBox: LinearLayout
    private lateinit var navIndicator: View
    private lateinit var navBar: FrameLayout
    private lateinit var testResult: TextView
    private val fields = LinkedHashMap<String, EditText>()
    private val handler = Handler(Looper.getMainLooper())
    private val rendered = HashSet<Long>()
    private var firstRender = true
    private var lastSig = ""
    private var tabIdx = 0
    private lateinit var acctBox: LinearLayout
    private lateinit var acctLine: TextView
    private lateinit var updateLine: TextView
    private var lastRefresh = 0L
    private var busy = false
    private val ticker = object : Runnable {
        override fun run() {
            render()
            pollPay()
            val t = System.currentTimeMillis()
            if (t - lastRefresh > 60000) { lastRefresh = t; refreshStatus(false) }
            handler.postDelayed(this, 3000)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, r: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun tv(t: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        fontFeatureSettings = "tnum"
        includeFontPadding = false
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun lp(top: Int = 0, w: Int = ViewGroup.LayoutParams.MATCH_PARENT) =
        LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun divider() = View(this).apply { setBackgroundColor(LINE) }
    private fun divLp(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    // Efek tekan halus untuk tombol/baris
    private fun View.pressable() {
        setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.98f).scaleY(0.98f).alpha(0.85f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(160).start()
            }
            false
        }
    }

    private fun fadeIn(v: View, delay: Long = 0) {
        v.alpha = 0f
        v.translationY = dp(8).toFloat()
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(240)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun wallet(label: String, color: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(View(this@MainActivity).apply { background = rounded(color, 2) }, LinearLayout.LayoutParams(dp(8), dp(8)))
        addView(tv(label, 13f, INK, true).apply { setPadding(dp(8), 0, 0, 0) })
    }

    private fun status(label: String, color: Int, pulse: Boolean = false) = tv("\u25CF  $label", 13f, color, true).apply {
        if (pulse) {
            val anim = ObjectAnimator.ofFloat(this, "alpha", 1f, 0.35f).apply {
                duration = 800; repeatCount = ObjectAnimator.INFINITE; repeatMode = ObjectAnimator.REVERSE; start()
            }
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) { anim.cancel() }
            })
        }
    }

    private fun button(label: String, filled: Boolean, textColor: Int = if (filled) Color.WHITE else INK, onClick: () -> Unit) =
        Button(this).apply {
            text = label
            transformationMethod = null
            stateListAnimator = null
            minHeight = dp(48)
            minimumHeight = dp(48)
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = if (filled) rounded(INK, 6) else rounded(BG, 6, LINE)
            pressable()
            setOnClickListener { onClick() }
        }

    private fun walletOf(pkg: String, title: String, text: String): Pair<String, Int> {
        if (pkg.startsWith("com.whatsapp")) return Pair("WhatsApp", Color.parseColor("#25D366"))
        return when (detectMethod(pkg, title, text)) {
            "dana" -> Pair("DANA", Color.parseColor("#118EEA"))
            "shopeepay" -> Pair("ShopeePay", Color.parseColor("#EE4D2D"))
            "gopay" -> Pair("GoPay", Color.parseColor("#00AA13"))
            else -> Pair(pkg.substringAfterLast('.'), FAINT)
        }
    }

    private fun listenerOn(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(packageName)
    }

    private fun openAccess() = startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun copy(t: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("invoice", t))
        toast("Disalin: $t")
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val p = Cfg.prefs(this)
        tabIdx = b?.getInt("tab", 0) ?: 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            fitsSystemWindows = true
        }

        // ---- header ----
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
        }
        header.addView(tv("Emy Cash", 20f, INK, true))
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
            setOnClickListener {
                if (!listenerOn()) openAccess() else { Cfg.rebind(this@MainActivity); toast("Menyambungkan ulang…") }
            }
        }
        statusDot = View(this).apply { background = rounded(Color.GRAY, 10) }
        statusRow.addView(statusDot, LinearLayout.LayoutParams(dp(8), dp(8)))
        statusText = tv("", 13f, MUTED).apply { setPadding(dp(8), 0, 0, 0) }
        statusRow.addView(statusText)
        header.addView(statusRow, lp(4, ViewGroup.LayoutParams.WRAP_CONTENT))
        acctLine = tv("", 12f, MUTED)
        header.addView(acctLine, lp(2))
        updateLine = tv("", 12f, AMBER, true).apply { visibility = View.GONE }
        header.addView(updateLine, lp(6))
        root.addView(header, lp())
        root.addView(divider(), divLp())

        // ---- halaman riwayat ----
        todayText = tv("Rp 0", 28f, INK, true)
        todaySub = tv("Belum ada pembayaran lunas hari ini", 13f, MUTED)
        val summary = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(16))
            addView(tv("Lunas hari ini", 13f, MUTED))
            addView(todayText, lp(6))
            addView(todaySub, lp(4))
        }
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val histActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(8), dp(20), dp(24))
            addView(button("Hapus riwayat", false, RED) {
                p.edit().remove("h").apply(); rendered.clear(); firstRender = true; lastSig = ""; render()
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val histRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(summary, lp())
            addView(divider(), divLp())
            addView(listBox, lp())
            addView(histActions, lp())
        }
        historyPage = ScrollView(this).apply { addView(histRoot); isVerticalScrollBarEnabled = false }

        // ---- halaman pengaturan ----
        fun field(box: LinearLayout, label: String, key: String, pw: Boolean = false, ph: String = "") {
            box.addView(tv(label, 12f, MUTED), lp(14))
            val e = EditText(this).apply {
                setText(p.getString(key, ""))
                hint = ph
                setHintTextColor(FAINT)
                setTextColor(INK)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setSingleLine()
                inputType = if (pw) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = rounded(BG, 6, LINE)
                setOnFocusChangeListener { v, f -> (v.background as GradientDrawable).setStroke(dp(1), if (f) INK else LINE) }
            }
            fields[key] = e
            box.addView(e, lp(6))
        }
        fun section(title: String, desc: String? = null): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            addView(tv(title, 15f, INK, true))
            if (desc != null) addView(tv(desc, 13f, MUTED).apply { setLineSpacing(0f, 1.15f) }, lp(4))
        }

        val setBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val s0 = section("Akun Emy Cash")
        acctBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        s0.addView(acctBox, lp(4))
        setBox.addView(s0, lp())
        setBox.addView(divider(), divLp())

        val s1 = section("Izin notifikasi", "Emy Cash perlu Notification Access untuk membaca notifikasi pembayaran. Kalau status di atas bukan hijau, sambungkan ulang.")
        s1.addView(button("Buka Notification Access", true) { openAccess() }, lp(12))
        s1.addView(button("Sambungkan ulang listener", false) { Cfg.rebind(this); toast("Menyambungkan ulang…"); lastSig = ""; handler.postDelayed({ render() }, 800) }, lp(8))
        s1.addView(button("Izinkan berjalan di latar belakang", false) {
            try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (e: Exception) { toast("Tidak tersedia di perangkat ini") }
        }, lp(8))
        setBox.addView(s1, lp())
        setBox.addView(divider(), divLp())

        val s2 = section("Server (mode manual)", "Terisi otomatis saat Anda masuk dengan akun. Ubah hanya untuk server sendiri atau mode pemilik dengan Secret. Cukup isi alamat server, /notif ditambahkan otomatis.")
        field(s2, "Alamat server", "wh", ph = Cfg.DEFAULT_SERVER)
        field(s2, "Kunci (Secret)", "sec", pw = true)
        testResult = tv("", 13f, MUTED)
        s2.addView(button("Tes koneksi", false) { testServer() }, lp(14))
        s2.addView(testResult, lp(8))
        s2.addView(button("Buka halaman order", false) {
            val u = Uri.parse(fields["wh"]!!.text.toString().trim())
            if (u.scheme != null && u.authority != null) {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.scheme + "://" + u.authority + "/"))) } catch (e: Exception) {}
            } else toast("Isi alamat server dulu")
        }, lp(8))
        setBox.addView(s2, lp())
        setBox.addView(divider(), divLp())

        val s3 = section("Deteksi pembayaran")
        val wideRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val wideTxt = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tv("Pantau semua notifikasi pembayaran", 14f, INK, true))
            addView(tv("Aplikasi yang belum ada di daftar tetap dibaca kalau notifikasinya jelas pembayaran masuk. Chat tidak pernah dibaca sebagai pembayaran.", 12f, MUTED).apply { setLineSpacing(0f, 1.15f) }, lp(2))
        }
        wideRow.addView(wideTxt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        wideRow.addView(Switch(this).apply {
            isChecked = Cfg.wide(this@MainActivity)
            setOnCheckedChangeListener { _, on -> p.edit().putBoolean("wide", on).apply() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(12) })
        s3.addView(wideRow, lp(12))
        s3.addView(tv("Package tambahan (pisah koma, * di akhir = semua berawalan itu). Package yang terdeteksi otomatis ikut tercatat di sini.", 12f, MUTED).apply { setLineSpacing(0f, 1.15f) }, lp(16))
        field(s3, "Package", "pkgsx")
        setBox.addView(s3, lp())
        setBox.addView(divider(), divLp())

        val s4 = section("Telegram (opsional)")
        field(s4, "Bot token", "tok", pw = true)
        field(s4, "Chat ID", "chat")
        val waRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        waRow.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tv("Teruskan notifikasi WhatsApp", 14f, INK, true))
            addView(tv("Nonaktif secara bawaan demi privasi. Pesan chat tidak pernah dihitung sebagai pembayaran.", 12f, MUTED).apply { setLineSpacing(0f, 1.15f) }, lp(2))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        waRow.addView(Switch(this).apply {
            isChecked = p.getBoolean("wa", false)
            setOnCheckedChangeListener { _, on -> p.edit().putBoolean("wa", on).apply() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(12) })
        s4.addView(waRow, lp(16))
        setBox.addView(s4, lp())
        setBox.addView(divider(), divLp())

        val s5 = section("Tentang")
        s5.addView(tv("Emy Cash ${versionInfo().first}", 13f, MUTED), lp(6))
        s5.addView(button("Cek pembaruan", false) { toast("Memeriksa…"); refreshStatus(true) }, lp(12))
        s5.addView(button("Buka situs Emy Cash", false) { openUrl(serverBase() + "/") }, lp(8))
        setBox.addView(s5, lp())

        val saveWrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), dp(28)) }
        saveWrap.addView(button("Simpan pengaturan", true) {
            val e = p.edit()
            for ((k, v) in fields) {
                var t = v.text.toString().trim()
                if (k == "wh" && t.isNotEmpty()) { t = Cfg.hookUrl(t); v.setText(t) }
                e.putString(k, t)
            }
            e.apply()
            toast("Tersimpan")
        }, lp())
        setBox.addView(saveWrap, lp())
        settingsPage = ScrollView(this).apply { addView(setBox); isVerticalScrollBarEnabled = false }

        // ---- halaman premium + (edisi dev) halaman developer ----
        premBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        premiumPage = ScrollView(this).apply { addView(premBox); isVerticalScrollBarEnabled = false }
        if (BuildConfig.DEV) devPage = buildDevPage()

        pages.add(historyPage); pages.add(premiumPage); pages.add(settingsPage)
        val names = arrayListOf("Riwayat", "Premium", "Pengaturan")
        devPage?.let { pages.add(it); names.add("Dev") }

        // ---- konten + navigasi bawah ----
        val content = FrameLayout(this)
        for (pg in pages) content.addView(pg, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(divider(), divLp())

        navBar = FrameLayout(this)
        val navRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((idx, label) in names.withIndex()) {
            val item = tv(label, 14f, MUTED, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, dp(16))
                setOnClickListener { selectTab(idx, true) }
            }
            navItems.add(item)
            navRow.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        navIndicator = View(this).apply { setBackgroundColor(INK) }
        navBar.addView(navRow, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        navBar.addView(navIndicator, FrameLayout.LayoutParams(0, dp(2)))
        root.addView(navBar, lp())

        setContentView(root)
        renderAccount()
        renderAcctLine()
        navBar.post {
            navIndicator.layoutParams = (navIndicator.layoutParams as FrameLayout.LayoutParams).apply { width = navBar.width / pages.size }
            selectTab(tabIdx.coerceIn(0, pages.size - 1), false)
        }
    }

    override fun onSaveInstanceState(o: Bundle) {
        super.onSaveInstanceState(o)
        o.putInt("tab", tabIdx)
    }

    private fun selectTab(i: Int, animate: Boolean) {
        tabIdx = i
        for ((k, pg) in pages.withIndex()) {
            pg.animate().cancel()
            if (k == i) {
                pg.visibility = View.VISIBLE
                if (animate) {
                    pg.alpha = 0f
                    pg.translationY = dp(10).toFloat()
                    pg.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
                } else { pg.alpha = 1f; pg.translationY = 0f }
            } else {
                pg.visibility = View.GONE; pg.alpha = 1f; pg.translationY = 0f
            }
        }
        val target = (i * navBar.width / pages.size).toFloat()
        if (animate) navIndicator.animate().translationX(target).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        else navIndicator.translationX = target
        for ((k, nv) in navItems.withIndex()) nv.setTextColor(if (k == i) INK else MUTED)
        val cur = pages[i]
        if (cur === premiumPage) { if (payOrder == null) loadPlans() else renderPremium() }
        if (cur === devPage) renderCoverage()
    }

    private fun testServer() {
        val base = Api.base(fields["wh"]!!.text.toString())
        val sec = fields["sec"]!!.text.toString().trim()
        if (base == null) {
            testResult.setTextColor(RED); testResult.text = "Isi alamat server dulu, mis. ${Cfg.DEFAULT_SERVER}"; return
        }
        testResult.setTextColor(MUTED); testResult.text = "Menguji…"
        Thread {
            val r = Api.call("GET", "$base/app/status", sec)
            val ok = r.ok
            val msg = when {
                r.ok -> "Server tersambung" + (r.json().optString("email").takeIf { it.isNotEmpty() }?.let { ", akun $it" } ?: "")
                r.code == 401 -> "Server terjangkau, tetapi kunci salah atau sudah dicabut"
                r.code == 404 -> "Server terjangkau, tetapi belum mendukung Emy Cash (HTTP 404). Perbarui gateway ke versi terbaru."
                else -> Api.message(r)
            }
            runOnUiThread {
                testResult.setTextColor(if (ok) OK else RED)
                testResult.text = msg
            }
        }.start()
    }

    // ---------- akun Emy Cash ----------
    private fun versionInfo(): Pair<String, Int> = try {
        val pi = packageManager.getPackageInfo(packageName, 0)
        Pair(pi.versionName ?: "", pi.versionCode)
    } catch (e: Exception) { Pair("", 0) }

    private fun serverBase(): String =
        Api.base(fields["wh"]?.text?.toString() ?: Cfg.prefs(this).getString("wh", "") ?: "") ?: Cfg.DEFAULT_SERVER

    private fun openUrl(u: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } catch (e: Exception) { toast("Tidak bisa membuka tautan") }
    }

    private fun acct(): JSONObject =
        try { JSONObject(Cfg.prefs(this).getString("acct", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }

    private fun fmtDate(t: Long): String = SimpleDateFormat("d MMM yyyy", Locale("id", "ID")).format(Date(t))

    private fun accountSummary(a: JSONObject): String {
        if (a.length() == 0) return "Memuat status…"
        val sb = StringBuilder()
        if (a.optBoolean("owner")) sb.append("Akun pemilik")
        else if (a.optBoolean("active")) sb.append("Paket ${a.optString("plan")} aktif sampai ${fmtDate(a.optLong("exp"))}")
        else sb.append("Langganan tidak aktif. Beli paket di tab Premium supaya pembayaran dicocokkan.")
        val m = a.optString("merchants")
        sb.append("\n").append(if (m.isBlank()) "Belum ada merchant QRIS. Daftarkan di dashboard." else "Merchant: $m")
        return sb.toString()
    }

    private fun editBox(hint: String, secret: Boolean, email: Boolean = false) = EditText(this).apply {
        this.hint = hint
        setHintTextColor(FAINT)
        setTextColor(INK)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setSingleLine()
        inputType = when {
            secret -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            email -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            else -> InputType.TYPE_CLASS_TEXT
        }
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = rounded(BG, 6, LINE)
        setOnFocusChangeListener { v, f -> (v.background as GradientDrawable).setStroke(dp(1), if (f) INK else LINE) }
    }

    private fun renderAccount() {
        val p = Cfg.prefs(this)
        acctBox.removeAllViews()
        val email = p.getString("email", "") ?: ""
        if (email.isEmpty()) {
            acctBox.addView(tv("Masuk dengan akun Emy Cash. Alamat server dan kunci terisi otomatis, tanpa salin-tempel.", 13f, MUTED).apply { setLineSpacing(0f, 1.15f) }, lp())
            val em = editBox("Email", false, true)
            val pw = editBox("Password", true)
            val msg = tv("", 13f, RED)
            acctBox.addView(em, lp(12))
            acctBox.addView(pw, lp(8))
            acctBox.addView(button("Masuk", true) { login(em.text.toString().trim(), pw.text.toString(), msg) }, lp(12))
            acctBox.addView(msg, lp(8))
            acctBox.addView(button("Daftar akun baru", false) { openUrl(serverBase() + "/dashboard#daftar") }, lp(8))
        } else {
            val a = acct()
            acctBox.addView(tv(email, 15f, INK, true), lp())
            acctBox.addView(tv(accountSummary(a), 13f, if (a.length() == 0 || a.optBoolean("active")) MUTED else AMBER).apply { setLineSpacing(0f, 1.15f) }, lp(4))
            acctBox.addView(button("Perbarui status", false) { refreshStatus(true) }, lp(12))
            acctBox.addView(button("Buka dashboard", false) { openUrl(serverBase() + "/dashboard") }, lp(8))
            acctBox.addView(button("Keluar", false, RED) { logout() }, lp(8))
        }
    }

    private fun renderAcctLine() {
        val p = Cfg.prefs(this)
        val email = p.getString("email", "") ?: ""
        val err = p.getString("acct_err", "") ?: ""
        val a = acct()
        val line: Pair<String, Int> = when {
            err.isNotEmpty() -> Pair(err, RED)
            email.isNotEmpty() -> Pair(
                email + " · " + when {
                    a.length() == 0 -> "memuat…"
                    a.optBoolean("owner") -> "pemilik"
                    a.optBoolean("active") -> "paket ${a.optString("plan")} aktif"
                    else -> "langganan tidak aktif"
                },
                if (a.length() == 0 || a.optBoolean("active")) MUTED else AMBER
            )
            (p.getString("sec", "") ?: "").isNotBlank() -> Pair("Mode manual", MUTED)
            else -> Pair("Belum masuk. Buka Pengaturan untuk masuk akun", AMBER)
        }
        acctLine.text = line.first
        acctLine.setTextColor(line.second)
        val uv = a.optString("upd_ver")
        if (uv.isNotEmpty()) {
            updateLine.text = "Versi $uv tersedia. Ketuk untuk unduh"
            updateLine.visibility = View.VISIBLE
            updateLine.setOnClickListener { openUrl(a.optString("upd_url")) }
        } else updateLine.visibility = View.GONE
    }

    private fun login(email: String, pw: String, msg: TextView) {
        if (busy) return
        if (email.isEmpty() || pw.isEmpty()) { msg.setTextColor(RED); msg.text = "Isi email dan password"; return }
        busy = true
        msg.setTextColor(MUTED)
        msg.text = "Masuk…"
        val base = serverBase()
        Thread {
            val body = JSONObject().put("email", email).put("password", pw).put("device_name", Build.MODEL ?: "Android")
            val r = Api.call("POST", "$base/app/login", null, body)
            runOnUiThread {
                busy = false
                val key = r.json().optString("device_key")
                if (r.ok && key.isNotEmpty()) {
                    Cfg.prefs(this).edit().putString("email", email).putString("wh", "$base/notif").putString("sec", key)
                        .putString("acct", "{}").remove("acct_err").apply()
                    fields["wh"]?.setText("$base/notif")
                    fields["sec"]?.setText(key)
                    lastSig = ""
                    renderAccount()
                    renderAcctLine()
                    toast("Berhasil masuk")
                    refreshStatus(false)
                } else {
                    msg.setTextColor(RED)
                    msg.text = if (r.code == 404) "Server belum mendukung Emy Cash (HTTP 404). Perbarui gateway ke versi terbaru." else Api.message(r)
                }
            }
        }.start()
    }

    private fun logout() {
        val p = Cfg.prefs(this)
        val base = serverBase()
        val key = p.getString("sec", "") ?: ""
        Thread { if (key.isNotBlank()) Api.call("POST", "$base/app/logout", key) }.start()
        p.edit().remove("email").remove("acct").remove("sec").remove("acct_err").apply()
        fields["sec"]?.setText("")
        lastSig = ""
        renderAccount()
        renderAcctLine()
        toast("Sudah keluar")
    }

    // Ambil status langganan, merchant, dan versi terbaru dari server; sekalian kirim heartbeat
    private fun refreshStatus(manual: Boolean) {
        val p = Cfg.prefs(this)
        val key = p.getString("sec", "") ?: ""
        val base = Api.base(p.getString("wh", "") ?: "")
        if (key.isBlank() || base == null) {
            if (manual) toast("Masuk dulu, atau isi server dan kunci")
            return
        }
        val cur = versionInfo().second
        Thread {
            val r = Api.call("GET", "$base/app/status", key)
            if (r.ok) {
                val j = r.json()
                val names = ArrayList<String>()
                val ms = j.optJSONArray("merchants")
                if (ms != null) for (i in 0 until ms.length()) names.add(ms.getJSONObject(i).optString("name"))
                val a = JSONObject().put("active", j.optBoolean("active")).put("plan", j.optString("plan_name"))
                    .put("exp", j.optLong("plan_expires_at")).put("owner", j.optBoolean("owner")).put("merchants", names.joinToString(", "))
                val app = j.optJSONObject("app")
                if (app != null && app.optBoolean("available") && app.optInt("version_code") > cur) {
                    val du = app.optString("download_url")
                    a.put("upd_ver", app.optString("version")).put("upd_url", if (du.startsWith("/")) base + du else du)
                }
                p.edit().putString("acct", a.toString()).remove("acct_err").apply()
                Cfg.heartbeat(applicationContext)
            } else if (r.code == 401) {
                p.edit().putString("acct_err", "Sesi berakhir. Masuk lagi.").apply()
            } else if (r.code == 404) {
                p.edit().putString("acct_err", "Server belum diperbarui (HTTP 404)").apply()
            }
            runOnUiThread {
                renderAccount()
                renderAcctLine()
                if (manual) toast(if (r.ok) "Status diperbarui" else Api.message(r))
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        lastSig = ""
        // izin aktif tetapi listener tidak tersambung (sering terjadi setelah update/hemat baterai): sambungkan ulang
        if (listenerOn() && !NotifService.connected) Cfg.rebind(this)
        ticker.run()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun render() {
        val on = listenerOn()
        val conn = NotifService.connected
        val raw = Cfg.prefs(this).getString("h", "[]") ?: "[]"
        val sig = raw + on + conn
        if (sig == lastSig) return
        lastSig = sig

        val (label, color) = when {
            !on -> Pair("Belum aktif. Ketuk untuk membuka izin", RED)
            !conn -> Pair("Izin aktif, belum tersambung. Ketuk untuk sambungkan ulang", AMBER)
            else -> Pair("Listener aktif", OK)
        }
        statusText.text = label
        statusText.setTextColor(if (on && conn) MUTED else color)
        (statusDot.background as GradientDrawable).setColor(color)

        val arr = JSONArray(raw)
        // ringkasan lunas hari ini
        var sum = 0L
        var cnt = 0
        val t0 = startOfToday()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("st") == "paid" && o.optLong("i") >= t0) { sum += o.optLong("am"); cnt++ }
        }
        todayText.text = rupiah(sum)
        todayText.setTextColor(if (sum > 0) OK else INK)
        todaySub.text = if (cnt == 0) "Belum ada pembayaran lunas hari ini" else "$cnt pembayaran lunas"

        listBox.removeAllViews()
        if (arr.length() == 0) {
            listBox.addView(tv(if ((Cfg.prefs(this).getString("sec", "") ?: "").isBlank()) "Belum masuk.\nBuka Pengaturan, masuk dengan akun Emy Cash, lalu aktifkan izin notifikasi." else "Belum ada notifikasi.\nPembayaran masuk akan muncul di sini.", 14f, MUTED).apply {
                setPadding(dp(20), dp(32), dp(20), dp(32))
                setLineSpacing(0f, 1.2f)
            }, lp())
            return
        }
        var animIdx = 0L
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optLong("i")
            val pkg = o.optString("p", o.optString("a", "?"))
            val title = o.optString("ti")
            val text = o.optString("tx")
            val st = o.optString("st")
            val inv = o.optString("inv")
            val info = o.optString("info")
            val (wl, wc) = walletOf(pkg, title, text)
            val amount: Long? =
                if (o.has("am")) o.optLong("am")
                else if (pkg.startsWith("com.whatsapp")) null
                else parseAmount(title, text)
            val chip: Triple<String, Int, Boolean>? = when (st) {
                "paid" -> Triple("Lunas", OK, false)
                "nomatch" -> Triple("Tidak cocok", AMBER, false)
                "fail" -> Triple("Gagal kirim", RED, false)
                "sending" -> Triple("Mengirim", MUTED, true)
                "local" -> Triple("Tercatat", MUTED, false)
                "unmon" -> Triple("Belum dipantau", AMBER, false)
                else -> null
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(16))
            }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(wallet(wl, wc), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(tv(o.optString("t"), 12f, FAINT))
            row.addView(top, lp())

            if (amount != null) {
                val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                line.addView(tv(rupiah(amount), 22f, if (st == "paid") OK else INK, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (chip != null) line.addView(status(chip.first, chip.second, chip.third))
                row.addView(line, lp(8))
            } else if (chip != null) {
                row.addView(status(chip.first, chip.second, chip.third), lp(8))
            }
            if (inv.isNotEmpty()) {
                row.addView(tv("Invoice  $inv", 13f, INK, true).apply {
                    setPadding(0, dp(4), 0, dp(4)); setOnClickListener { copy(inv) }
                }, lp(4))
            }
            if (info.isNotEmpty()) row.addView(tv(info, 12f, if (st == "fail") RED else MUTED), lp(2))

            // detail ringkas; ketuk baris untuk membuka teks lengkap
            val detail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            if (title.isNotEmpty()) detail.addView(tv(title, 14f, INK, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, lp())
            if (text.isNotEmpty()) detail.addView(tv(text, 13f, MUTED).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setLineSpacing(0f, 1.15f) }, lp(3))
            val pkgLine = tv(pkg, 11f, FAINT).apply { visibility = View.GONE }
            detail.addView(pkgLine, lp(6))
            row.addView(detail, lp(10))
            var open = false
            row.setOnClickListener {
                open = !open
                for (k in 0 until detail.childCount) {
                    val c = detail.getChildAt(k) as TextView
                    if (c === pkgLine) {
                        c.visibility = if (open) View.VISIBLE else View.GONE
                    } else {
                        c.maxLines = if (open) 50 else if (k == 0) 1 else 2
                    }
                }
            }
            if (st == "unmon") {
                row.addView(button("Pantau package ini", true) {
                    Cfg.addPackage(this@MainActivity, pkg)
                    Toast.makeText(this@MainActivity, "Dipantau: $pkg. Bayar lagi untuk mencoba.", Toast.LENGTH_LONG).show()
                }, lp(12))
            }
            listBox.addView(row, lp())
            listBox.addView(divider(), divLp())

            // animasi hanya untuk entri baru (bukan saat render ulang tiap 3 detik)
            if (!firstRender && rendered.add(id)) { fadeIn(row, animIdx * 40); animIdx++ } else rendered.add(id)
        }
        firstRender = false
    }

    // =====================================================================
    // Premium: beli paket, pilih QRIS merchant developer (GoPay / DANA / ShopeePay)
    // =====================================================================
    private var planJson: JSONObject? = null
    private var premErr = ""
    private var premLoading = false
    private var selPlan: JSONObject? = null
    private var payOrder: JSONObject? = null
    private var qrBmp: Bitmap? = null
    private var payStatus = ""          // pending | paid | expired
    private var payBusy = false
    private var polling = false

    private fun walletColor(m: String): Int = when (m) {
        "dana" -> Color.parseColor("#118EEA")
        "shopeepay" -> Color.parseColor("#EE4D2D")
        "gopay" -> Color.parseColor("#00AA13")
        else -> FAINT
    }

    private fun fmtNum(n: Long): String = String.format(Locale.US, "%,d", n).replace(',', '.')

    private fun absUrl(base: String, u: String): String = if (u.startsWith("/")) base + u else u

    private fun premNote(t: String, c: Int) {
        premBox.addView(tv(t, 13f, c).apply { setPadding(dp(20), dp(8), dp(20), dp(8)); setLineSpacing(0f, 1.15f) }, lp())
    }

    private fun premButton(label: String, filled: Boolean, color: Int = if (filled) Color.WHITE else INK, onClick: () -> Unit) {
        premBox.addView(button(label, filled, color, onClick), lp(8).apply { leftMargin = dp(20); rightMargin = dp(20) })
    }

    private fun backToPlans() {
        selPlan = null; payOrder = null; qrBmp = null; payStatus = ""
        renderPremium()
        loadPlans()
    }

    private fun loadPlans() {
        val p = Cfg.prefs(this)
        val key = p.getString("sec", "") ?: ""
        val base = Api.base(p.getString("wh", "") ?: "")
        if (key.isBlank() || base == null) { planJson = null; premErr = ""; renderPremium(); return }
        if (premLoading) return
        premLoading = true
        if (planJson == null) renderPremium()
        Thread {
            val r = Api.call("GET", "$base/app/plans", key)
            runOnUiThread {
                premLoading = false
                if (r.ok) { planJson = r.json(); premErr = "" } else { premErr = Api.message(r) }
                if (payOrder == null) renderPremium()
            }
        }.start()
    }

    private fun renderPremium() {
        premBox.removeAllViews()
        val head = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(8)) }
        head.addView(tv("Paket Premium", 20f, INK, true))
        premBox.addView(head, lp())

        val p = Cfg.prefs(this)
        val signedIn = (p.getString("sec", "") ?: "").isNotBlank()
        val pj = planJson
        val po = payOrder
        val sp = selPlan
        when {
            !signedIn -> {
                premNote("Masuk dengan akun Emy Cash di tab Pengaturan untuk membeli paket.", MUTED)
                premButton("Buka Pengaturan", true) { selectTab(2, true) }
            }
            po != null -> renderPay(po)
            premErr.isNotEmpty() -> {
                premNote(premErr, RED)
                premButton("Coba lagi", false) { premErr = ""; loadPlans() }
            }
            pj != null && sp != null -> renderMethodPicker(sp, pj)
            pj != null -> renderPlanList(pj)
            else -> premNote("Memuat paket…", MUTED)
        }
        premBox.addView(View(this), lp(24))
    }

    private fun renderPlanList(pj: JSONObject) {
        val active = pj.optBoolean("active")
        premNote(
            if (active) "Paket ${pj.optString("plan_name")} aktif sampai ${fmtDate(pj.optLong("plan_expires_at"))}. Membeli lagi menambah masa aktif."
            else "Belum ada paket aktif. Pilih paket, lalu pilih QRIS merchant untuk membayar.",
            if (active) OK else AMBER
        )
        premBox.addView(divider(), divLp(8))
        val arr = pj.optJSONArray("plans") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val pl = arr.getJSONObject(i)
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(16)) }
            row.pressable()
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(tv(pl.optString("name"), 15f, INK, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(tv(rupiah(pl.optLong("price")), 15f, INK, true))
            row.addView(top, lp())
            row.addView(tv("${pl.optInt("merchants")} merchant · ${fmtNum(pl.optLong("ordersPerDay"))} order/hari · riwayat ${pl.optInt("historyDays")} hari", 12f, MUTED), lp(4))
            row.setOnClickListener { selPlan = pl; renderPremium() }
            premBox.addView(row, lp())
            premBox.addView(divider(), divLp())
        }
        premButton("Muat ulang", false) { loadPlans(); toast("Memuat…") }
    }

    private fun renderMethodPicker(pl: JSONObject, pj: JSONObject) {
        premBox.addView(tv("${pl.optString("name")} · ${rupiah(pl.optLong("price"))}", 16f, INK, true).apply { setPadding(dp(20), dp(4), dp(20), 0) }, lp())
        premNote("Pilih QRIS merchant untuk membayar. Nominal dan QR dibuat khusus untuk pesanan ini.", MUTED)
        val ms = pj.optJSONArray("methods") ?: JSONArray()
        if (ms.length() == 0) premNote("Pembayaran paket belum tersedia di server. Coba lagi nanti.", AMBER)
        for (i in 0 until ms.length()) {
            val m = ms.getJSONObject(i)
            val code = m.optString("method")
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = rounded(BG, 6, LINE)
            }
            row.addView(wallet(m.optString("name"), walletColor(code)), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(tv(if (payBusy) "…" else "Bayar", 13f, MUTED))
            row.pressable()
            row.setOnClickListener { startPay(pl.optString("id"), code) }
            premBox.addView(row, lp(8).apply { leftMargin = dp(20); rightMargin = dp(20) })
        }
        premButton("Kembali ke daftar paket", false) { selPlan = null; renderPremium() }
    }

    private fun startPay(planId: String, method: String) {
        if (payBusy) return
        val p = Cfg.prefs(this)
        val key = p.getString("sec", "") ?: ""
        val base = Api.base(p.getString("wh", "") ?: "") ?: return
        payBusy = true
        toast("Membuat pesanan…")
        Thread {
            val r = Api.call("POST", "$base/app/subscribe", key, JSONObject().put("plan", planId).put("method", method))
            val j = r.json()
            var bmp: Bitmap? = null
            if (r.ok) {
                val raw = Api.bytes(absUrl(base, j.optString("qr_url")))
                if (raw != null) bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            }
            runOnUiThread {
                payBusy = false
                if (r.ok) {
                    payOrder = j; qrBmp = bmp; payStatus = "pending"
                    renderPremium()
                } else {
                    toast(Api.message(r))
                    renderPremium()
                }
            }
        }.start()
    }

    private fun renderPay(o: JSONObject) {
        val base = Api.base(Cfg.prefs(this).getString("wh", "") ?: "") ?: Cfg.DEFAULT_SERVER
        val total = o.optLong("total")
        val name = o.optString("method_name")
        when (payStatus) {
            "paid" -> {
                premBox.addView(tv("Pembayaran diterima", 18f, OK, true).apply { setPadding(dp(20), dp(8), dp(20), 0) }, lp())
                premNote("Paket sudah aktif. Terima kasih.", MUTED)
                premButton("Selesai", true) { backToPlans() }
                return
            }
            "expired" -> {
                premBox.addView(tv("Pesanan kedaluwarsa", 18f, AMBER, true).apply { setPadding(dp(20), dp(8), dp(20), 0) }, lp())
                premNote("Kalau sudah terlanjur membayar, tunggu sebentar: paket aktif otomatis begitu pembayaran terbaca. Kalau belum, buat pesanan baru.", MUTED)
                premButton("Buat pesanan baru", true) { payOrder = null; qrBmp = null; payStatus = ""; renderPremium() }
                return
            }
        }
        premBox.addView(tv("Bayar dengan $name", 16f, INK, true).apply { setPadding(dp(20), dp(4), dp(20), 0) }, lp())
        premBox.addView(tv(rupiah(total), 28f, INK, true).apply { setPadding(dp(20), dp(10), dp(20), 0) }, lp())
        premNote("Scan QR di bawah dengan aplikasi ${name} atau e-wallet lain yang mendukung QRIS, lalu bayar sesuai nominal tepat di atas. Paket aktif otomatis setelah pembayaran terbaca.", MUTED)
        val box = FrameLayout(this).apply { background = rounded(Color.WHITE, 6, LINE) }
        val bmp = qrBmp
        if (bmp != null) {
            box.addView(ImageView(this).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            box.addView(tv("QR tidak termuat.\nKetuk “Buka halaman bayar”.", 13f, MUTED).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        premBox.addView(box, LinearLayout.LayoutParams(dp(260), dp(260)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8) })
        val until = SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date(o.optLong("expires_at")))
        premBox.addView(tv("Berlaku sampai $until · ${o.optString("invoice")}", 12f, FAINT).apply { gravity = Gravity.CENTER }, lp(10))
        premBox.addView(status("Menunggu pembayaran", AMBER, true).apply { gravity = Gravity.CENTER }, lp(12))
        premButton("Salin nominal", false) { copy(total.toString()) }
        premButton("Buka halaman bayar", false) { openUrl(absUrl(base, o.optString("pay_url"))) }
        premButton("Ganti metode / batalkan", false, RED) { payOrder = null; qrBmp = null; payStatus = ""; renderPremium() }
    }

    // Dipanggil tiap 3 detik oleh ticker selama ada pesanan premium yang menunggu
    private fun pollPay() {
        val o = payOrder ?: return
        if ((payStatus != "pending" && payStatus != "expired") || polling) return
        val p = Cfg.prefs(this)
        val key = p.getString("sec", "") ?: ""
        val base = Api.base(p.getString("wh", "") ?: "") ?: return
        val inv = o.optString("invoice")
        polling = true
        Thread {
            val r = Api.call("GET", "$base/app/order/$inv", key)
            runOnUiThread {
                polling = false
                if (payOrder?.optString("invoice") != inv) return@runOnUiThread
                if (r.ok) {
                    val st = r.json().optString("status")
                    if (st == "paid" || (st == "expired" && payStatus != "expired")) {
                        payStatus = st
                        if (st == "paid") { toast("Pembayaran diterima. Paket aktif."); refreshStatus(false) }
                        renderPremium()
                    }
                }
            }
        }.start()
    }

    // =====================================================================
    // Edisi developer: cakupan PG, uji parser lokal, info server pemilik
    // =====================================================================
    private var devCover: LinearLayout? = null

    // kode, nama, pola package bawaan, contoh package untuk uji "dipantau"
    private val GW = listOf(
        arrayOf("gopay", "GoPay", "com.gojek*, com.gopay*", "com.gojek.gopaymerchant"),
        arrayOf("dana", "DANA", "id.dana*, com.dana*", "id.dana.business"),
        arrayOf("shopeepay", "ShopeePay", "com.shopee*, com.shopeepay*", "com.shopee.id")
    )

    private val SAMPLES = listOf(
        arrayOf("GoPay", "com.gojek.gopaymerchant", "Pembayaran QRIS statis diterima", "Rp 10.000 di Toko Emy, dari pelanggan."),
        arrayOf("DANA", "id.dana", "Pembayaran Masuk", "Rp10.000 diterima DANA Bisnis."),
        arrayOf("ShopeePay", "com.shopee.id", "Pembayaran sebesar Rp10.000 diterima", "telah diterima pada transaksi 12345")
    )

    private fun secBox(title: String, desc: String? = null) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(20))
        addView(tv(title, 15f, INK, true))
        if (desc != null) addView(tv(desc, 13f, MUTED).apply { setLineSpacing(0f, 1.15f) }, lp(4))
    }

    private fun stLabel(st: String): String = when (st) {
        "paid" -> "Lunas"
        "nomatch" -> "Tidak cocok"
        "fail" -> "Gagal kirim"
        "sending" -> "Mengirim"
        "local" -> "Tercatat"
        "unmon" -> "Belum dipantau"
        else -> st
    }

    private fun buildDevPage(): ScrollView {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val s1 = secBox("Cakupan payment gateway", "Semua dompet yang dikonfirmasi otomatis (GoPay, DANA, ShopeePay), pola package yang dipantau, dan notifikasi terakhir di perangkat ini.")
        val cover = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        devCover = cover
        s1.addView(cover, lp(8))
        s1.addView(button("Segarkan", false) { renderCoverage() }, lp(8))
        box.addView(s1, lp())
        box.addView(divider(), divLp())

        val s2 = secBox("Uji parser (lokal)", "Hanya diproses di perangkat, tidak dikirim ke server, jadi aman dipakai saat ada order aktif.")
        val ePkg = editBox("Package", false)
        val eTi = editBox("Judul notifikasi", false)
        val eTx = editBox("Isi notifikasi", false)
        val out = tv("", 13f, MUTED).apply { setLineSpacing(0f, 1.2f) }
        s2.addView(ePkg, lp(12))
        s2.addView(eTi, lp(8))
        s2.addView(eTx, lp(8))
        for (sm in SAMPLES) {
            s2.addView(button("Isi contoh ${sm[0]}", false) {
                ePkg.setText(sm[1]); eTi.setText(sm[2]); eTx.setText(sm[3])
            }, lp(8))
        }
        s2.addView(button("Uji", true) {
            runParserTest(ePkg.text.toString().trim(), eTi.text.toString(), eTx.text.toString(), out)
        }, lp(12))
        s2.addView(out, lp(10))
        box.addView(s2, lp())
        box.addView(divider(), divLp())

        val s3 = secBox("Server pemilik", "Butuh Secret pemilik (Pengaturan > Server, mode manual). Menampilkan QRIS developer, pesanan premium, dan pengguna.")
        val srvOut = tv("", 13f, MUTED).apply { setLineSpacing(0f, 1.2f) }
        s3.addView(button("Muat info developer", true) { loadDevInfo(srvOut) }, lp(12))
        s3.addView(srvOut, lp(10))
        box.addView(s3, lp())
        box.addView(divider(), divLp())

        val s4 = secBox("Build")
        val key = Cfg.prefs(this).getString("sec", "") ?: ""
        s4.addView(tv("Emy Cash ${versionInfo().first} (${versionInfo().second})\n${packageName}\nServer: ${serverBase()}\nKunci: ${if (key.length > 6) key.take(6) + "…" else if (key.isEmpty()) "belum ada" else "pendek"}", 13f, MUTED).apply { setLineSpacing(0f, 1.2f) }, lp(8))
        s4.addView(tv("Edisi dev terpasang terpisah dari edisi user. Matikan Notification Access salah satunya agar notifikasi tidak terkirim ganda.", 12f, AMBER).apply { setLineSpacing(0f, 1.15f) }, lp(10))
        box.addView(s4, lp())
        box.addView(View(this), lp(24))

        return ScrollView(this).apply { addView(box); isVerticalScrollBarEnabled = false }
    }

    private fun renderCoverage() {
        val box = devCover ?: return
        box.removeAllViews()
        val arr = try { JSONArray(Cfg.prefs(this).getString("h", "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
        for (g in GW) {
            val m = g[0]
            var n = 0
            var lastT = ""
            var lastSt = ""
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (!o.has("am")) continue
                if (detectMethod(o.optString("p"), o.optString("ti"), o.optString("tx")) != m) continue
                if (n == 0) { lastT = o.optString("t"); lastSt = o.optString("st") }
                n++
            }
            val on = Cfg.monitored(this, g[3])
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, dp(10)) }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(wallet(g[1], walletColor(m)), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(status(if (on) "Dipantau" else "Tidak dipantau", if (on) OK else RED))
            row.addView(top, lp())
            row.addView(tv("Package: ${g[2]}", 12f, MUTED), lp(4))
            row.addView(tv(if (n == 0) "Belum ada notifikasi pembayaran di riwayat" else "$n notifikasi · terakhir $lastT · ${stLabel(lastSt)}", 12f, MUTED), lp(2))
            box.addView(row, lp())
            box.addView(divider(), divLp())
        }
    }

    private fun runParserTest(pkg: String, title: String, text: String, out: TextView) {
        if (pkg.isEmpty() && title.isEmpty() && text.isEmpty()) { out.setTextColor(RED); out.text = "Isi package, judul, dan teks (atau pakai contoh)."; return }
        val amt = parseAmount(title, text)
        val m = detectMethod(pkg, title, text)
        val pay = isPayment(pkg, title, text)
        val mon = Cfg.monitored(this, pkg)
        val sb = StringBuilder()
        sb.append("Nominal: ").append(if (amt == null) "tidak terbaca" else rupiah(amt)).append("\n")
        sb.append("Metode: ").append(if (m == null) "tidak dikenali" else methodName(m)).append("\n")
        sb.append("Dianggap pembayaran masuk: ").append(if (pay) "ya" else "tidak").append("\n")
        sb.append("Package dipantau: ").append(if (mon) "ya" else "belum (mode luas ${if (Cfg.wide(this)) "aktif" else "mati"})")
        out.text = sb.toString()
        out.setTextColor(if (pay && mon) OK else AMBER)
    }

    private fun loadDevInfo(out: TextView) {
        val p = Cfg.prefs(this)
        val key = p.getString("sec", "") ?: ""
        val base = Api.base(p.getString("wh", "") ?: "")
        if (key.isBlank() || base == null) {
            out.setTextColor(RED); out.text = "Isi alamat server dan Secret pemilik di Pengaturan > Server, lalu simpan."; return
        }
        out.setTextColor(MUTED); out.text = "Memuat…"
        Thread {
            val r = Api.call("GET", "$base/app/dev", key)
            var msg: String
            if (r.ok) {
                val j = r.json()
                val sb = StringBuilder()
                sb.append("Server ${j.optString("base")} · v${j.optString("version")}\n")
                val qs = j.optJSONArray("owner_qris")
                if (qs != null) for (i in 0 until qs.length()) {
                    val q = qs.getJSONObject(i)
                    val st = if (!q.optBoolean("configured")) "belum diisi" else if (q.optBoolean("crc_ok")) "OK" else "CRC salah"
                    sb.append("QRIS ${q.optString("name")}: $st\n")
                }
                val so = j.optJSONObject("subscription_orders")
                if (so != null) sb.append("Order premium: ${so.optInt("pending")} menunggu · ${so.optInt("paid_today")} lunas hari ini (${rupiah(so.optLong("paid_today_total"))})\n")
                val us = j.optJSONObject("users")
                if (us != null) sb.append("Pengguna: ${us.optInt("total")} · aktif ${us.optInt("active")} · perangkat online ${us.optInt("devices_online")}\n")
                val um = j.optJSONArray("unmatched")
                sb.append("Pembayaran tak cocok terakhir: ${um?.length() ?: 0}")
                msg = sb.toString()
            } else if (r.code == 403) {
                msg = "Kunci ini bukan Secret pemilik. Isi Secret di Pengaturan > Server (mode manual)."
            } else if (r.code == 404) {
                msg = "Server belum mendukung panel developer (HTTP 404). Perbarui gateway ke versi terbaru."
            } else msg = Api.message(r)
            runOnUiThread { out.setTextColor(if (r.ok) INK else RED); out.text = msg }
        }.start()
    }
}
