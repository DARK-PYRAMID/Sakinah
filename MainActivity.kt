package com.sakinah.app

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.AudioAttributes
import android.view.animation.RotateAnimation
import android.view.animation.Animation
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

data class Friend(
    val id: String,
    var name: String,
    var photo: String = "",
    var customDua: String = "",
    var time: String = "09:00",
    var count: Int = 3,
    var enabled: Boolean = true
)

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("sakinah_data", MODE_PRIVATE) }
    private val friends = mutableListOf<Friend>()
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private var selectedPhotoPath = ""
    private var activeCard: LinearLayout? = null
    private var selectedFriendId: String? = null
    private var mediaPlayer: MediaPlayer? = null
    private var qiblaText: TextView? = null
    private var qiblaArrow: TextView? = null
    private var sensorManager: SensorManager? = null
    private var qiblaBearing: Float = 0f
    private var lastAzimuth: Float = 0f
    private var qiblaListener: SensorEventListener? = null
    private val gold = Color.rgb(222, 197, 128)
    private val bg = Color.rgb(7, 25, 18)
    private val panel = Color.rgb(17, 48, 36)
    private val pale = Color.rgb(246, 242, 226)
    private val muted = Color.rgb(184, 201, 189)
    private val prayers = listOf(
        "اللهم احفظه بعينك التي لا تنام، واكتب له الخير حيث كان، واصرف عنه كل سوء.",
        "اللهم ارزقه راحةً في القلب، وسعةً في الرزق، وبركةً في العمر والعمل.",
        "اللهم يسّر له أمره، واشرح صدره، ووفّقه لما تحب وترضى.",
        "اللهم اجبر خاطره جبرًا يليق بكرمك، وأبدل تعبه طمأنينةً وفرحًا.",
        "اللهم اكتب له من الخير أكثر مما يرجو، واصرف عنه ما يؤذيه، وبارك له فيما أعطيته.",
        "اللهم اغفر لنا وله، وارحمنا برحمتك، وأعنّا على ذكرك وشكرك وحسن عبادتك.",
        "اللهم ارزقه قلبًا مطمئنًا، ونفسًا راضية، وأيامًا تحمل له الخير.",
        "اللهم افتح له أبواب رحمتك وفضلك، وارزقه رزقًا حلالًا طيبًا مباركًا فيه.",
        "اللهم كن له عونًا وسندًا، ويسّر له كل عسير، واجعل له من كل ضيق مخرجًا."
    )
    private val azkar = listOf(
        "سُبْحَانَ اللهِ وَبِحَمْدِهِ", "أَسْتَغْفِرُ اللهَ وَأَتُوبُ إِلَيْهِ",
        "لَا إِلَهَ إِلَّا اللهُ", "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ",
        "سُبْحَانَ اللهِ، وَالْحَمْدُ لِلَّهِ، وَلَا إِلَهَ إِلَّا اللهُ، وَاللهُ أَكْبَرُ"
    )
    private val adhanPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val file = File(filesDir, "custom_adhan_${System.currentTimeMillis()}.audio")
                contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                prefs.edit().putString("adhan_audio", file.absolutePath).apply()
                toast("تم حفظ ملف الأذان على هذا الجهاز")
            } catch (_: Exception) { toast("تعذر حفظ ملف الصوت") }
        }
    }
    private val photoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val file = File(filesDir, "friend_${System.currentTimeMillis()}.img")
                contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                selectedPhotoPath = file.absolutePath
                showAddFriendDialog()
            } catch (_: Exception) { toast("تعذر حفظ الصورة، جرّب صورة أخرى") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadFriends()
        createChannel()
        askNotificationPermission()
        buildShell()
        showHome()
        scheduleAll()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(22, 20, 22, 16); setBackgroundColor(Color.rgb(9, 32, 23))
        }
        header.addView(text("۞  سَكِينَة", 28, gold, true))
        header.addView(text("أثر طيب · أصحابك في دعائك", 13, muted))
        root.addView(header)
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(8, 8, 8, 8); setBackgroundColor(Color.rgb(10, 35, 25))
        }
        listOf("الرئيسية", "أصحابي", "الأذكار", "القرآن", "القبلة", "الصلاة").forEachIndexed { index, label ->
            val b = Button(this).apply {
                text = label; textSize = 12f; setTextColor(gold)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { when(index) { 0 -> showHome(); 1 -> showFriends(); 2 -> showAzkar(); 3 -> showQuran(); 4 -> showQibla(); 5 -> showPrayerTimes() } }
            }
            nav.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(nav)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 28) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun showHome() {
        content.removeAllViews()
        content.addView(text("خلّي لأحبابك نصيب من دعائك 🤍", 23, pale, true))
        content.addView(text("تذكير بالخير، في الوقت اللي تختاره.", 14, muted))
        space(14)
        card {
            addView(text("دعوة اليوم", 18, gold, true))
            val f = friends.firstOrNull { it.id == selectedFriendId } ?: friends.firstOrNull()
            if (f == null) {
                addView(text("أضف صاحبًا، وسنذكّرك بالدعاء له تلقائيًا.", 15, pale))
                button("＋ أضف صاحبًا") { choosePhoto() }
            } else {
                selectedFriendId = f.id
                addFriendPhoto(this, f.photo)
                addView(text("🤍  ${f.name}", 18, pale, true))
                addView(text(if (f.customDua.isNotBlank()) f.customDua else prayers[(Date().time / 86_400_000L % prayers.size).toInt()], 17, pale))
                button("دعاء آخر ↻") {
                    val p = prayers.random()
                    AlertDialog.Builder(this).setTitle("دعاء لـ ${f.name}").setMessage(p)
                        .setPositiveButton("تم") { _, _ -> showHome() }.show()
                }
                button("🤲 دعوت له") { toast("ربنا يتقبل منك ويكتب له الخير 🤍") }
            }
        }
        space(14)
        card {
            addView(text("خطوتك اليومية", 18, gold, true))
            addView(text("• أذكار الصباح والمساء\n• الاستغفار والدعاء للأحباب\n• متابعة مواقيت الصلاة", 15, pale))
            button("افتح الأذكار") { showAzkar() }
        }
        space(14)
        card {
            addView(text("أصحابك في دعائك: ${friends.size}", 17, gold, true))
            button("إدارة الأصحاب") { showFriends() }
        }
        space(12)
        card {
            addView(text("🌙 وضع رمضان", 18, gold, true))
            addView(text("تذكيرات السحور والإفطار ومتابعة وردك في رمضان.", 13, muted))
            button("فتح وضع رمضان") { showRamadanMode() }
        }
    }

    private fun showRamadanMode() {
        content.removeAllViews()
        content.addView(text("🌙 وضع رمضان", 24, pale, true))
        content.addView(text("أدوات بسيطة لتنظيم ورد القرآن وتذكير السحور والإفطار. راجع المواقيت المحلية دائمًا.", 13, muted))
        space(12)
        val timings = try { JSONObject(prefs.getString("prayer_times", "{}")) } catch (_: Exception) { JSONObject() }
        val maghrib = timings.optString("Maghrib", "لم تُحمّل بعد").take(5)
        val fajr = timings.optString("Fajr", "لم يُحمّل بعد").take(5)
        card {
            addView(text("موعد الإفطار التقريبي", 17, gold, true))
            addView(text("المغرب: $maghrib", 21, pale, true))
            addView(text("موعد الإمساك التقريبي", 17, gold, true))
            addView(text("الفجر: $fajr", 21, pale, true))
        }
        space(10)
        card {
            addView(text("ورد القرآن", 17, gold, true))
            addView(text("اجعل لك وردًا يوميًا ثابتًا، ولو قليلًا، وتابع آخر سورة قرأتها من قسم القرآن.", 14, pale))
            button("افتح القرآن الكريم") { showQuran() }
        }
        space(10)
        card {
            addView(text("تذكير السحور", 17, gold, true))
            addView(text("يمكنك ضبط منبّه السحور من تطبيق الساعة في هاتفك. هذه الشاشة لا تفترض بداية رمضان أو نهايته تلقائيًا.", 13, muted))
        }
        button("رجوع للرئيسية") { showHome() }
    }

    private fun showFriends() {
        content.removeAllViews()
        content.addView(text("أصحابي في دعائي", 24, pale, true))
        content.addView(text("أضف الشخص مرة واحدة، واختر عدد التذكيرات اليومية.", 13, muted))
        space(10)
        button("＋ إضافة صاحب وصورته") { choosePhoto() }
        space(10)
        if (friends.isEmpty()) {
            card { addView(text("لسه ما أضفتش حد 🤍", 16, muted)) }
        } else friends.toList().forEach { f ->
            card {
                addFriendPhoto(this, f.photo)
                addView(text("🤍  ${f.name}", 18, gold, true))
                addView(text(if (f.enabled) "التذكير مفعّل · ${f.count} يوميًا · ${f.time}" else "التذكير متوقف", 12, muted))
                if (f.customDua.isNotBlank()) addView(text("دعاؤك: ${f.customDua}", 14, pale))
                button("عرض دعاء الآن") {
                    selectedFriendId = f.id
                    AlertDialog.Builder(this@MainActivity).setTitle("دعاء لـ ${f.name}")
                        .setMessage(if (f.customDua.isNotBlank()) f.customDua else prayers.random())
                        .setPositiveButton("دعوت له") { _, _ -> toast("ربنا يتقبل منك 🤍") }
                        .setNegativeButton("إغلاق", null).show()
                }
                button(if (f.enabled) "إيقاف التذكيرات" else "تفعيل التذكيرات") {
                    f.enabled = !f.enabled; saveFriends(); scheduleAll(); showFriends()
                }
                button("تعديل الاسم والدعاء والمواعيد") { editFriend(f) }
                button("حذف الشخص") {
                    AlertDialog.Builder(this@MainActivity).setMessage("حذف ${f.name} من القائمة؟")
                        .setNegativeButton("إلغاء", null).setPositiveButton("حذف") { _, _ ->
                            friends.removeAll { it.id == f.id }; saveFriends(); scheduleAll(); showFriends()
                        }.show()
                }
            }
            space(10)
        }
    }

    private fun choosePhoto() {
        selectedPhotoPath = ""
        AlertDialog.Builder(this).setTitle("إضافة صورة لصاحبك")
            .setMessage("تقدر تختار صورة، أو تكمل من غير صورة.")
            .setPositiveButton("اختيار صورة") { _, _ -> photoPicker.launch("image/*") }
            .setNegativeButton("بدون صورة") { _, _ -> showAddFriendDialog() }
            .show()
    }

    private fun showAddFriendDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 8, 28, 0) }
        val name = EditText(this).apply { hint = "اسم صاحبك"; setSingleLine(true) }
        val dua = EditText(this).apply { hint = "دعاء مخصص (اختياري)" }
        val time = EditText(this).apply { hint = "موعد أول تذكير HH:mm"; setText("09:00"); setSingleLine(true) }
        val count = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("مرة يوميًا", "مرتين يوميًا", "3 مرات يوميًا")) }
        box.addView(name); box.addView(dua); box.addView(time); box.addView(count)
        AlertDialog.Builder(this).setTitle("صاحب جديد").setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                val n = name.text.toString().trim()
                if (n.isBlank()) { toast("اكتب اسم صاحبك"); return@setPositiveButton }
                val tm = time.text.toString().trim().takeIf { Regex("\\d{2}:\\d{2}").matches(it) } ?: "09:00"
                val f = Friend(System.currentTimeMillis().toString(), n, selectedPhotoPath, dua.text.toString().trim(), tm,
                    count.selectedItemPosition + 1, true)
                friends.add(f); selectedFriendId = f.id; saveFriends(); scheduleAll(); showFriends()
                toast("تمت إضافة ${f.name} 🤍")
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun editFriend(f: Friend) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0) }
        val name = EditText(this).apply { setText(f.name); hint = "الاسم" }
        val dua = EditText(this).apply { setText(f.customDua); hint = "دعاء خاص (اختياري)" }
        val time = EditText(this).apply { setText(f.time); hint = "موعد HH:mm" }
        val count = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("مرة يوميًا", "مرتين يوميًا", "3 مرات يوميًا"))
            setSelection((f.count - 1).coerceIn(0, 2))
        }
        box.addView(name); box.addView(dua); box.addView(time); box.addView(count)
        AlertDialog.Builder(this).setTitle("تعديل ${f.name}").setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                if (name.text.toString().trim().isNotBlank()) f.name = name.text.toString().trim()
                f.customDua = dua.text.toString().trim()
                f.time = time.text.toString().trim().takeIf { Regex("\\d{2}:\\d{2}").matches(it) } ?: "09:00"
                f.count = count.selectedItemPosition + 1
                saveFriends(); scheduleAll(); showFriends()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun showAzkar() {
        content.removeAllViews()
        content.addView(text("الأذكار والاستغفار", 24, pale, true))
        content.addView(text("اختر ذكرًا وكرره على مهل.", 13, muted))
        space(10)
        var total = 0
        val counter = TextView(this).apply { text = "عدد التسبيحات: 0"; textSize = 20f; setTextColor(gold); gravity = Gravity.CENTER; setPadding(0, 18, 0, 18) }
        content.addView(counter)
        azkar.forEach { z ->
            card {
                addView(text(z, 18, pale, true))
                button("سبّح / كرر الذكر") { total++; counter.text = "عدد التسبيحات: $total" }
            }
            space(8)
        }
        button("تصفير العداد") { total = 0; counter.text = "عدد التسبيحات: 0" }
    }

    private fun showPrayerTimes() {
        content.removeAllViews()
        content.addView(text("مواقيت الصلاة", 24, pale, true))
        content.addView(text("اضبط المدينة لعرض مواقيت الصلاة اليومية من خدمة مواقيت الصلاة عبر الإنترنت.", 13, muted))
        space(12)
        val city = EditText(this).apply { hint = "المدينة بالإنجليزية، مثال: Jizan"; setText(prefs.getString("city", "Jizan")) }
        content.addView(city)
        button("اختيار ملف أذان من الهاتف") { adhanPicker.launch("audio/*") }
        content.addView(text(if (prefs.getString("adhan_audio", null) != null) "يوجد ملف أذان مخصص محفوظ على الجهاز." else "يمكنك اختيار ملف أذان تملك حق استخدامه، أو سيستخدم التطبيق تنبيه النظام.", 12, muted))
        button("جلب المواقيت وتفعيل تنبيهات الصلاة") {
            val c = city.text.toString().trim().ifBlank { "Jizan" }
            prefs.edit().putString("city", c).apply()
            content.addView(text("جاري جلب المواقيت...", 14, gold))
            Thread {
                try {
                    val url = java.net.URL("https://api.aladhan.com/v1/timingsByCity?city=${java.net.URLEncoder.encode(c, "UTF-8")}&country=Saudi%20Arabia&method=4")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 8000; conn.readTimeout = 8000
                    val obj = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val t = obj.getJSONObject("data").getJSONObject("timings")
                    val prayerList = listOf("Fajr" to "الفجر", "Dhuhr" to "الظهر", "Asr" to "العصر", "Maghrib" to "المغرب", "Isha" to "العشاء")
                    prefs.edit().putString("prayer_times", t.toString()).apply()
                    val lines = listOf("Fajr" to "الفجر", "Sunrise" to "الشروق", "Dhuhr" to "الظهر", "Asr" to "العصر", "Maghrib" to "المغرب", "Isha" to "العشاء")
                        .joinToString("\n") { (key, ar) -> "$ar  ·  ${t.optString(key)}" }
                    prayerList.forEachIndexed { idx, item -> schedulePrayerAlarm(item.first, item.second, t.optString(item.first), idx) }
                    runOnUiThread {
                        card { addView(text("مواقيت $c اليوم", 18, gold, true)); addView(text(lines, 18, pale)); addView(text("تم إعداد تنبيهات الصلوات الخمس. قد تتأثر الدقة بطريقة الحساب وإعدادات الجهاز.", 12, muted)) }
                    }
                    conn.disconnect()
                } catch (_: Exception) { runOnUiThread { toast("تعذر جلب المواقيت. تحقق من الإنترنت واسم المدينة.") } }
            }.start()
        }
        space(10)
        card {
            addView(text("عن تنبيهات الصلاة", 16, gold, true))
            addView(text("يستخدم التطبيق تنبيهًا من النظام عند الصلاة. يمكنك اختيار ملف أذان تملك حق استخدامه من هاتفك ليعمل عند الموعد. السماح بالإشعارات والمنبهات الدقيقة وتحسين إعدادات البطارية قد يكون ضروريًا؛ بعض الأجهزة قد تؤخر التنبيهات.", 13, muted))
        }
    }

    private fun showQuran() {
        content.removeAllViews()
        content.addView(text("القرآن الكريم", 24, pale, true))
        content.addView(text("تصفح السور، واحفظ موضع قراءتك، واستمع للتلاوة عند توفر الإنترنت.", 13, muted))
        space(10)
        val status = text("جاري تحميل فهرس السور...", 14, gold)
        content.addView(status)
        Thread {
            try {
                val conn = java.net.URL("https://api.alquran.cloud/v1/surah").openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000; conn.readTimeout = 10000
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val data = json.getJSONArray("data")
                val names = (0 until data.length()).map { i -> data.getJSONObject(i) }
                runOnUiThread {
                    content.removeView(status)
                    names.forEach { surah ->
                        card {
                            addView(text("${surah.optInt("number")}. ${surah.optString("name")}", 18, gold, true))
                            addView(text("${surah.optString("englishName") } · ${surah.optInt("numberOfAyahs")} آية", 12, muted))
                            button("فتح السورة") { loadSurah(surah.optInt("number"), surah.optString("name")) }
                        }
                        space(6)
                    }
                }
                conn.disconnect()
            } catch (_: Exception) { runOnUiThread { status.text = "تعذر تحميل القرآن. تحقق من اتصال الإنترنت ثم افتح القسم مرة أخرى." } }
        }.start()
    }

    private fun loadSurah(number: Int, name: String) {
        content.removeAllViews()
        content.addView(text(name, 25, gold, true))
        content.addView(text("نص القرآن يُحمّل من واجهة Quran Cloud عبر الإنترنت.", 12, muted))
        space(8)
        val status = text("جاري تحميل الآيات...", 14, gold)
        content.addView(status)
        Thread {
            try {
                val conn = java.net.URL("https://api.alquran.cloud/v1/surah/$number/quran-uthmani").openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000; conn.readTimeout = 10000
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val ayahs = json.getJSONObject("data").getJSONArray("ayahs")
                runOnUiThread {
                    content.removeView(status)
                    val saved = prefs.getInt("last_surah", 1)
                    button(if (saved == number) "✓ آخر سورة قرأتها" else "حفظ السورة كآخر موضع قراءة") {
                        prefs.edit().putInt("last_surah", number).apply(); toast("تم حفظ موضع القراءة")
                    }
                    for (i in 0 until ayahs.length()) {
                        val ayah = ayahs.getJSONObject(i)
                        val ayahNumber = ayah.optInt("number")
                        card {
                            addView(text(ayah.optString("text"), 22, pale, true).apply { gravity = Gravity.CENTER; setLineSpacing(10f, 1.15f) })
                            addView(text("﴿ ${ayah.optInt("numberInSurah")} ﴾", 13, gold).apply { gravity = Gravity.CENTER })
                            button("▶ استمع للآية") { playAyah(ayahNumber) }
                        }
                        space(6)
                    }
                    button("رجوع لفهرس السور") { showQuran() }
                }
                conn.disconnect()
            } catch (_: Exception) { runOnUiThread { status.text = "تعذر تحميل السورة. تحقق من الإنترنت." } }
        }.start()
    }

    private fun playAyah(globalAyahNumber: Int) {
        Thread {
            try {
                val url = java.net.URL("https://api.alquran.cloud/v1/ayah/$globalAyahNumber/ar.alafasy")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000; conn.readTimeout = 10000
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val audioUrl = json.getJSONObject("data").optString("audio")
                conn.disconnect()
                runOnUiThread {
                    try {
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(audioUrl); setOnPreparedListener { it.start(); toast("بدأ تشغيل التلاوة") }
                            setOnErrorListener { mp, _, _ -> mp.release(); mediaPlayer = null; toast("تعذر تشغيل الصوت"); true }
                            prepareAsync()
                        }
                    } catch (_: Exception) { toast("تعذر تشغيل التلاوة") }
                }
            } catch (_: Exception) { runOnUiThread { toast("تعذر جلب ملف التلاوة") } }
        }.start()
    }

    private fun showQibla() {
        content.removeAllViews()
        content.addView(text("اتجاه القبلة", 24, pale, true))
        content.addView(text("اتجاه تقريبي من موقعك الحالي باستخدام GPS وحساس البوصلة. ابعد الهاتف عن المعادن وأعد معايرة البوصلة بتحريكه على شكل رقم 8.", 13, muted))
        space(12)
        qiblaArrow = text("▲", 82, gold, true).apply { gravity = Gravity.CENTER }
        content.addView(qiblaArrow, LinearLayout.LayoutParams(-1, 220))
        qiblaText = text("اضغط على الزر لتحديد اتجاه القبلة", 16, pale, true).apply { gravity = Gravity.CENTER }
        content.addView(qiblaText)
        space(10)
        button("تحديد اتجاه القبلة") { locateQibla() }
        card {
            addView(text("ملاحظة", 16, gold, true))
            addView(text("دقة الاتجاه تعتمد على حساسات هاتفك والسماح بالوصول للموقع. لا تعتمد عليه وحده إذا كانت البوصلة غير معايرة.", 13, muted))
        }
    }

    private fun locateQibla() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 2202)
            toast("اسمح بالوصول للموقع ثم اضغط مرة أخرى")
            return
        }
        try {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            val loc = (listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).mapNotNull { provider ->
                try { lm.getLastKnownLocation(provider) } catch (_: Exception) { null }
            }).maxByOrNull { it.time }
            if (loc == null) { qiblaText?.text = "تعذر تحديد موقعك. فعّل الموقع وحاول في مكان مفتوح."; return }
            val kaaba = Location("kaaba").apply { latitude = 21.4225; longitude = 39.8262 }
            qiblaBearing = loc.bearingTo(kaaba)
            val sensor = getSystemService(SENSOR_SERVICE) as SensorManager
            sensorManager = sensor
            val accelerometer = sensor.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val magnetometer = sensor.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (accelerometer == null || magnetometer == null) { qiblaText?.text = "هاتفك لا يوفر حساسات البوصلة المطلوبة."; return }
            var gravityValues = FloatArray(3); var magneticValues = FloatArray(3)
            qiblaListener?.let { sensor.unregisterListener(it) }
            qiblaListener = object : SensorEventListener {
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) gravityValues = event.values.clone()
                    if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) magneticValues = event.values.clone()
                    val r = FloatArray(9); val orientation = FloatArray(3)
                    if (SensorManager.getRotationMatrix(r, null, gravityValues, magneticValues)) {
                        SensorManager.getOrientation(r, orientation)
                        val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        val angle = ((qiblaBearing - azimuth + 360f) % 360f)
                        qiblaArrow?.rotation = angle
                        qiblaText?.text = "اتجاه القبلة: ${angle.toInt()}° من اتجاه الشمال المغناطيسي"
                    }
                }
            }
            sensor.registerListener(qiblaListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            sensor.registerListener(qiblaListener, magnetometer, SensorManager.SENSOR_DELAY_UI)
        } catch (_: SecurityException) { toast("تعذر الوصول للموقع") }
        catch (_: Exception) { toast("تعذر تحديد القبلة على هذا الجهاز") }
    }

    private fun schedulePrayerAlarm(key: String, arabicName: String, timeText: String, index: Int) {
        val clean = timeText.take(5)
        val parts = clean.split(":")
        if (parts.size != 2) return
        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val intent = Intent(this, ReminderReceiver::class.java).apply {
            putExtra("is_prayer", true); putExtra("prayer_key", key); putExtra("prayer_name", arabicName)
            putExtra("prayer_time", clean); putExtra("slot", index)
        }
        val pi = PendingIntent.getBroadcast(this, 7000 + index, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            else if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            else am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } catch (_: Exception) { am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi) }
    }

    private fun scheduleAll() {
        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        friends.forEach { f ->
            for (i in 0..2) {
                val request = (f.id.hashCode() * 10) + i + 1
                am.cancel(PendingIntent.getBroadcast(this, request, Intent(this, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            if (!f.enabled) return@forEach
            val times = mutableListOf(f.time)
            if (f.count >= 2) times.add("14:00")
            if (f.count >= 3) times.add("19:30")
            times.forEachIndexed { i, time ->
                val parts = time.split(":")
                val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0,23) ?: 9
                val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0,59) ?: 0
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                }
                val intent = Intent(this, ReminderReceiver::class.java).apply {
                    putExtra("friend_id", f.id); putExtra("friend_name", f.name)
                    putExtra("custom_dua", f.customDua); putExtra("slot", i)
                }
                val pi = PendingIntent.getBroadcast(this, f.id.hashCode() * 10 + i + 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                try {
                    if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                    } else if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                    else am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                } catch (_: Exception) { am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi) }
            }
        }
    }

    private fun loadFriends() {
        val arr = try { JSONArray(prefs.getString("friends", "[]")) } catch (_: Exception) { JSONArray() }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            friends.add(Friend(o.optString("id"), o.optString("name"), o.optString("photo"), o.optString("dua"),
                o.optString("time", "09:00"), o.optInt("count", 3), o.optBoolean("enabled", true)))
        }
        selectedFriendId = friends.firstOrNull()?.id
    }
    private fun saveFriends() {
        val arr = JSONArray()
        friends.forEach { f -> arr.put(JSONObject().put("id", f.id).put("name", f.name).put("photo", f.photo)
            .put("dua", f.customDua).put("time", f.time).put("count", f.count).put("enabled", f.enabled)) }
        prefs.edit().putString("friends", arr.toString()).apply()
        SakinahWidgetProvider.updateAll(this)
    }
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel("sakinah_reminders", "تذكيرات سَكِينَة", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "تذكيرات الدعاء والأذكار"
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
            }
            nm.createNotificationChannel(channel)
            nm.createNotificationChannel(NotificationChannel("sakinah_prayer_default", "تنبيهات الصلاة", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "تنبيهات مواقيت الصلاة"
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            })
            nm.createNotificationChannel(NotificationChannel("sakinah_prayer_audio", "أذان مخصص", NotificationManager.IMPORTANCE_LOW).apply {
                description = "تشغيل ملف الأذان الذي اختاره المستخدم"
                setSound(null, null)
            })
        }
    }
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
    private fun addFriendPhoto(parent: LinearLayout, path: String) {
        if (path.isBlank()) return
        val file = File(path)
        if (!file.exists()) return
        try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
            val image = ImageView(this).apply { setImageBitmap(bitmap); scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = "صورة صاحب" }
            val size = (resources.displayMetrics.density * 88).toInt()
            val lp = LinearLayout.LayoutParams(size, size); lp.bottomMargin = 8
            parent.addView(image, lp)
        } catch (_: Exception) { }
    }

    private fun text(s: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size.toFloat(); setTextColor(color); gravity = Gravity.RIGHT
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 5, 0, 5)
    }
    private fun space(h: Int) { content.addView(View(this), LinearLayout.LayoutParams(1, h)) }
    private fun card(block: LinearLayout.() -> Unit) {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(16, 14, 16, 14); setBackgroundColor(panel)
            elevation = 2f
        }
        val previous = activeCard
        activeCard = c
        c.block()
        activeCard = previous
        val lp = LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = 6
        content.addView(c, lp)
    }
    private fun button(label: String, action: () -> Unit) {
        val b = Button(this).apply { text = label; textSize = 13f; setTextColor(bg); setBackgroundColor(gold); setOnClickListener { action() } }
        val lp = LinearLayout.LayoutParams(-1, -2); lp.topMargin = 8
        (activeCard ?: content).addView(b, lp)
    }
    override fun onDestroy() {
        qiblaListener?.let { sensorManager?.unregisterListener(it) }
        mediaPlayer?.release(); mediaPlayer = null
        super.onDestroy()
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
