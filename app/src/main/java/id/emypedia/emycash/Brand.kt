package id.emypedia.emycash

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable

// Identitas visual tiap dompet: warna merek (DANA biru, GoPay hijau, ShopeePay oranye), nama, dan ikon aplikasi aslinya.
object Brand {
    val LIME = Color.parseColor("#C8FF3E")
    private val DANA = Color.parseColor("#2F9BFF")
    private val SHOPEE = Color.parseColor("#FF6B35")
    private val GOPAY = Color.parseColor("#19D66B")
    private val NEUTRAL = Color.parseColor("#6D6C82")

    fun color(method: String?): Int = when (method) {
        "dana" -> DANA
        "shopeepay" -> SHOPEE
        "gopay" -> GOPAY
        else -> NEUTRAL
    }

    fun initials(method: String?): String = when (method) {
        "dana" -> "DA"
        "shopeepay" -> "SP"
        "gopay" -> "GP"
        else -> "?"
    }

    fun methodOfPkg(pkg: String): String? = when {
        pkg.startsWith("com.shopee") -> "shopeepay"
        pkg.startsWith("com.gojek") || pkg.startsWith("com.gopay") -> "gopay"
        pkg.startsWith("id.dana") || pkg.startsWith("com.dana") -> "dana"
        else -> null
    }

    // Judul notifikasi dengan bahasa aplikasi asalnya
    fun receivedTitle(method: String?, amount: Long): String = when (method) {
        "shopeepay" -> "ShopeePay: pembayaran diterima ${rupiah(amount)}"
        "dana" -> "DANA: dana masuk ${rupiah(amount)}"
        "gopay" -> "GoPay: kamu menerima ${rupiah(amount)}"
        else -> "Pembayaran masuk ${rupiah(amount)}"
    }

    // Aplikasi dompet yang terpasang di HP ini (berdasarkan pola package yang dipantau)
    fun installed(c: Context): List<ApplicationInfo> {
        val pm = c.packageManager
        val all = try { pm.getInstalledApplications(0) } catch (e: Exception) { emptyList<ApplicationInfo>() }
        return all.filter {
            it.packageName != c.packageName && methodOfPkg(it.packageName) != null &&
                !Cfg.isChatPkg(it.packageName) && pm.getLaunchIntentForPackage(it.packageName) != null
        }.sortedBy { it.packageName }
    }

    fun label(c: Context, pkg: String): String = try {
        c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg.substringAfterLast('.') }

    private val cache = HashMap<String, Bitmap>()

    // Ikon aplikasi asli bila terpasang; kalau tidak, lencana berwarna merek dengan inisial
    @Synchronized
    fun icon(c: Context, pkg: String?, method: String?, px: Int): Bitmap {
        val key = "$pkg|$method|$px"
        cache[key]?.let { return it }
        val m = method?.takeIf { it.isNotEmpty() } ?: pkg?.let { methodOfPkg(it) }
        var bmp: Bitmap? = null
        if (pkg != null && !pkg.startsWith("emy.")) bmp = fromPackage(c, pkg, px)
        if (bmp == null && m != null) {
            val app = installed(c).firstOrNull { methodOfPkg(it.packageName) == m }
            if (app != null) bmp = fromPackage(c, app.packageName, px)
        }
        val out = bmp ?: badge(m, px)
        cache[key] = out
        return out
    }

    private fun fromPackage(c: Context, pkg: String, px: Int): Bitmap? = try {
        toBitmap(c.packageManager.getApplicationIcon(pkg), px)
    } catch (e: Exception) { null }

    private fun toBitmap(d: Drawable, px: Int): Bitmap {
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        d.setBounds(0, 0, px, px)
        d.draw(cv)
        return b
    }

    private fun badge(method: String?, px: Int): Bitmap {
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color(method)
        cv.drawRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), px * 0.28f, px * 0.28f, p)
        p.color = Color.parseColor("#0A0A0E")
        p.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        p.textSize = px * 0.40f
        p.textAlign = Paint.Align.CENTER
        cv.drawText(initials(method), px / 2f, px / 2f - (p.descent() + p.ascent()) / 2f, p)
        return b
    }

    // Logo Emy Cash (tile lime + huruf E), dipakai di header
    fun logo(px: Int): Bitmap {
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val u = px / 100f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.parseColor("#A58BFF")
        cv.drawRoundRect(RectF(15 * u, 15 * u, 93 * u, 93 * u), 16 * u, 16 * u, p)
        p.color = LIME
        cv.drawRoundRect(RectF(7 * u, 7 * u, 81 * u, 81 * u), 16 * u, 16 * u, p)
        p.color = Color.parseColor("#0A0A0E")
        cv.drawRect(28 * u, 24 * u, 38 * u, 64 * u, p)
        cv.drawRect(28 * u, 24 * u, 62 * u, 34 * u, p)
        cv.drawRect(28 * u, 39.5f * u, 58 * u, 49.5f * u, p)
        cv.drawRect(28 * u, 54 * u, 62 * u, 64 * u, p)
        return b
    }
}
