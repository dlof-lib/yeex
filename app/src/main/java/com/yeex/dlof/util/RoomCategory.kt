package com.yeex.dlof.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.yeex.dlof.R

/**
 * Fixed set of "فئة الغرفة" (room category) values — the topical
 * classification a room owner picks in [com.yeex.dlof.ui.room.CreateRoomScreen]
 * so the room can be filtered/discovered by subject in
 * [com.yeex.dlof.ui.room.BrowseRoomsScreen], the same way [BusinessCategory]
 * classifies a business *account*. Kept as a separate enum from
 * [BusinessCategory] on purpose: a room's topic (e.g. "رياضة") and an
 * owner's business type (e.g. "متجر") are independent axes — a store
 * account can easily run a gaming-themed room.
 */
object RoomCategory {
    const val GENERAL = "GENERAL"
    const val GAMING = "GAMING"
    const val MUSIC = "MUSIC"
    const val SPORTS = "SPORTS"
    const val MOVIES_TV = "MOVIES_TV"
    const val NEWS_POLITICS = "NEWS_POLITICS"
    const val EDUCATION = "EDUCATION"
    const val TECHNOLOGY = "TECHNOLOGY"
    const val BUSINESS_FINANCE = "BUSINESS_FINANCE"
    const val ART_DESIGN = "ART_DESIGN"
    const val FASHION_BEAUTY = "FASHION_BEAUTY"
    const val FOOD_DRINK = "FOOD_DRINK"
    const val TRAVEL = "TRAVEL"
    const val HEALTH_FITNESS = "HEALTH_FITNESS"
    const val SCIENCE = "SCIENCE"
    const val BOOKS_LITERATURE = "BOOKS_LITERATURE"
    const val FAMILY_PARENTING = "FAMILY_PARENTING"
    const val COMEDY = "COMEDY"
    const val RELIGION_SPIRITUALITY = "RELIGION_SPIRITUALITY"
    const val AUTOMOTIVE = "AUTOMOTIVE"
    const val PETS_ANIMALS = "PETS_ANIMALS"
    const val RELATIONSHIPS = "RELATIONSHIPS"
    const val OTHER = "OTHER"

    val ALL = listOf(
        GENERAL, GAMING, MUSIC, SPORTS, MOVIES_TV, NEWS_POLITICS,
        EDUCATION, TECHNOLOGY, BUSINESS_FINANCE, ART_DESIGN, FASHION_BEAUTY,
        FOOD_DRINK, TRAVEL, HEALTH_FITNESS, SCIENCE, BOOKS_LITERATURE,
        FAMILY_PARENTING, COMEDY, RELIGION_SPIRITUALITY, AUTOMOTIVE,
        PETS_ANIMALS, RELATIONSHIPS, OTHER
    )

    @Composable
    fun label(category: String): String = when (category) {
        GENERAL -> stringResource(R.string.room_category_general)
        GAMING -> stringResource(R.string.room_category_gaming)
        MUSIC -> stringResource(R.string.room_category_music)
        SPORTS -> stringResource(R.string.room_category_sports)
        MOVIES_TV -> stringResource(R.string.room_category_movies_tv)
        NEWS_POLITICS -> stringResource(R.string.room_category_news_politics)
        EDUCATION -> stringResource(R.string.room_category_education)
        TECHNOLOGY -> stringResource(R.string.room_category_technology)
        BUSINESS_FINANCE -> stringResource(R.string.room_category_business_finance)
        ART_DESIGN -> stringResource(R.string.room_category_art_design)
        FASHION_BEAUTY -> stringResource(R.string.room_category_fashion_beauty)
        FOOD_DRINK -> stringResource(R.string.room_category_food_drink)
        TRAVEL -> stringResource(R.string.room_category_travel)
        HEALTH_FITNESS -> stringResource(R.string.room_category_health_fitness)
        SCIENCE -> stringResource(R.string.room_category_science)
        BOOKS_LITERATURE -> stringResource(R.string.room_category_books_literature)
        FAMILY_PARENTING -> stringResource(R.string.room_category_family_parenting)
        COMEDY -> stringResource(R.string.room_category_comedy)
        RELIGION_SPIRITUALITY -> stringResource(R.string.room_category_religion_spirituality)
        AUTOMOTIVE -> stringResource(R.string.room_category_automotive)
        PETS_ANIMALS -> stringResource(R.string.room_category_pets_animals)
        RELATIONSHIPS -> stringResource(R.string.room_category_relationships)
        else -> stringResource(R.string.room_category_other)
    }

    /** Small glyph shown next to the category label in chips/badges. */
    fun icon(category: String): ImageVector = when (category) {
        GAMING -> Icons.Filled.SportsEsports
        MUSIC -> Icons.Filled.Album
        SPORTS -> Icons.Filled.SportsSoccer
        MOVIES_TV -> Icons.Filled.Movie
        NEWS_POLITICS -> Icons.Filled.Newspaper
        EDUCATION -> Icons.Filled.School
        TECHNOLOGY -> Icons.Filled.Memory
        BUSINESS_FINANCE -> Icons.Filled.AttachMoney
        ART_DESIGN -> Icons.Filled.Palette
        FASHION_BEAUTY -> Icons.Filled.Style
        FOOD_DRINK -> Icons.Filled.LocalCafe
        TRAVEL -> Icons.Filled.Explore
        HEALTH_FITNESS -> Icons.Filled.FitnessCenter
        SCIENCE -> Icons.Filled.Science
        BOOKS_LITERATURE -> Icons.Filled.AutoStories
        FAMILY_PARENTING -> Icons.Filled.FamilyRestroom
        COMEDY -> Icons.Filled.EmojiEmotions
        RELIGION_SPIRITUALITY -> Icons.Filled.SelfImprovement
        AUTOMOTIVE -> Icons.Filled.DirectionsCar
        PETS_ANIMALS -> Icons.Filled.Pets
        RELATIONSHIPS -> Icons.Filled.FavoriteBorder
        OTHER -> Icons.Filled.Grain
        else -> Icons.Filled.Groups
    }
}
