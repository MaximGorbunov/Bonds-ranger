package cf.mgorbunov

import cf.mgorbunov.model.Arguments
import cf.mgorbunov.model.BondInfo
import cf.mgorbunov.model.convertToProto
import cf.mgorbunov.service.BondsRangingService
import cf.mgorbunov.service.TinkoffService
import cf.mgorbunov.utils.RateLimiter
import cf.mgorbunov.utils.TokenCallCredentials
import cf.mgorbunov.utils.argumentsToMap
import de.vandermeer.asciitable.AsciiTable
import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import kotlinx.coroutines.runBlocking
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpcKt
import ru.tinkoff.piapi.contract.v1.MarketDataServiceGrpcKt
import ru.tinkoff.piapi.contract.v1.RiskLevel.RISK_LEVEL_LOW
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes

fun main(args: Array<String>): Unit = runBlocking {
    println(LOGO)
    val arguments = parseArguments(args)

    val channel = NettyChannelBuilder.forTarget("invest-public-api.tinkoff.ru:443").build()
    val rangingService = createRangingService(arguments, channel)
    val bondsRanged = rangingService.rangeBonds(
        arguments.riskLevels,
        arguments.sellDate,
        arguments.currency,
        arguments.ruoniaRate
    )

    printTable(bondsRanged)
    channel.shutdownNow()
}

private fun createRangingService(arguments: Arguments, channel: ManagedChannel): BondsRangingService {
    val instrumentsService = InstrumentsServiceGrpcKt.InstrumentsServiceCoroutineStub(channel).withCallCredentials(
        TokenCallCredentials(arguments.token)
    )
    val marketService = MarketDataServiceGrpcKt.MarketDataServiceCoroutineStub(channel).withCallCredentials(
        TokenCallCredentials(arguments.token)
    )
    val instrumentServiceRateLimit = RateLimiter(200, 1.minutes)
    val marketServiceRateLimit = RateLimiter(300, 1.minutes)
    val tinkoffService =
        TinkoffService(
            instrumentsService,
            marketService,
            instrumentServiceRateLimit,
            marketServiceRateLimit
        )
    return (BondsRangingService(tinkoffService))
}

private fun printTable(bondsRanged: List<BondInfo>) {
    val table = AsciiTable()
    table.addRow("Name", "Profit", "Price", "Ticker", "FIGI", "ISIN")
    table.addRule()
    bondsRanged.forEach {
        table.addRow(it.name, String.format("%.2f%%", it.profitPercent), it.price, it.ticker, it.figi, it.isin)
        table.addRule()
    }
    println(table.render(150))
}

private fun parseArguments(args: Array<String>): Arguments {
    val arguments = argumentsToMap(args)
    val period =
        arguments["period"]?.toLong() ?: throw IllegalArgumentException("period must be provided with --period <days>")
    val currency = arguments["currency"] ?: "rub"
    val riskLevels: List<ru.tinkoff.piapi.contract.v1.RiskLevel> = arguments["risk"]?.split(",")?.map {
        cf.mgorbunov.model.RiskLevel.valueOf(it).convertToProto()
    } ?: listOf(RISK_LEVEL_LOW)
    val ruoniaRate =
        arguments["ruonia"]?.toDouble()?.div(100) ?: throw IllegalArgumentException("--ruonia rate required")
    val token = arguments["token"] ?: throw IllegalArgumentException("--token required to call tinkoff API")
    val sellDate = LocalDateTime.now().plusDays(period)
    return Arguments(period, currency, riskLevels, ruoniaRate, token, sellDate)
}

private const val LOGO = """
                            ██████╗░░█████╗░███╗░░██╗██████╗░░██████╗  ██████╗░░█████╗░███╗░░██╗░██████╗░███████╗██████╗░
                            ██╔══██╗██╔══██╗████╗░██║██╔══██╗██╔════╝  ██╔══██╗██╔══██╗████╗░██║██╔════╝░██╔════╝██╔══██╗
                            ██████╦╝██║░░██║██╔██╗██║██║░░██║╚█████╗░  ██████╔╝███████║██╔██╗██║██║░░██╗░█████╗░░██████╔╝
                            ██╔══██╗██║░░██║██║╚████║██║░░██║░╚═══██╗  ██╔══██╗██╔══██║██║╚████║██║░░╚██╗██╔══╝░░██╔══██╗
                            ██████╦╝╚█████╔╝██║░╚███║██████╔╝██████╔╝  ██║░░██║██║░░██║██║░╚███║╚██████╔╝███████╗██║░░██║
                            ╚═════╝░░╚════╝░╚═╝░░╚══╝╚═════╝░╚═════╝░  ╚═╝░░╚═╝╚═╝░░╚═╝╚═╝░░╚══╝░╚═════╝░╚══════╝╚═╝░░╚═╝
    """