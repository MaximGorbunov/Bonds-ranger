package cf.mgorbunov.model

import ru.tinkoff.piapi.contract.v1.RiskLevel
import java.time.LocalDateTime

data class Arguments(
    val period: Long,
    val currency: String,
    val riskLevels: List<RiskLevel>,
    val ruoniaRate: Double,
    val token: String,
    val sellDate: LocalDateTime,
)
