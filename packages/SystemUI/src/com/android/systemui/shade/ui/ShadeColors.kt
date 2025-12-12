/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.shade.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.provider.Settings
import com.android.internal.graphics.ColorUtils
import com.android.systemui.res.R

object ShadeColors {
    
    private const val DEFAULT_SHADE_PANEL_ALPHA = 32
    private const val DEFAULT_NOTIFICATION_SCRIM_ALPHA = 14

    private var appContext: Context? = null

    @JvmStatic
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @JvmStatic
    fun Resources.shadePanel(blurSupported: Boolean): Int {
        return if (blurSupported) {
            shadePanelStandard()
        } else {
            shadePanelFallback()
        }
    }

    @JvmStatic
    fun Resources.notificationScrim(blurSupported: Boolean): Int {
        return if (blurSupported) {
            notificationScrimStandard()
        } else {
            notificationScrimFallback()
        }
    }

    private fun Resources.isNightModeActive(): Boolean {
        return (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == 
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun getShadePanelAlpha(): Float {
        val context = appContext ?: return (DEFAULT_SHADE_PANEL_ALPHA / 100f)
        val alphaInt = Settings.System.getInt(
            context.contentResolver,
            Settings.System.SHADE_PANEL_ALPHA,
            DEFAULT_SHADE_PANEL_ALPHA
        )
        return alphaInt / 100f
    }

    private fun getNotificationScrimAlpha(): Float {
        val context = appContext ?: return (DEFAULT_NOTIFICATION_SCRIM_ALPHA / 100f)
        val alphaInt = Settings.System.getInt(
            context.contentResolver,
            Settings.System.NOTIFICATION_SCRIM_ALPHA,
            DEFAULT_NOTIFICATION_SCRIM_ALPHA
        )
        return alphaInt / 100f
    }

    @JvmStatic
    private fun Resources.shadePanelStandard(): Int {
        return if (isNightModeActive()) {
            shadePanelStandardDark()
        } else {
            shadePanelStandardLight()
        }
    }

    private fun Resources.shadePanelStandardLight(): Int {
        val alpha = getShadePanelAlpha()
        val layerAbove = getColor(R.color.shade_panel_fg, null)
        val layerBelow = getColor(R.color.shade_panel_bg, null)
        val layerBelowWithAlpha = applyAlpha(layerBelow, alpha)
        return ColorUtils.compositeColors(layerAbove, layerBelowWithAlpha)
    }

    private fun Resources.shadePanelStandardDark(): Int {
        val alpha = getShadePanelAlpha()
        val layerAbove = getColor(R.color.shade_panel_fg, null)
        val layerBelow = getColor(R.color.shade_panel_bg, null)
        val layerBelowWithAlpha = applyAlpha(layerBelow, alpha)
        return ColorUtils.compositeColors(layerAbove, layerBelowWithAlpha)
    }

    @JvmStatic
    private fun Resources.shadePanelFallback(): Int {
        return ColorUtils.blendARGB(
            getColor(R.color.nt_scrim_behind_1), 
            getColor(R.color.nt_scrim_behind_2), 
            0.5f
        )
    }

    @JvmStatic
    private fun Resources.notificationScrimStandard(): Int {
        return if (isNightModeActive()) {
            notificationScrimStandardDark()
        } else {
            notificationScrimStandardLight()
        }
    }

    private fun Resources.notificationScrimStandardLight(): Int {
        val alpha = getNotificationScrimAlpha()
        val layerAbove = getColor(R.color.notification_scrim_fg, null)
        val layerBelow = getColor(R.color.notification_scrim_bg, null)
        val layerBelowWithAlpha = applyAlpha(layerBelow, alpha)
        return ColorUtils.compositeColors(layerAbove, layerBelowWithAlpha)
    }

    private fun Resources.notificationScrimStandardDark(): Int {
        val alpha = getNotificationScrimAlpha()
        val layerAbove = getColor(R.color.notification_scrim_fg, null)
        val layerBelow = getColor(R.color.notification_scrim_bg, null)
        val layerBelowWithAlpha = applyAlpha(layerBelow, alpha)
        return ColorUtils.compositeColors(layerAbove, layerBelowWithAlpha)
    }

    @JvmStatic
    private fun Resources.notificationScrimFallback(): Int {
        return getColor(R.color.notification_scrim_fallback, null)
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val a = (Color.alpha(color) * alpha).toInt().coerceIn(0, 255)
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.argb(a, r, g, b)
    }
}
