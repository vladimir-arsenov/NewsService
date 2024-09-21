package service

import dto.News
import logger
import org.apache.commons.csv.CSVFormat
import java.io.File
import java.io.FileNotFoundException
import java.io.FileWriter
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class NewsService {

    fun getMostRatedNews(newsList: List<News>, count: Int, period: ClosedRange<LocalDate>): List<News> {
        return newsList.asSequence()
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
}