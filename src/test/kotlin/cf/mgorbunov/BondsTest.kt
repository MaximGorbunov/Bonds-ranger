package cf.mgorbunov

import cf.mgorbunov.model.BondInfo
import cf.mgorbunov.model.RiskLevel
import cf.mgorbunov.model.convertToProto
import cf.mgorbunov.service.BondsRangingService
import cf.mgorbunov.service.TinkoffService
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.googlecode.protobuf.format.JsonFormat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import ru.tinkoff.piapi.contract.v1.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.Month
import kotlin.test.assertEquals

class BondsTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `should calculate bonds correctly`() = runBlocking {
        mockkStatic(LocalDateTime::class)
        every {
            LocalDateTime.now()
        } returns LocalDateTime.of(2023, Month.APRIL, 7, 12, 0)
        val period = 30L
        val riskLevels = listOf(RiskLevel.LOW.convertToProto())
        val currency = "rub"
        val ruoniaRate = 7.61 / 100.0
        val sellDate = LocalDateTime.now().plusDays(period)
        val instrumentsService = mockk<InstrumentsServiceGrpcKt.InstrumentsServiceCoroutineStub>()
        val marketService = mockk<MarketDataServiceGrpcKt.MarketDataServiceCoroutineStub>()
        coEvery {
            instrumentsService.bonds(any())
        } answers {
            val result = BondsResponse.newBuilder()
            val json = String(Files.readAllBytes(Path.of(this.javaClass.getResource("/bonds.json")!!.toURI())))
            JsonFormat.merge(json, result)
            result.build()
        }
        coEvery {
            marketService.getLastPrices(any())
        } answers {
            val req = it.invocation.args[0] as GetLastPricesRequest
            val result = GetLastPricesResponse.newBuilder()
            val json = String(
                Files.readAllBytes(
                    Path.of(
                        this.javaClass.getResource("/bond_price_${req.figiList[0]}.json")?.toURI()
                            ?: throw IllegalArgumentException(req.figiList[0])
                    )
                )
            )
            JsonFormat.merge(json, result)
            result.build()
        }
        coEvery {
            instrumentsService.getBondCoupons(any())
        } answers {
            val req = it.invocation.args[0] as GetBondCouponsRequest
            val result = GetBondCouponsResponse.newBuilder()
            val json = String(
                Files.readAllBytes(
                    Path.of(
                        this.javaClass.getResource("/bond_coupon_${req.figi}.json")!!.toURI()
                    )
                )
            )
            JsonFormat.merge(json, result)
            result.build()
        }
        val tinkoffService =
            TinkoffService(instrumentsService, marketService, mockk(relaxed = true), mockk(relaxed = true))
        val rangingService = BondsRangingService(tinkoffService)
        val actual = rangingService.rangeBonds(riskLevels, sellDate, currency, ruoniaRate)
        val expectedJson = Files.readAllBytes(
            Path.of(
                this.javaClass.getResource("/expectedResult.json")!!.toURI()
            )
        )
        println(String(objectMapper.writeValueAsBytes(actual)))
        val expectedList: List<BondInfo> =
            objectMapper.readValue(expectedJson, object : TypeReference<List<BondInfo?>?>() {})
        expectedList.forEachIndexed { index, expected ->
            assertEquals(expected.name, actual[index].name)
            assertEquals(expected.profitPercent, actual[index].profitPercent)
            assertEquals(expected.price, actual[index].price)
            assertEquals(expected.figi, actual[index].figi)
            assertEquals(expected.ticker, actual[index].ticker)
            assertEquals(expected.isin, actual[index].isin)
        }
    }

}