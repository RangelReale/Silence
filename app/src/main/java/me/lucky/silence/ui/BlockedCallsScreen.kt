package me.lucky.silence.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import me.lucky.silence.AppDatabase
import me.lucky.silence.BlockedCall
import me.lucky.silence.R
import me.lucky.silence.ui.common.ClickablePreference
import me.lucky.silence.ui.common.Dimension
import me.lucky.silence.ui.common.Screen
import java.text.DateFormat
import java.util.Date

@Composable
fun BlockedCallsScreen(ctx: Context, onBackPressed: () -> Boolean) {
    val dao = remember { AppDatabase.getInstance(ctx).blockedCallDao() }
    var calls by remember { mutableStateOf(dao.selectRecent()) }
    val dateFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    }
    Screen(title = R.string.notification_channel,
        onBackPressed = onBackPressed,
        content = {
            if (calls.isEmpty()) {
                Text(
                    text = stringResource(R.string.blocked_calls_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(horizontal = Dimension.HORIZONTAL_PADDING)
                        .padding(Dimension.PADDING),
                )
                return@Screen
            }
            ClickablePreference(
                name = stringResource(R.string.blocked_calls_clear),
                description = stringResource(R.string.blocked_calls_clear_description),
                onModuleClick = {
                    dao.deleteAll()
                    calls = emptyList()
                },
            )
            HorizontalDivider()
            calls.forEach { BlockedCallItem(it, dateFormat) }
        })
}

@Composable
private fun BlockedCallItem(call: BlockedCall, dateFormat: DateFormat) {
    var description = dateFormat.format(Date(call.ts * 1000))
    if (call.sim != null) description = "$description (${call.sim})"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimension.HORIZONTAL_PADDING)
            .padding(Dimension.PADDING)
    ) {
        Text(
            text = call.phoneNumber ?: stringResource(R.string.blocked_calls_hidden_number),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Preview
@Composable
fun BlockedCallsScreenPreview() {
    MaterialTheme {
        BlockedCallsScreen(LocalContext.current) { true }
    }
}
