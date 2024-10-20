package service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logger
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertTrue

class AsynchronousNewsServiceTest {


    @Test
    fun semaphoreShouldBlockThreads() = runBlocking {
        val delay = 100L
        val permits = 2
        val time = measureTimeMillis {
            val semaphore = Semaphore(permits)
            newsWorkers(semaphore, permits * 3, delay).join()
        }
        assertTrue { time >=  delay * 3 }
    }

    private fun CoroutineScope.newsWorkers(semaphore: Semaphore, workersCount: Int, delay: Long) = launch {
        val coroutineDispatcher = newFixedThreadPoolContext(workersCount, "News Test Scope")
        for (workerId in 1..workersCount) {
            launch(coroutineDispatcher) {
                semaphore.withPermit {
                    logger.info { "Worker #$workerId is working" }
                    delay(delay)
                    logger.info { "Worker #$workerId finished it's work" }
                }
            }
        }
    }

}