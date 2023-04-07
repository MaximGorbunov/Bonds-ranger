package cf.mgorbunov.model

enum class RiskLevel {
    LOW, MODERATE, HIGH
}

fun RiskLevel.convertToProto() = when (this) {
    RiskLevel.LOW -> ru.tinkoff.piapi.contract.v1.RiskLevel.RISK_LEVEL_LOW
    RiskLevel.MODERATE -> ru.tinkoff.piapi.contract.v1.RiskLevel.RISK_LEVEL_MODERATE
    RiskLevel.HIGH -> ru.tinkoff.piapi.contract.v1.RiskLevel.RISK_LEVEL_HIGH
}