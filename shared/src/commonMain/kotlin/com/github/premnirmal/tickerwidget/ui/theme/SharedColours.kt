package com.github.premnirmal.tickerwidget.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Theme/accent colours shared by the Compose Multiplatform widgets. Hoisting these out of the
 * Android-only `ColourPalette` lets shared widgets (the quote card, total-holdings popup, news card
 * placeholder, …) render the same change/gain/loss colours on Android and iOS. The Android
 * `ColourPalette` delegates its change-colour accessors here so there is a single source of truth.
 *
 * **A-share (mainland-China) convention:** gains print **red** and losses print **green** — the
 * inverse of the Western convention. The names below therefore describe the *meaning* ([UpColour] =
 * 涨/赚, [DownColour] = 跌/亏) rather than the hue, so switching market conventions later is a
 * one-line change here instead of a hunt through every call site.
 */
object SharedColours {
  // Quote/chart change text — a lighter pair that stays readable at large sizes.
  private val LightChangeUp = Color(0xFFEF5350) // red
  private val DarkChangeUp = Color(0xFFEF5350)
  private val LightChangeDown = Color(0xFF66BB6A) // green
  private val DarkChangeDown = Color(0xFF66BB6A)

  // Table cells (quote card, holdings popup) — a deeper pair for small text.
  private val LightUp = Color(0xFFe55b5b) // red
  private val DarkUp = Color(0xFFff6666)
  private val LightDown = Color(0xFF009900) // green
  private val DarkDown = Color(0xFFccff66)

  /** 涨 — red under the A-share convention. */
  val ChangeUpColour: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkChangeUp else LightChangeUp

  /** 跌 — green under the A-share convention. */
  val ChangeDownColour: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkChangeDown else LightChangeDown

  /** 涨/赚 — red under the A-share convention. */
  val UpColour: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkUp else LightUp

  /** 跌/亏 — green under the A-share convention. */
  val DownColour: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkDown else LightDown

  val ImagePlaceHolderGray = Color(0x20a7a7a7)

  /**
   * Resolves the colour used to render a value that moved up/down/unchanged, matching
   * [UpColour]/[DownColour] and falling back to the theme's `onSurfaceVariant`.
   */
  @Composable
  fun changeColour(up: Boolean, down: Boolean): Color = when {
    up -> UpColour
    down -> DownColour
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }
}
