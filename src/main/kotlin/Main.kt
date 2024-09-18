import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import mu.KotlinLogging
import org.apache.commons.csv.CSVFormat
import java.io.File
import java.io.FileNotFoundException
import java.io.FileWriter
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId

private val logger = KotlinLogging.logger {}
private const val URL = "https://kudago.com/public-api/v1.4/news/"


suspend fun main() {
    val news = getNews(300)
        .getMostRatedNews(50,
            LocalDate.of(2010, Month.NOVEMBER, 7)
                    ..LocalDate.of(2016, Month.NOVEMBER, 27))

    saveNews("news.csv", news)
}

// Задание #2
suspend fun getNews(count: Int = 100): List<News> {
    logger.info { "Getting news from API" }

    val client = HttpClient(CIO)
    val news = mutableListOf<News>()
    var page = 0

    try {
        while (page * count.coerceAtMost(100) < count) {
            page++
            val response: HttpResponse = client.get(URL) {
                parameters {
                    parameter("fields",
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



// Задание #3
fun List<News>.getMostRatedNews(count: Int, period: ClosedRange<LocalDate>): List<News> {
    logger.info { "Getting most rated news" }

    return this.asSequence()
        .filter {
            val localDate = Instant.ofEpochSecond(it.publicationDate)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

            localDate in period
        }
        .sortedByDescending { it.rating }
        .take(count)
        .toList()
}

/**
 * Note: попробуйте выполнить данное задание с помощью списков и циклов
*/
//fun List<News>.getMostRatedNews(count: Int, period: ClosedRange<LocalDate>): List<News> {
//    val filteredNews = mutableListOf<News>()
//    for (news in this) {
//        val localDate = Instant.ofEpochSecond(news.publicationDate)
//            .atZone(ZoneId.systemDefault())
//            .toLocalDate()
//
//        if (localDate in period) {
//            filteredNews.add(news)
//        }
//    }
//
//    filteredNews.sortedByDescending { it.rating }
//
//    val result = mutableListOf<News>()
//    for (i in 0 until minOf(count, filteredNews.size)) {
//        result.add(filteredNews[i])
//    }
//
//    return result
//}

// Задание #4
fun saveNews(path: String, news: Collection<News>) {
    logger.info { "Saving news to CSV file: $path" }
    val file = File(path)

    if (file.exists()) {
        throw FileAlreadyExistsException("File $path already exists")
    }
    if (file.parentFile != null && !file.parentFile.exists() && !file.parentFile.mkdirs()) {
        throw FileNotFoundException("Incorrect path")
    }

    try {
        FileWriter(file).use { writer ->
            CSVFormat.DEFAULT.print(writer).apply {
                printRecord(
                    "Id", "Title", "Place", "Description", "Site Url",
                    "Rating", "Favorites Count", "Comments Count", "Publication Date"
                )
                news.forEach {
                    printRecord(
                        it.id, it.title, it.place, it.description, it.siteUrl,
                        it.rating, it.favoritesCount, it.commentsCount, it.publicationDate
                    )
                }
            }
        }
    } catch (e: IOException) {
        logger.error { "Error writing CSV file: $path" }
    }
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