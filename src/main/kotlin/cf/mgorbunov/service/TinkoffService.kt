package cf.mgorbunov.service

import cf.mgorbunov.utils.RateLimiter
import com.google.protobuf.Timestamp
import ru.tinkoff.piapi.contract.v1.*
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

private const val NANO = 1_000_000_000.0

class TinkoffService(
    private val instrumentsService: InstrumentsServiceGrpcKt.InstrumentsServiceCoroutineStub,
    private val marketService: MarketDataServiceGrpcKt.MarketDataServiceCoroutineStub,
    private val instrumentServiceRateLimit: RateLimiter,
    private val marketServiceRateLimit: RateLimiter
) {

    suspend fun getBondsList(): MutableList<Bond> {
        instrumentServiceRateLimit.acquire()
        return instrumentsService.bonds(
            InstrumentsRequest.newBuilder()
                .setInstrumentStatus(InstrumentStatus.INSTRUMENT_STATUS_BASE)
                .build()
        ).instrumentsList
    }

    suspend fun getBondPrice(figi: String, nominal: Double): Double {
        marketServiceRateLimit.acquire()
        val marketPricePercentObj = marketService.getLastPrices(
            GetLastPricesRequest.newBuilder()
                .addFigi(figi)
                .build()
        ).getLastPrices(0).price
        val marketPricePercent =
            marketPricePercentObj.units + (marketPricePercentObj.nano / NANO)
        return marketPricePercent / 100.0 * nominal
    }

    suspend fun getCouponsForBond(figi: String, sellDate: LocalDateTime): MutableList<Coupon> {
        instrumentServiceRateLimit.acquire()
        return instrumentsService.getBondCoupons(
            GetBondCouponsRequest.newBuilder()
                .setFigi(figi)
                .setFrom(
                    Timestamp.newBuilder()
                        .setSeconds(
                            LocalDateTime.now().minusYears(1).toEpochSecond(UTC)
                        ) // Вычитаем год чтобы получить прежыдущие купоны
                        .build()
                )
                .setTo(
                    Timestamp.newBuilder()
                        .setSeconds(
                            sellDate.plusYears(1).toEpochSecond(UTC)
                        ) // Добавляем год чтобы получить следующий купон
                        .build()
                )
                .build()
        ).eventsList
    }
}