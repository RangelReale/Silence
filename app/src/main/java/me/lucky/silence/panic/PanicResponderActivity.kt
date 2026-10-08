package me.lucky.silence.panic

import android.os.Bundle
import androidx.activity.ComponentActivity
import info.guardianproject.panic.Panic
import info.guardianproject.panic.PanicResponder
import me.lucky.silence.AppDatabase
import me.lucky.silence.Preferences
import me.lucky.silence.Utils

class PanicResponderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // any app can send the trigger, so only the app the user connected may act on it
        if (!Panic.isTriggerIntent(intent) ||
            !PanicResponder.receivedTriggerFromConnectedApp(this)
        ) {
            finishAndRemoveTask()
            return
        }
        Preferences(this).isEnabled = false
        Utils.updateMessagesEnabled(this)
        AppDatabase.getInstance(this).allowNumberDao().deleteAll()
        finishAndRemoveTask()
    }
}