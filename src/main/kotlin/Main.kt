import client.NewsClient
import mu.KotlinLogging
import service.NewsService
import java.time.LocalDate
import java.time.Month

val logger = KotlinLogging.logger {}


suspend fun main() {
    val service = NewsService()
    val news = NewsClient().getNews(300)
    val mostRatedNews = service.getMostRatedNews(news, 50,
        LocalDate.of(2010, Month.NOVEMBER, 7)
                ..LocalDate.of(2016, Month.NOVEMBER, 27)
        )
   service.saveNews("news.csv", mostRatedNews)
}