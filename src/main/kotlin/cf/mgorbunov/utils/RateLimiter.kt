package cf.mgorbunov.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration

class RateLimiter(capacity: Int, period: Duration) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val channel = scope.produce {
        while (isActive) {
            repeat(capacity) { send(Unit) }
            delay(period)
        }
    }

    suspend fun acquire() = channel.receive()
}