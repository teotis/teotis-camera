package com.opencamera.core.effect

/** Shared semantic and normalized visual contract for the Philosophy Like Water watermark. */
object TravelTicketPaperSpec {
    const val TEMPLATE_ID = "travel-polaroid"
    const val TITLE = "哲思如水"
    const val SUBTITLE = "静水流深 · 万物有声"
    const val PLACEHOLDER_TRIP = SUBTITLE
    const val PLACEHOLDER_DATE = "此刻留白"

    const val PAPER_COLOR = 0xFFF7F3E9.toInt()
    const val BLACK_COLOR = 0xFF101010.toInt()
    const val COBALT_COLOR = 0xFF1257D6.toInt()
    const val CORAL_COLOR = 0xFFFF6552.toInt()
    const val LIME_COLOR = 0xFFB8EB15.toInt()

    // 1080 px formal master: 960 px photo opening, 60 px side border, 300 px band.
    const val SIDE_BORDER_RATIO = 0.0625f
    const val BOTTOM_BAND_RATIO = 0.3125f
    const val TICKET_LEFT_RATIO = 0.566667f
    const val TICKET_TOP_RATIO = 0.1f
    const val TICKET_BOTTOM_RATIO = 0.833333f
    const val LIME_STUB_RATIO = 0.156863f

    fun supportingLines(
        location: String?,
        profileName: String?,
        datetime: String?,
        model: String?
    ): List<String> {
        val contextLine = listOfNotNull(
            location?.trim()?.takeIf(String::isNotBlank),
            profileName?.trim()?.takeIf(String::isNotBlank),
            formatDate(datetime)
        ).joinToString(separator = " · ")
            .ifBlank { model?.trim().orEmpty() }
            .takeIf(String::isNotBlank)
        return listOfNotNull(SUBTITLE, contextLine)
    }

    fun formatDate(datetime: String?): String? {
        val value = datetime?.trim().orEmpty()
        val match = Regex("""(\d{4})[-:./](\d{2})[-:./](\d{2})""").find(value)
            ?: return value.ifBlank { null }
        return "${match.groupValues[1]}.${match.groupValues[2]}.${match.groupValues[3]}"
    }

    fun serial(lines: List<String>): String {
        return lines.asSequence()
            .mapNotNull { line -> Regex("""\d{4}[.\-/](\d{2})[.\-/](\d{2})""").find(line) }
            .firstOrNull()
            ?.let { match -> match.groupValues[1] + match.groupValues[2] }
            ?: "TRIP"
    }
}
