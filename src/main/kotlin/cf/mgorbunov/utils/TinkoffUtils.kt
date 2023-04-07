package cf.mgorbunov.utils

import ru.tinkoff.piapi.contract.v1.MoneyValue

fun MoneyValue.convertToDouble(): Double {
    return this.units + (this.nano / 1_000_000_000.0)
}