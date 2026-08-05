package com.yeex.dlof.ui.components

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yeex.dlof.R

/**
 * "Tek" when not following -> tapping calls [onToggle] and the caller flips
 * [isFollowing]; label then switches to "Teker" (i.e. now following).
 */
@Composable
fun TekButton(isFollowing: Boolean, onToggle: () -> Unit) {
    if (isFollowing) {
        OutlinedButton(onClick = onToggle) { Text(stringResource(R.string.action_teker)) }
    } else {
        Button(
            onClick = onToggle,
            colors = ButtonDefaults.buttonColors()
        ) { Text(stringResource(R.string.action_tek)) }
    }
}
