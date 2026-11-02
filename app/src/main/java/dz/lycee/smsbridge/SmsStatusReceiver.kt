package dz.lycee.smsbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ok = resultCode == android.app.Activity.RESULT_OK
        val action = if (intent.action == MainActivity.ACTION_SENT) "sent" else "delivered"
        val result = if (ok) "نجاح" else "فشل (رمز $resultCode)"
        context.getSharedPreferences("bridge", Context.MODE_PRIVATE)
            .edit().putString("last_result", "$action: $result").apply()
        val update = Intent(MainActivity.ACTION_STATUS).setPackage(context.packageName)
        update.putExtra("result", "$action: $result")
        context.sendBroadcast(update)
    }
}
