package com.brotimer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * BroTimer's own icons, drawn from SVG path strings.
 *
 * They exist because the only icon set in the offline Gradle cache is `material-icons-core`,
 * which has no alarm clock, stopwatch, hourglass, pause or music note — the old tabs had to make
 * do with a bell, a refresh arrow and a calendar. `material-icons-extended` would need a download
 * (see CLAUDE.md invariant 5). Several shapes follow Feather Icons (MIT, feathericons.com).
 *
 * Colour is irrelevant here: `Icon(tint = ...)` recolours every path.
 */
object AppIcons {

    private fun icon(
        name: String,
        strokes: List<String> = emptyList(),
        fills: List<String> = emptyList(),
        strokeWidth: Float = 2f,
    ): ImageVector {
        val b = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        fills.forEach { d ->
            b.addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
        }
        strokes.forEach { d ->
            b.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val AlarmClock: ImageVector by lazy {
        icon(
            "AlarmClock",
            strokes = listOf(
                "M12 20.5a7 7 0 1 0 0-14a7 7 0 1 0 0 14z",
                "M12 10.5v3l2 1.5",
                "M4 6.5L6.5 4",
                "M20 6.5L17.5 4",
                "M7.5 19L6 20.5",
                "M16.5 19L18 20.5",
            ),
        )
    }

    val Stopwatch: ImageVector by lazy {
        icon(
            "Stopwatch",
            strokes = listOf(
                "M12 21a7 7 0 1 0 0-14a7 7 0 1 0 0 14z",
                "M10 3h4",
                "M12 3v4",
                "M12 14v-3.5",
                "M18 7.5l1.5-1.5",
            ),
        )
    }

    val Hourglass: ImageVector by lazy {
        icon(
            "Hourglass",
            strokes = listOf(
                "M6.5 3h11",
                "M6.5 21h11",
                "M8 3v2.5c0 2.5 4 4 4 6.5c0 2.5-4 4-4 6.5V21",
                "M16 3v2.5c0 2.5-4 4-4 6.5c0 2.5 4 4 4 6.5V21",
            ),
        )
    }

    val Play: ImageVector by lazy { icon("Play", fills = listOf("M8 5.5v13l10.5-6.5z")) }

    val Pause: ImageVector by lazy { icon("Pause", fills = listOf("M7 5h3.5v14H7zM13.5 5H17v14h-3.5z")) }

    val StopSquare: ImageVector by lazy { icon("Stop", fills = listOf("M7 7h10v10H7z")) }

    val Reset: ImageVector by lazy {
        icon("Reset", strokes = listOf("M1 4v6h6", "M3.51 15a9 9 0 1 0 2.13-9.36L1 10"))
    }

    val Moon: ImageVector by lazy {
        icon("Moon", fills = listOf("M21 12.79A9 9 0 1 1 11.21 3A7 7 0 0 0 21 12.79z"))
    }

    val Sun: ImageVector by lazy {
        icon(
            "Sun",
            strokes = listOf(
                "M12 16a4 4 0 1 0 0-8a4 4 0 1 0 0 8z",
                "M12 2v2", "M12 20v2",
                "M4.93 4.93l1.41 1.41", "M17.66 17.66l1.41 1.41",
                "M2 12h2", "M20 12h2",
                "M4.93 19.07l1.41-1.41", "M17.66 6.34l1.41-1.41",
            ),
        )
    }

    val Music: ImageVector by lazy {
        icon(
            "Music",
            strokes = listOf("M9 18V5l12-2v13"),
            fills = listOf(
                "M6 21a3 3 0 1 0 0-6a3 3 0 1 0 0 6z",
                "M18 19a3 3 0 1 0 0-6a3 3 0 1 0 0 6z",
            ),
        )
    }

    val Repeat: ImageVector by lazy {
        icon(
            "Repeat",
            strokes = listOf(
                "M17 1l4 4-4 4",
                "M3 11V9a4 4 0 0 1 4-4h14",
                "M7 23l-4-4 4-4",
                "M21 13v2a4 4 0 0 1-4 4H3",
            ),
        )
    }

    val Volume: ImageVector by lazy {
        icon(
            "Volume",
            strokes = listOf(
                "M11 5L6 9H2v6h4l5 4V5z",
                "M19.07 4.93a10 10 0 0 1 0 14.14",
                "M15.54 8.46a5 5 0 0 1 0 7.07",
            ),
        )
    }

    val FolderPlus: ImageVector by lazy {
        icon(
            "FolderPlus",
            strokes = listOf(
                "M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z",
                "M12 11v6",
                "M9 14h6",
            ),
        )
    }

    val Bell: ImageVector by lazy {
        icon(
            "Bell",
            strokes = listOf(
                "M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9",
                "M13.73 21a2 2 0 0 1-3.46 0",
            ),
        )
    }

    val Trash: ImageVector by lazy {
        icon(
            "Trash",
            strokes = listOf(
                "M3 6h18",
                "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
                "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
                "M10 11v6",
                "M14 11v6",
            ),
        )
    }

    val Check: ImageVector by lazy { icon("Check", strokes = listOf("M20 6L9 17l-5-5"), strokeWidth = 2.5f) }

    val Alert: ImageVector by lazy {
        icon(
            "Alert",
            strokes = listOf(
                "M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z",
                "M12 9v4",
                "M12 17h.01",
            ),
        )
    }

    val ChevronRight: ImageVector by lazy { icon("ChevronRight", strokes = listOf("M9 18l6-6-6-6")) }

    val Plus: ImageVector by lazy { icon("Plus", strokes = listOf("M12 5v14", "M5 12h14"), strokeWidth = 2.5f) }

    val Minus: ImageVector by lazy { icon("Minus", strokes = listOf("M5 12h14"), strokeWidth = 2.5f) }
}
