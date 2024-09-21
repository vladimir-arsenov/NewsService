package dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlin.math.exp

@Serializable
data class News @OptIn(ExperimentalSerializationApi::class) constructor(
    val id: Long,
    val title: String,
    val place: Int?,
    val description: String,
    @JsonNames("site_url") val siteUrl: String,
    @JsonNames("favorites_count") val favoritesCount: Int,
    @JsonNames("comments_count") val commentsCount: Int,
    @JsonNames("publication_date") val publicationDate: Long
) {
    val rating: Float by lazy {
        1 / (1 + exp((-(favoritesCount / (commentsCount + 1))).toFloat()))
    }
}