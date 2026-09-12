package de.gabriel.nearping.logic

enum class CoordinationSignalCode(val wireCode: String) {
    COME_TO_YOU("come_to_you"),
    COME_TO_ME("come_to_me"),
    WAVE("wave"),
    YES("yes"),
    NO("no"),
    FLOOR("floor"),
    MEET_AT("meet_at"),
}

data class CoordinationChoice(
    val label: String,
    val signal: CoordinationSignal,
)

class CoordinationSignal private constructor(
    val code: CoordinationSignalCode,
    val optionCode: String? = null,
    val text: String,
) {
    companion object {
        val COME_TO_YOU = CoordinationSignal(
            CoordinationSignalCode.COME_TO_YOU,
            text = "Ich komme zu dir.",
        )
        val COME_TO_ME = CoordinationSignal(
            CoordinationSignalCode.COME_TO_ME,
            text = "Kommst du zu mir?",
        )
        val WAVE = CoordinationSignal(
            CoordinationSignalCode.WAVE,
            text = "Ich kann dich nicht sehen – bitte kurz winken.",
        )
        val YES = CoordinationSignal(CoordinationSignalCode.YES, text = "Ja.")
        val NO = CoordinationSignal(CoordinationSignalCode.NO, text = "Nein.")

        val quickChoices = listOf(
            CoordinationChoice("Ich komme zu dir", COME_TO_YOU),
            CoordinationChoice("Kommst du zu mir?", COME_TO_ME),
            CoordinationChoice("Bitte kurz winken", WAVE),
            CoordinationChoice("Ja", YES),
            CoordinationChoice("Nein", NO),
        )

        val floorChoices: List<CoordinationChoice> = (-3..20).map { floor ->
            val optionCode = when {
                floor < 0 -> "UG${-floor}"
                floor == 0 -> "EG"
                else -> "OG$floor"
            }
            val floorLabel = when {
                floor < 0 -> "${-floor}. Untergeschoss"
                floor == 0 -> "Erdgeschoss"
                else -> "$floor. Stock"
            }
            CoordinationChoice(
                label = floorLabel,
                signal = CoordinationSignal(
                    code = CoordinationSignalCode.FLOOR,
                    optionCode = optionCode,
                    text = "Ich bin im $floorLabel.",
                ),
            )
        }

        val meetingChoices = listOf(
            meetingChoice("main_entrance", "Haupteingang", "Treffen wir uns am Haupteingang?"),
            meetingChoice("exit", "Ausgang", "Treffen wir uns am Ausgang?"),
            meetingChoice("reception", "Empfang", "Treffen wir uns beim Empfang?"),
            meetingChoice("elevator", "Aufzüge", "Treffen wir uns bei den Aufzügen?"),
            meetingChoice("stairs", "Treppenhaus", "Treffen wir uns im Treppenhaus?"),
            meetingChoice("kitchen", "Küche", "Treffen wir uns in der Küche?"),
            meetingChoice("cafeteria", "Mensa/Cafeteria", "Treffen wir uns in der Mensa/Cafeteria?"),
            meetingChoice("cafe", "Café", "Treffen wir uns im Café?"),
        )

        fun fromWire(code: String, optionCode: String?): CoordinationSignal? {
            val signalCode = CoordinationSignalCode.entries
                .firstOrNull { it.wireCode == code } ?: return null
            return when (signalCode) {
                CoordinationSignalCode.COME_TO_YOU -> COME_TO_YOU.takeIf { optionCode == null }
                CoordinationSignalCode.COME_TO_ME -> COME_TO_ME.takeIf { optionCode == null }
                CoordinationSignalCode.WAVE -> WAVE.takeIf { optionCode == null }
                CoordinationSignalCode.YES -> YES.takeIf { optionCode == null }
                CoordinationSignalCode.NO -> NO.takeIf { optionCode == null }
                CoordinationSignalCode.FLOOR -> floorChoices
                    .firstOrNull { it.signal.optionCode == optionCode }
                    ?.signal
                CoordinationSignalCode.MEET_AT -> meetingChoices
                    .firstOrNull { it.signal.optionCode == optionCode }
                    ?.signal
            }
        }

        private fun meetingChoice(
            optionCode: String,
            label: String,
            text: String,
        ) = CoordinationChoice(
            label = label,
            signal = CoordinationSignal(
                code = CoordinationSignalCode.MEET_AT,
                optionCode = optionCode,
                text = text,
            ),
        )
    }
}
