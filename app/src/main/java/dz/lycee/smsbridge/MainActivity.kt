package dz.lycee.smsbridge

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    companion object {
        const val ACTION_SENT = "dz.lycee.smsbridge.SMS_SENT"
        const val ACTION_DELIVERED = "dz.lycee.smsbridge.SMS_DELIVERED"
        const val ACTION_STATUS = "dz.lycee.smsbridge.STATUS"
        private const val REQ_PERMS = 700
    }

    private lateinit var simSpinner: Spinner
    private lateinit var phoneInput: EditText
    private lateinit var messageInput: EditText
    private lateinit var statusText: TextView
    private val sims = mutableListOf<SubscriptionInfo>()
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            statusText.text = intent?.getStringExtra("result") ?: ""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        registerReceiver(statusReceiver, IntentFilter(ACTION_STATUS))
        statusText.text = getSharedPreferences("bridge", MODE_PRIVATE)
            .getString("last_result", "جاهز لاختبار الإرسال")
        refreshSims()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 28, 28, 28)
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }
        fun label(text: String) = TextView(this).apply {
            this.text = text; textSize = 17f; setPadding(0, 14, 0, 6)
        }
        val title = TextView(this).apply {
            text = "LYCEE SMS Bridge\nربط هاتف الإدارة بنظام الثانوية"
            textSize = 22f; gravity = Gravity.CENTER; setPadding(0, 8, 0, 18)
        }
        root.addView(title)
        root.addView(label("الشريحة المستخدمة"))
        simSpinner = Spinner(this)
        root.addView(simSpinner, LinearLayout.LayoutParams(-1, -2))
        val refresh = Button(this).apply { text = "تحديث الشرائح"; setOnClickListener { refreshSims() } }
        root.addView(refresh)
        root.addView(label("رقم الهاتف"))
        phoneInput = EditText(this).apply {
            hint = "مثال: 0550123456"; inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        root.addView(phoneInput, LinearLayout.LayoutParams(-1, -2))
        root.addView(label("نص الرسالة"))
        messageInput = EditText(this).apply {
            hint = "اكتب رسالة تجريبية"; minLines = 3; gravity = Gravity.TOP
        }
        root.addView(messageInput, LinearLayout.LayoutParams(-1, 0, 1f))
        val send = Button(this).apply {
            text = "إرسال SMS تجريبية"
            setOnClickListener { sendTestSms() }
        }
        root.addView(send, LinearLayout.LayoutParams(-1, -2))
        statusText = TextView(this).apply {
            textSize = 15f; gravity = Gravity.CENTER; setPadding(0, 18, 0, 4)
        }
        root.addView(statusText)
        setContentView(root)
    }

    private fun hasPermissions(): Boolean =
        checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
        checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    private fun refreshSims() {
        if (!hasPermissions()) {
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE), REQ_PERMS)
            statusText.text = "يرجى منح صلاحيات SMS وقراءة حالة الشرائح"
            return
        }
        try {
            val manager = getSystemService(SubscriptionManager::class.java)
            sims.clear()
            sims.addAll(manager.activeSubscriptionInfoList ?: emptyList())
            val names = sims.map { info ->
                val slot = "SIM ${info.simSlotIndex + 1}"
                val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() } ?: "شبكة غير معروفة"
                "$slot — $carrier"
            }
            simSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
                if (names.isEmpty()) listOf("لا توجد شريحة نشطة") else names)
            statusText.text = if (sims.isEmpty()) "لم يتم العثور على شريحة نشطة" else "تم اكتشاف ${sims.size} شريحة نشطة"
        } catch (e: SecurityException) {
            statusText.text = "لم يسمح Android بقراءة الشرائح: ${e.message}"
        }
    }

    private fun sendTestSms() {
        if (!hasPermissions()) {
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE), REQ_PERMS)
            return
        }
        val destination = phoneInput.text.toString().trim()
        val body = messageInput.text.toString().trim()
        if (destination.isBlank() || body.isBlank()) {
            statusText.text = "أدخل رقم الهاتف ونص الرسالة أولاً"; return
        }
        if (sims.isEmpty() || simSpinner.selectedItemPosition !in sims.indices) {
            statusText.text = "لا توجد شريحة نشطة للإرسال"; return
        }
        try {
            val subId = sims[simSpinner.selectedItemPosition].subscriptionId
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subId)
            }
            val parts = sms.divideMessage(body)
            val sent = ArrayList<PendingIntent>()
            val delivered = ArrayList<PendingIntent>()
            parts.forEachIndexed { index, _ ->
                val sentIntent = Intent(this, SmsStatusReceiver::class.java).setAction(ACTION_SENT)
                val deliveredIntent = Intent(this, SmsStatusReceiver::class.java).setAction(ACTION_DELIVERED)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
                sent.add(PendingIntent.getBroadcast(this, 1000 + index, sentIntent, flags))
                delivered.add(PendingIntent.getBroadcast(this, 2000 + index, deliveredIntent, flags))
            }
            if (parts.size == 1) sms.sendTextMessage(destination, null, body, sent[0], delivered[0])
            else sms.sendMultipartTextMessage(destination, null, parts, sent, delivered)
            statusText.text = "تم تسليم طلب الإرسال إلى Android؛ ننتظر تقرير الشبكة..."
        } catch (e: Exception) {
            statusText.text = "تعذر إرسال الرسالة: ${e.message}"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) {
            if (hasPermissions()) refreshSims()
            else statusText.text = "يجب منح صلاحيات SMS وقراءة الشرائح لتجربة الإرسال"
        }
    }

    override fun onDestroy() {
        unregisterReceiver(statusReceiver)
        super.onDestroy()
    }
}
