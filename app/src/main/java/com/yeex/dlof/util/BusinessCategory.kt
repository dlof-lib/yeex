package com.yeex.dlof.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yeex.dlof.R

/**
 * Fixed set of "نوع النشاط" categories for a business account
 * ([com.yeex.dlof.data.model.User.businessCategory]) — mirrors the "page
 * category" concept on major platforms (Meta/X/etc: public figure, company,
 * store, media/TV channel...).
 *
 * [TV_CHANNEL] intentionally shares its raw string with
 * [RoomType.TV_CHANNEL] — a TV-channel business account and a TV-channel
 * room are the same real-world concept at two different levels (account vs.
 * room), so a TV channel operator picks this category for their profile and
 * also gets [RoomType.TV_CHANNEL] available when creating their room.
 */
object BusinessCategory {
    const val PUBLIC_FIGURE = "PUBLIC_FIGURE"
    const val COMPANY = "COMPANY"
    const val STORE = "STORE"
    const val TV_CHANNEL = "TV_CHANNEL"
    const val MEDIA = "MEDIA"
    const val OTHER = "OTHER"

    val ALL = listOf(PUBLIC_FIGURE, COMPANY, STORE, TV_CHANNEL, MEDIA, OTHER)

    @Composable
    fun label(category: String): String = when (category) {
        PUBLIC_FIGURE -> stringResource(R.string.business_category_public_figure)
        COMPANY -> stringResource(R.string.business_category_company)
        STORE -> stringResource(R.string.business_category_store)
        TV_CHANNEL -> stringResource(R.string.business_category_tv_channel)
        MEDIA -> stringResource(R.string.business_category_media)
        else -> stringResource(R.string.business_category_other)
    }
}

/** "GENERAL" | "TV_CHANNEL" — see [com.yeex.dlof.data.model.Room.roomType]. */
object RoomType {
    const val GENERAL = "GENERAL"
    const val TV_CHANNEL = "TV_CHANNEL"
}
