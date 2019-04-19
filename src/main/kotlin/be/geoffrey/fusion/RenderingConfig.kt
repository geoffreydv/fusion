package be.geoffrey.fusion

data class RegexValueForType(val type: QName, val valueToRender: String)

data class RenderingConfig(private val regexValueForTypes: List<RegexValueForType> = listOf()) {
    fun getRegexValueForType(type: QName): String? {
        return regexValueForTypes.filter { it.type == type }.map { it.valueToRender }.firstOrNull()
    }
}