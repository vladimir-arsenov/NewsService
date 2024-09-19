package client

import dto.News
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import logger


class NewsClient {

    private val url = "https://kudago.com/public-api/v1.4/news/"

    suspend fun getNews(count: Int = 100): List<News> {
        logger.info { "Getting news from API" }

        val client = HttpClient(CIO)
        val news = mutableListOf<News>()
        var page = 0

        try {
            while (page * count.coerceAtMost(100) < count) {
                page++
                val response: HttpResponse = client.get(url) {
                    parameters {
                        parameter(
                            "fields",
                            "id,title,place,description,site_url,favorites_count,comments_count,publication_date"
                        )
                        parameter("location", "msk")
                        parameter("page", "$page")
                        parameter("page_size", "${count.coerceAtMost(100)}")
                        parameter("order_by", "publication_date")
                    }
                }
                val responseBody: String = response.body()
                parseResponseIntoNewsList(responseBody, news)
            }
        } catch (e: Exception) {
            logger.error { "Error getting news from API" }
            throw RuntimeException(e)
        } finally {
            client.close()
        }
        return news
    }

    private fun parseResponseIntoNewsList(jsonString: String, list: MutableList<News>) {
        logger.info { "Parsing response to objects" }
        val json = Json { ignoreUnknownKeys = true }
        val response = json.parseToJsonElement(jsonString) as JsonObject
        val newsArray = response["results"] as JsonArray

        newsArray.forEachIndexed { i, _ ->
            var n = newsArray[i]
            val placeValue = n.jsonObject["place"]
            if (placeValue !is JsonNull) {
                n = JsonObject(n.jsonObject + ("place" to placeValue!!.jsonObject["id"]!!.jsonPrimitive))
            }
            list.add(json.decodeFromJsonElement<News>(n))
        }
    }
}
