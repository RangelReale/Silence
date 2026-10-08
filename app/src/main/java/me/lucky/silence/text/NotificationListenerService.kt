package me.lucky.silence.text

import android.app.Notification
import android.database.sqlite.SQLiteConstraintException
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import com.google.i18n.phonenumbers.PhoneNumberUtil
import me.lucky.silence.AllowNumber
import me.lucky.silence.AllowNumberDao
import me.lucky.silence.AppDatabase
import me.lucky.silence.Message
import me.lucky.silence.Preferences

class NotificationListenerService : NotificationListenerService() {
    companion object {
        private const val TELECOM_PACKAGE = "com.android.server.telecom"
    }

    private val phoneNumberUtil = PhoneNumberUtil.getInstance()
    private lateinit var prefs: Preferences
    private lateinit var db: AllowNumberDao
    private var telephonyManager: TelephonyManager? = null

    override fun onCreate() {
        super.onCreate()
        init()
    }

    private fun init() {
        prefs = Preferences(this)
        db = AppDatabase.getInstance(this).allowNumberDao()
        telephonyManager = getSystemService(TelephonyManager::class.java)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null
            || !prefs.messages.has(Message.NOTIFICATION)
            || isCallNotification(sbn)) return
        var hasNumber = false
        for (number in phoneNumberUtil
            .findNumbers(
                sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    ?: return,
                telephonyManager?.networkCountryIso?.uppercase(),
            )
            .asSequence()
            .map { it.number() }
            .filter { phoneNumberUtil.getNumberType(it) == PhoneNumberUtil.PhoneNumberType.MOBILE }
            .map { AllowNumber.new(it, prefs.messagesTtl) }
        ) {
            try { db.insert(number) } catch (_: SQLiteConstraintException) { db.update(number) }
            hasNumber = true
        }
        if (hasNumber) {
            // the job is postponed by each new number, so drop expired ones here too
            db.deleteExpired()
            CleanupWorker.schedule(this, prefs.messagesTtl)
        }
    }

    // a blocked or missed call shows its own number, which would allow the next call from it
    private fun isCallNotification(sbn: StatusBarNotification) =
        sbn.packageName == packageName ||
            sbn.packageName == TELECOM_PACKAGE ||
            sbn.packageName == getSystemService(TelecomManager::class.java)?.defaultDialerPackage ||
            sbn.notification.category == Notification.CATEGORY_CALL ||
            sbn.notification.category == Notification.CATEGORY_MISSED_CALL

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            migrateNotificationFilter(0, null)
    }
}
