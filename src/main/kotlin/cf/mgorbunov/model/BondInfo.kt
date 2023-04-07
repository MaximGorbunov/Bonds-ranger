package cf.mgorbunov.model

data class BondInfo(
    val name: String,
    val profitPercent: Double,
    val price: Double,
    val figi: String,
    val ticker: String,
    val isin: String
) {
    constructor() : this("", .0, .0, "", "", "")
}