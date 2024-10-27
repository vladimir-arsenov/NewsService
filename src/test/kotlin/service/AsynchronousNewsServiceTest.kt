import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.junit.jupiter.api.Test
import service.AsynchronousNewsService
import java.io.File
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue


@WireMockTest(httpPort = 8081)
class AsynchronousNewsServiceTest {

    private val service = AsynchronousNewsService()
    private val config = ConfigFactory.load()

    @Test
    fun `rate limiter should limit concurrent requests`() = runBlocking {
        val semaphoreValue = 2
        val activeRequests = mutableListOf<Int>()
        val semaphore = Semaphore(semaphoreValue)

        var i = 0
        val time = measureTimeMillis {
            coroutineScope {
                repeat(semaphoreValue * 5) {
                    launch {
                        semaphore.withPermit {
                            val requestInd = i++
                            activeRequests.add(requestInd)
                            delay(100)
                            activeRequests.remove(requestInd)
                            assertTrue(activeRequests.size <= semaphoreValue)
                        }
                    }
                }
            }
        }

        assertTrue(time >= 500)
    }

    @Test
    fun `getNews should write csv` () {
        service.getNews()
        File(config.getString("newsService.outputPath")).bufferedReader().use { reader ->
            assertTrue(CSVParser(reader, CSVFormat.DEFAULT.builder().setSkipHeaderRecord(true).build()).records.size > 0)
        }
    }


}
