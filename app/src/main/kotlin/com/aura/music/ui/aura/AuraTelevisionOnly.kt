/**
 * Aura Music — the television guard.
 *
 * The manifest asks Android to keep this APK off phones, but filtering is only half the job: a
 * manual sideload ignores it, so the device class is re-checked at startup and this screen is the
 * only thing a non-television device ever gets — no nav graph, no player, no shell.
 */

package com.aura.music.ui.aura

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.music.R
import timber.log.Timber

/**
 * Television-class means leanback, or no touchscreen at all. The second half matters: a box that
 * reports neither feature faithfully is still a television, whereas a phone reports both a screen
 * it can touch and no leanback. Getting this wrong in the other direction bricks the shipped app.
 */
fun auraIsTelevisionClass(context: Context): Boolean {
    val features = context.packageManager
    val leanback = features.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val touchscreen = features.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    Timber.d("tvGuard: leanback=$leanback touchscreen=$touchscreen")
    return leanback || !touchscreen
}

@Composable
fun AuraTelevisionOnly() {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(AuraMock.Page)
            .padding(48.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(percent = 27))
                .background(AuraMock.Primary),
        ) {
            Icon(
                painter = painterResource(R.drawable.aura_ic_logo_note),
                contentDescription = null,
                tint = AuraMock.OnPrimary,
                modifier = Modifier.size(88.dp),
            )
        }
        Text(
            text = stringResource(R.string.aura_tv_only_title),
            color = AuraMock.Text,
            fontSize = 30.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 40.dp),
        )
        Text(
            text = stringResource(R.string.aura_tv_only_body),
            color = AuraMock.Muted,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}
