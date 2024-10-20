package service

import dto.News
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import logger
import org.apache.commons.csv.CSVFormat
import java.io.File
import java.io.FileNotFoundException
import java.io.FileWriter
import java.io.IOException
import kotlin.math.ceil
import kotlin.system.measureTimeMillis

fun main() {
    AsynchronousNewsService().getNews()
}
class AsynchronousNewsService (
    private val workersCount: Int = 2,
    semaphoreValue: Int = 6,
    private val pageSize: Int = 100
){
    private val url = "https://kudago.com/public-api/v1.4/news/"
    private val client = HttpClient(CIO)
    private val semaphore = Semaphore(semaphoreValue)

    fun getNews() {
        val time = measureTimeMillis {
            runBlocking {
                val channel = Channel<List<News>>()
                val outputPath = "news_async_output.csv"
                val pagesCount = ceil(getTotalNewsCount().toDouble() / pageSize).toInt()
                val processor = newsProcessor(channel, outputPath)
                newsWorkers(pageSize, pagesCount, channel, workersCount).join()
                channel.close()
                processor.join()
            }
            client.close()
        }
        println("Took ${time}ms")
    }

    private fun CoroutineScope.newsWorkers(pageSize: Int, pagesCount: Int, channel: Channel<List<News>>, workersCount: Int) = launch {
        val coroutineDispatcher = newFixedThreadPoolContext(workersCount, "News Fetching Scope")
        for (workerId in 1..workersCount) {
            launch(coroutineDispatcher) {
                semaphore.withPermit {
                    for (page in workerId..pagesCount step workersCount) {
                        logger.info { "Worker #$workerId fetching page $page" }
                        channel.send(getPage(pageSize, page))
                    }
                    logger.info { "Worker #$workerId finished it's work" }
                }
            }
        }
    }

   private fun CoroutineScope.newsProcessor(channel: Channel<List<News>>, path: String) = launch(Dispatchers.IO) {
        logger.info { "Saving news to CSV file: $path" }
        val file = File(path)

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
                     for (newsList in channel){
                        newsList.forEach {
                            printRecord(
                                it.id, it.title, it.place, it.description, it.siteUrl,
                                it.rating, it.favoritesCount, it.commentsCount, it.publicationDate
                            )
                        }
                    }
                }
            }
        } catch (e: IOException) {
            logger.error { "Error writing CSV file: $path" }
        }
        logger.info { "All news has been written to file." }
    }


    private suspend fun getPage(count: Int = 100, page: Int): List<News> {
        logger.info { "Getting news from API" }

        val news = mutableListOf<News>()

        try {
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
            if (response.status.isSuccess()) {
                val responseBody: String = response.body()
                parseResponseIntoNewsList(responseBody, news)
            } else {
                throw Exception("Response status is ${response.status.value}")
            }

        } catch (e: Exception) {
            logger.error ("Error getting news from API: {}", e.message)
        }
        return news
    }

    private suspend fun getTotalNewsCount(): Int  {
        try {
            val response: HttpResponse = client.get(url) {
                parameters {
                    parameter("location", "msk")
                }
            }
            val jsonResponseBody: String = response.body()
            val json = Json { ignoreUnknownKeys = true }
            return (json.parseToJsonElement(jsonResponseBody) as JsonObject)["count"].toString().toInt()
        } catch (e: Exception) {
            throw RuntimeException(e)
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
}