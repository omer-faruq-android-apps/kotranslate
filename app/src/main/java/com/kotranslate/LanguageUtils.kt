package com.kotranslate

import com.google.mlkit.nl.translate.TranslateLanguage

data class LanguageInfo(
    val code: String,
    val name: String,
    val mlKitCode: String
)

object LanguageUtils {

    val allLanguages: List<LanguageInfo> = listOf(
        LanguageInfo("af", "Afrikaans", TranslateLanguage.AFRIKAANS),
        LanguageInfo("ar", "Arabic", TranslateLanguage.ARABIC),
        LanguageInfo("be", "Belarusian", TranslateLanguage.BELARUSIAN),
        LanguageInfo("bg", "Bulgarian", TranslateLanguage.BULGARIAN),
        LanguageInfo("bn", "Bengali", TranslateLanguage.BENGALI),
        LanguageInfo("ca", "Catalan", TranslateLanguage.CATALAN),
        LanguageInfo("cs", "Czech", TranslateLanguage.CZECH),
        LanguageInfo("cy", "Welsh", TranslateLanguage.WELSH),
        LanguageInfo("da", "Danish", TranslateLanguage.DANISH),
        LanguageInfo("de", "German", TranslateLanguage.GERMAN),
        LanguageInfo("el", "Greek", TranslateLanguage.GREEK),
        LanguageInfo("en", "English", TranslateLanguage.ENGLISH),
        LanguageInfo("eo", "Esperanto", TranslateLanguage.ESPERANTO),
        LanguageInfo("es", "Spanish", TranslateLanguage.SPANISH),
        LanguageInfo("et", "Estonian", TranslateLanguage.ESTONIAN),
        LanguageInfo("fa", "Persian", TranslateLanguage.PERSIAN),
        LanguageInfo("fi", "Finnish", TranslateLanguage.FINNISH),
        LanguageInfo("fr", "French", TranslateLanguage.FRENCH),
        LanguageInfo("ga", "Irish", TranslateLanguage.IRISH),
        LanguageInfo("gl", "Galician", TranslateLanguage.GALICIAN),
        LanguageInfo("gu", "Gujarati", TranslateLanguage.GUJARATI),
        LanguageInfo("hi", "Hindi", TranslateLanguage.HINDI),
        LanguageInfo("hr", "Croatian", TranslateLanguage.CROATIAN),
        LanguageInfo("hu", "Hungarian", TranslateLanguage.HUNGARIAN),
        LanguageInfo("id", "Indonesian", TranslateLanguage.INDONESIAN),
        LanguageInfo("is", "Icelandic", TranslateLanguage.ICELANDIC),
        LanguageInfo("it", "Italian", TranslateLanguage.ITALIAN),
        LanguageInfo("ja", "Japanese", TranslateLanguage.JAPANESE),
        LanguageInfo("ka", "Georgian", TranslateLanguage.GEORGIAN),
        LanguageInfo("kn", "Kannada", TranslateLanguage.KANNADA),
        LanguageInfo("ko", "Korean", TranslateLanguage.KOREAN),
        LanguageInfo("lt", "Lithuanian", TranslateLanguage.LITHUANIAN),
        LanguageInfo("lv", "Latvian", TranslateLanguage.LATVIAN),
        LanguageInfo("mk", "Macedonian", TranslateLanguage.MACEDONIAN),
        LanguageInfo("mr", "Marathi", TranslateLanguage.MARATHI),
        LanguageInfo("ms", "Malay", TranslateLanguage.MALAY),
        LanguageInfo("mt", "Maltese", TranslateLanguage.MALTESE),
        LanguageInfo("nl", "Dutch", TranslateLanguage.DUTCH),
        LanguageInfo("no", "Norwegian", TranslateLanguage.NORWEGIAN),
        LanguageInfo("pl", "Polish", TranslateLanguage.POLISH),
        LanguageInfo("pt", "Portuguese", TranslateLanguage.PORTUGUESE),
        LanguageInfo("ro", "Romanian", TranslateLanguage.ROMANIAN),
        LanguageInfo("ru", "Russian", TranslateLanguage.RUSSIAN),
        LanguageInfo("sk", "Slovak", TranslateLanguage.SLOVAK),
        LanguageInfo("sl", "Slovenian", TranslateLanguage.SLOVENIAN),
        LanguageInfo("sq", "Albanian", TranslateLanguage.ALBANIAN),
        LanguageInfo("sv", "Swedish", TranslateLanguage.SWEDISH),
        LanguageInfo("sw", "Swahili", TranslateLanguage.SWAHILI),
        LanguageInfo("ta", "Tamil", TranslateLanguage.TAMIL),
        LanguageInfo("te", "Telugu", TranslateLanguage.TELUGU),
        LanguageInfo("th", "Thai", TranslateLanguage.THAI),
        LanguageInfo("tl", "Tagalog", TranslateLanguage.TAGALOG),
        LanguageInfo("tr", "Turkish", TranslateLanguage.TURKISH),
        LanguageInfo("uk", "Ukrainian", TranslateLanguage.UKRAINIAN),
        LanguageInfo("ur", "Urdu", TranslateLanguage.URDU),
        LanguageInfo("vi", "Vietnamese", TranslateLanguage.VIETNAMESE),
        LanguageInfo("zh", "Chinese", TranslateLanguage.CHINESE),
    )

    private val codeToInfo = allLanguages.associateBy { it.code }
    private val mlKitToInfo = allLanguages.associateBy { it.mlKitCode }

    fun getByCode(code: String): LanguageInfo? = codeToInfo[code]
    fun getByMlKitCode(mlKitCode: String): LanguageInfo? = mlKitToInfo[mlKitCode]

    fun getNameByCode(code: String): String = codeToInfo[code]?.name ?: code
    fun getMlKitCode(code: String): String? = codeToInfo[code]?.mlKitCode
}
