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
    const val ARTIST = "ARTIST"
    const val MUSICIAN = "MUSICIAN"
    const val PHOTOGRAPHER = "PHOTOGRAPHER"
    const val WRITER = "WRITER"
    const val ATHLETE = "ATHLETE"
    const val EDUCATOR = "EDUCATOR"
    const val HEALTH_PROFESSIONAL = "HEALTH_PROFESSIONAL"
    const val RESTAURANT_CAFE = "RESTAURANT_CAFE"
    const val NONPROFIT = "NONPROFIT"
    const val GAMER = "GAMER"
    const val REAL_ESTATE = "REAL_ESTATE"
    const val TECHNOLOGY = "TECHNOLOGY"
    const val COMMUNITY = "COMMUNITY"
    const val GOVERNMENT = "GOVERNMENT"
    const val FASHION_BEAUTY = "FASHION_BEAUTY"
    const val TRAVEL_TOURISM = "TRAVEL_TOURISM"
    const val AUTOMOTIVE = "AUTOMOTIVE"
    const val FINANCE = "FINANCE"
    const val LEGAL = "LEGAL"
    const val AGRICULTURE = "AGRICULTURE"
    const val CONSTRUCTION = "CONSTRUCTION"
    const val FITNESS_WELLNESS = "FITNESS_WELLNESS"
    const val PET_ANIMAL = "PET_ANIMAL"
    const val EVENT_PLANNING = "EVENT_PLANNING"
    const val RELIGIOUS_ORG = "RELIGIOUS_ORG"
    const val JOURNALIST = "JOURNALIST"
    const val COMEDIAN = "COMEDIAN"
    const val DESIGN_ARCHITECTURE = "DESIGN_ARCHITECTURE"
    const val OTHER = "OTHER"

    val ALL = listOf(
        PUBLIC_FIGURE, COMPANY, STORE, TV_CHANNEL, MEDIA,
        ARTIST, MUSICIAN, PHOTOGRAPHER, WRITER, ATHLETE,
        EDUCATOR, HEALTH_PROFESSIONAL, RESTAURANT_CAFE, NONPROFIT, GAMER,
        REAL_ESTATE, TECHNOLOGY, COMMUNITY, GOVERNMENT,
        FASHION_BEAUTY, TRAVEL_TOURISM, AUTOMOTIVE, FINANCE, LEGAL,
        AGRICULTURE, CONSTRUCTION, FITNESS_WELLNESS, PET_ANIMAL, EVENT_PLANNING,
        RELIGIOUS_ORG, JOURNALIST, COMEDIAN, DESIGN_ARCHITECTURE,
        OTHER
    )

    @Composable
    fun label(category: String): String = when (category) {
        PUBLIC_FIGURE -> stringResource(R.string.business_category_public_figure)
        COMPANY -> stringResource(R.string.business_category_company)
        STORE -> stringResource(R.string.business_category_store)
        TV_CHANNEL -> stringResource(R.string.business_category_tv_channel)
        MEDIA -> stringResource(R.string.business_category_media)
        ARTIST -> stringResource(R.string.business_category_artist)
        MUSICIAN -> stringResource(R.string.business_category_musician)
        PHOTOGRAPHER -> stringResource(R.string.business_category_photographer)
        WRITER -> stringResource(R.string.business_category_writer)
        ATHLETE -> stringResource(R.string.business_category_athlete)
        EDUCATOR -> stringResource(R.string.business_category_educator)
        HEALTH_PROFESSIONAL -> stringResource(R.string.business_category_health_professional)
        RESTAURANT_CAFE -> stringResource(R.string.business_category_restaurant_cafe)
        NONPROFIT -> stringResource(R.string.business_category_nonprofit)
        GAMER -> stringResource(R.string.business_category_gamer)
        REAL_ESTATE -> stringResource(R.string.business_category_real_estate)
        TECHNOLOGY -> stringResource(R.string.business_category_technology)
        COMMUNITY -> stringResource(R.string.business_category_community)
        GOVERNMENT -> stringResource(R.string.business_category_government)
        FASHION_BEAUTY -> stringResource(R.string.business_category_fashion_beauty)
        TRAVEL_TOURISM -> stringResource(R.string.business_category_travel_tourism)
        AUTOMOTIVE -> stringResource(R.string.business_category_automotive)
        FINANCE -> stringResource(R.string.business_category_finance)
        LEGAL -> stringResource(R.string.business_category_legal)
        AGRICULTURE -> stringResource(R.string.business_category_agriculture)
        CONSTRUCTION -> stringResource(R.string.business_category_construction)
        FITNESS_WELLNESS -> stringResource(R.string.business_category_fitness_wellness)
        PET_ANIMAL -> stringResource(R.string.business_category_pet_animal)
        EVENT_PLANNING -> stringResource(R.string.business_category_event_planning)
        RELIGIOUS_ORG -> stringResource(R.string.business_category_religious_org)
        JOURNALIST -> stringResource(R.string.business_category_journalist)
        COMEDIAN -> stringResource(R.string.business_category_comedian)
        DESIGN_ARCHITECTURE -> stringResource(R.string.business_category_design_architecture)
        else -> stringResource(R.string.business_category_other)
    }
}

/** "GENERAL" | "TV_CHANNEL" — see [com.yeex.dlof.data.model.Room.roomType]. */
object RoomType {
    const val GENERAL = "GENERAL"
    const val TV_CHANNEL = "TV_CHANNEL"
}
