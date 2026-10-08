package me.lucky.silence.text

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.TelephonyManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import me.lucky.silence.AllowNumber
import me.lucky.silence.AppDatabase
import me.lucky.silence.Message
import me.lucky.silence.Preferences
import java.util.concurrent.TimeUnit

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val prefs = Preferences(context ?: return)
        if (!prefs.messages.has(Message.SMS)) return
        Thread(Runner(context, intent, prefs, goAsync())).start()
    }

    private class Runner(
        private val ctx: Context,
        private val intent: Intent,
        private val prefs: Preferences,
        private val pendingResult: PendingResult,
    ) : Runnable {
        private val phoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }
        private val telephonyManager by lazy { ctx.getSystemService(TelephonyManager::class.java) }

        override fun run() {
            val countryCode by lazy {
                telephonyManager?.networkCountryIso?.uppercase()
            }
            val db by lazy { AppDatabase.getInstance(ctx).allowNumberDao() }
            var hasNumber = false
            for (msg in Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return) {
                if (
                    msg.isStatusReportMessage ||
                    msg.isCphsMwiMessage ||
                    msg.isMWIClearMessage ||
                    msg.isMWISetMessage ||
                    msg.isMwiDontStore ||
                    msg.isReplace ||
                    !isTrustedSender(msg.originatingAddress, countryCode) ||
                    (
                        msg.messageClass != SmsMessage.MessageClass.CLASS_1
                        && msg.messageClass != SmsMessage.MessageClass.UNKNOWN
                    )
                ) continue
                for (number in phoneNumberUtil
                    .findNumbers(msg.messageBody, countryCode)
                    .asSequence()
                    .map { it.number() }
                    .filter {
                        phoneNumberUtil.getNumberType(it) == PhoneNumberUtil.PhoneNumberType.MOBILE
                    }
                    .map { AllowNumber.new(it, prefs.messagesTtl) }
                ) {
                    try {
                        db.insert(number)
                    } catch (_: SQLiteConstraintException) {
                        db.update(number)
                    }
                    hasNumber = true
                }
            }
            if (hasNumber) scheduleCleanup(ctx, prefs)
            pendingResult.finish()
        }

        // an unknown caller could otherwise text their own number and then call
        private fun isTrustedSender(address: String?, countryCode: String?): Boolean {
            address ?: return false
            val isPhoneNumber = try {
                phoneNumberUtil.isValidNumber(phoneNumberUtil.parse(address, countryCode))
            } catch (_: NumberParseException) { false }
            // sender ids and short codes belong to services
            return !isPhoneNumber || isContact(address)
        }

        private fun isContact(address: String): Boolean {
            val cursor: Cursor?
            try {
                cursor = ctx.contentResolver.query(
                    Uri.withAppendedPath(
                        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                        Uri.encode(address),
                    ),
                    arrayOf(ContactsContract.PhoneLookup._ID),
                    null,
                    null,
                    null,
                )
            } catch (_: SecurityException) { return false }
            var result = false
            cursor?.apply {
                if (moveToFirst()) result = true
                close()
            }
            return result
        }

        private fun scheduleCleanup(ctx: Context, prefs: Preferences) =
            WorkManager
                .getInstance(ctx)
                .enqueue(OneTimeWorkRequestBuilder<CleanupWorker>()
                    .setInitialDelay(prefs.messagesTtl.toLong() + 5, TimeUnit.MINUTES)
                    .build())
    }
}