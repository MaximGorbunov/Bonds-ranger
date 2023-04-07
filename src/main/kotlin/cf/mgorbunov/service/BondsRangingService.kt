package cf.mgorbunov.service

import cf.mgorbunov.model.BondInfo
import cf.mgorbunov.utils.convertToDouble
import kotlinx.coroutines.flow.*
import ru.tinkoff.piapi.contract.v1.Bond
import ru.tinkoff.piapi.contract.v1.RiskLevel
import java.time.LocalDateTime
import java.time.ZoneOffset

class BondsRangingService(
    private val tinkoffService: TinkoffService
) {

    suspend fun rangeBonds(
        desiredRiskLevels: List<RiskLevel>,
        sellDate: LocalDateTime,
        currency: String,
        ruoniaRate: Double
    ): List<BondInfo> {
        val bondsList = tinkoffService.getBondsList().filter {
            !it.amortizationFlag
                    && !it.blockedTcaFlag
                    && it.buyAvailableFlag
                    && it.sellAvailableFlag
                    && !it.forQualInvestorFlag
                    && currency == it.nominal.currency
                    && desiredRiskLevels.contains(it.riskLevel)
        }
        return bondsList.asFlow().flatMapMerge {
            flow {
                val nominal = it.nominal.convertToDouble()
                val bondPrice = tinkoffService.getBondPrice(it.figi, nominal)
                if (bondPrice == 0.0) {
                    emit(null)
                    return@flow
                }
                val currentAccumulatedCouponIncome = it.aciValue.convertToDouble()
                val fullBondPrice = bondPrice + currentAccumulatedCouponIncome
                var couponsIncome = getCouponsIncome(
                    figi = it.figi,
                    currentAccumulatedCouponIncome = currentAccumulatedCouponIncome,
                    russiaOFZ = it.name.startsWith("ОФЗ"),
                    sellDate = sellDate,
                    ruoniaRate = calculateRuoniaRate(ruoniaRate, it),
                    nominal = nominal
                )
                couponsIncome -= calculateTax(couponsIncome, currentAccumulatedCouponIncome)
                val incomePercent = ((bondPrice + couponsIncome) - fullBondPrice) * 100.0 / fullBondPrice
                emit(
                    BondInfo(
                        name = it.name,
                        profitPercent = incomePercent,
                        price = fullBondPrice,
                        figi = it.figi,
                        ticker = it.ticker,
                        isin = it.isin
                    )
                )
            }
        }.filterNotNull().toList().sortedByDescending { it.profitPercent }
    }

    private fun calculateTax(couponsIncome: Double, currentAccumulatedCouponIncome: Double): Double {
        return (couponsIncome - currentAccumulatedCouponIncome) * TAX_RATE
    }

    private fun calculateRuoniaRate(ruoniaRate: Double, it: Bond): Double {
        val additionToRuonia = (mapPrem[it.name] ?: 0.0) / 100.0
        return (ruoniaRate + additionToRuonia) / it.couponQuantityPerYear
    }

    private suspend fun getCouponsIncome(
        figi: String,
        currentAccumulatedCouponIncome: Double,
        russiaOFZ: Boolean,
        sellDate: LocalDateTime,
        ruoniaRate: Double,
        nominal: Double
    ): Double { // Сумма дохода по купонам
        val currentDateSeconds = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
        val coupons =
            tinkoffService.getCouponsForBond(figi, sellDate) // Содержит выплаченные купоны за год от текущей даты,
        // а также следующие купоны за год от момента ожидаемой продажи
        // ! Для расчета дохода от купонов необходимо отфильтровать купоны за период владения, а также посчитать накопленный купонный доход
        val sellDateInSeconds = sellDate.toEpochSecond(ZoneOffset.UTC)
        val ownPeriodCoupons = coupons.filter { // Купоны которые будут выплачены за период владения
            it.fixDate.seconds in (currentDateSeconds + 1) until sellDateInSeconds
        }
        val daysUntilSell = ((sellDateInSeconds - currentDateSeconds) / SECONDS_IN_DAY).toInt()
        val accumulatedCouponIncome =
            if (ownPeriodCoupons.isEmpty()) { // Если не будет выплат по купонам за период владения
                val nextCoupon = coupons.filter {
                    it.fixDate.seconds > currentDateSeconds
                }.minByOrNull { it.fixDate.seconds }
                    ?: return 0.0 // Не должно быть null, ожидается хоть будующая одна выплата по купонам
                val daysUntilNextCoupon = (nextCoupon.fixDate.seconds - currentDateSeconds) / SECONDS_IN_DAY
                val couponValue =
                    getCouponValue(nextCoupon.payOneBond.convertToDouble(), russiaOFZ, nominal, ruoniaRate)
                val incomePerDay =
                    (couponValue - currentAccumulatedCouponIncome) / daysUntilNextCoupon // Вычитаем из купонного дохода текущий накполенный и делим на количество оставшихся дней чтоб получить доход с купона в день
                currentAccumulatedCouponIncome + (incomePerDay * daysUntilSell)
            } else { // Если  будут выплаты по купонам за период владения
                val lastPayedCoupon =
                    ownPeriodCoupons.maxByOrNull { it.couponEndDate.seconds }!! // не может быть null тк будет хоть одна выплата
                val nextCoupon = coupons.filter {
                    it.couponEndDate.seconds > lastPayedCoupon.couponEndDate.seconds && it.couponStartDate.seconds < sellDateInSeconds
                }
                    .minByOrNull { it.fixDate.seconds } // Можеть быть null тк облигация может быть погашена в период владения
                if (nextCoupon != null) {
                    val daysUntilSellFromCouponStart =
                        ((sellDateInSeconds - nextCoupon.couponStartDate.seconds) / SECONDS_IN_DAY).toInt()
                    val daysUntilNextCoupon =
                        (nextCoupon.couponEndDate.seconds - nextCoupon.couponStartDate.seconds) / SECONDS_IN_DAY
                    val couponValue =
                        getCouponValue(nextCoupon.payOneBond.convertToDouble(), russiaOFZ, nominal, ruoniaRate)
                    val incomePerDay =
                        couponValue / daysUntilNextCoupon // Величину купона делим на количество оставшихся дней чтоб получить доход с купона в день
                    incomePerDay * daysUntilSellFromCouponStart
                } else {
                    0.0
                }
            }
        val couponsSum = ownPeriodCoupons.sumOf {
            var couponValue = it.payOneBond.convertToDouble()
            if (couponValue == 0.0 && russiaOFZ) {
                couponValue = nominal * ruoniaRate
            }
            getCouponValue(it.payOneBond.convertToDouble(), russiaOFZ, nominal, ruoniaRate)
            couponValue
        }
        return couponsSum + accumulatedCouponIncome
    }

    private fun getCouponValue(
        value: Double,
        russiaOFZ: Boolean,
        nominal: Double,
        ruoniaRate: Double
    ): Double {
        if (value == 0.0 && russiaOFZ) {
            return nominal * ruoniaRate
        }
        return value
    }


    companion object {
        const val SECONDS_IN_DAY = 86400.0
        private const val TAX_RATE = 0.13


        // Список ОФЗ с плавающей купонной ставкой, с премией к ставке RUONIA
        val mapPrem = mapOf<String, Double>(
            "ОФЗ 29006" to 1.2,
            "ОФЗ 29007" to 1.3,
            "ОФЗ 29008" to 1.4,
            "ОФЗ 29009" to 1.5,
            "ОФЗ 29010" to 1.6,
            "ОФЗ 29012" to 0.4,
        )
    }
}