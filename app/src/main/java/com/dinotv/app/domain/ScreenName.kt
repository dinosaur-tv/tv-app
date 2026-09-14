package com.dinotv.app.domain

/**
 * What this television calls itself, for the phone that has to tell it from the one in the
 * other room. Android reports the make in whatever case the vendor felt like, and often
 * repeats it inside the model, so both are tidied before they are put together.
 */
object ScreenName {
    private const val MAX = 40

    fun of(manufacturer: String, model: String): String {
        val make = tidy(manufacturer)
        val name = model.trim()
        if (name.isEmpty()) return make.take(MAX)
        if (make.isEmpty()) return name.take(MAX)
        // "LG-43UQ81" and "LG 43UQ81" already say the make; saying it twice reads as a stutter.
        val repeats = name.lowercase().startsWith(make.lowercase())
        return (if (repeats) name else "$make $name").take(MAX)
    }

    /** "xiaomi" and "LG" both arrive; the first wants a capital, the second wants leaving alone. */
    private fun tidy(manufacturer: String): String {
        val make = manufacturer.trim()
        if (make.isEmpty()) return ""
        if (make.any { it.isUpperCase() }) return make
        return make.replaceFirstChar { it.uppercase() }
    }
}
