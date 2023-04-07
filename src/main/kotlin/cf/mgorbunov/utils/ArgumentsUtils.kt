package cf.mgorbunov.utils

fun argumentsToMap(arguments: Array<String>): MutableMap<String, String?> {
    val argumentMap = mutableMapOf<String, String?>()
    var i = 0
    while (i < arguments.size) {
        val currentArgument = arguments[i]
        val nextArgument = if (arguments.size > i + 1) arguments[i + 1] else null
        if (!currentArgument.startsWith("-")) {
            throw IllegalArgumentException("Expected argument started with --")
        }
        val key = currentArgument.replace(Regex("-+"), "")
        if (nextArgument != null && !nextArgument.startsWith("--")) {
            argumentMap[key] = nextArgument
            i += 2
        } else {
            argumentMap[key] = null
            i++
        }
    }
    return argumentMap
}