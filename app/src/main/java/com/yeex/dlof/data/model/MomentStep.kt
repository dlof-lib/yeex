package com.yeex.dlof.data.model

/**
 * One stage inside a "لحظة" (YEEX MOMENT) paragraph — see [Paragraph.momentSteps].
 *
 * A Moment is *not* a generic timeline screen; it's a compact paragraph that
 * happens to render as a connected sequence of stages instead of one flat
 * text/image/video, e.g.:
 *
 * ```
 * 08:00 🚗 الانطلاق
 *   ↓
 * 10:30 ☕ الوصول
 * ```
 *
 * [order] is the authoritative sort key (rather than relying on list/array
 * position surviving a Firebase round-trip) so drag-and-drop reordering in
 * the composer is unambiguous once persisted.
 */
data class MomentStep(
    val id: String = "",
    val order: Int = 0,
    val title: String = "",
    val time: String = "",          // free text: "08:00", "12'", a date, etc. — not parsed/validated
    val icon: String = "📍",         // a single emoji, picked from a preset palette or typed freely
    val text: String = "",          // short supporting description
    val imageBase64: String = "",   // "" when this stage has no photo
    val colorHex: String = ""       // "" = default brand color; otherwise "#RRGGBB" status color
)
