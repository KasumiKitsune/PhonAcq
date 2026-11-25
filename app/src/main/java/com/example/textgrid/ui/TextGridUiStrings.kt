package com.example.textgrid.ui

import com.example.i18n.S
import com.example.i18n.TextGridVisualStrings
import com.example.textgrid.model.TextGridOpenMode
import com.example.textgrid.model.TextGridScaleMode
import com.example.textgrid.model.TextGridScoreTheme
import com.example.textgrid.model.TextGridTickPrecision
import com.example.textgrid.model.TextGridWrapMode

/**
 * Convenience accessor bridging `com.example.textgrid.ui` to [S.textGridVisual].
 */
object TextGridUiStrings {
    val current: TextGridVisualStrings
        get() = S.textGrid.visual

    fun labelForOpenMode(mode: TextGridOpenMode): String = when (mode) {
        TextGridOpenMode.VISUAL -> current.optOpenVisual
        TextGridOpenMode.SOURCE -> current.optOpenSource
    }

    fun labelForScaleMode(mode: TextGridScaleMode): String = when (mode) {
        TextGridScaleMode.ELASTIC -> current.optScaleElastic
        TextGridScaleMode.LINEAR -> current.optScaleLinear
    }

    fun labelForWrapMode(mode: TextGridWrapMode): String = when (mode) {
        TextGridWrapMode.AUTO -> current.optWrapAuto
        TextGridWrapMode.SINGLE -> current.optWrapSingle
    }

    fun labelForTickPrecision(precision: TextGridTickPrecision): String = when (precision) {
        TextGridTickPrecision.P2 -> "2"
        TextGridTickPrecision.P3 -> current.optTickP3
        TextGridTickPrecision.P4 -> current.optTickP4
        TextGridTickPrecision.FULL -> "Full"
        TextGridTickPrecision.NONE -> current.optTickNone
    }

    fun labelForScoreTheme(theme: TextGridScoreTheme): String = when (theme) {
        TextGridScoreTheme.FOLLOW_APP -> current.optThemeFollowApp
        TextGridScoreTheme.WARM_PAPER -> current.optThemeWarmPaper
        TextGridScoreTheme.ACADEMIC_BW -> current.optThemeAcademicBw
    }
}
