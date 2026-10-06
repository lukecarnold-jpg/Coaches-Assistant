package com.lukearnold.coachesassistant.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Keeps each translation in step with the English strings: every string and plural is there, with
 * the same placeholders (so a translated message can't crash or drop a value when it's filled in),
 * and each language offered in Settings has a translation. Reads the resource files directly, so it
 * needs no Android.
 */
class TranslationsTest {

    private val resDir = File("src/main/res")

    private data class Strings(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    private fun read(folder: String): Strings {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(resDir, "$folder/strings.xml"))
        val strings = doc.getElementsByTagName("string").elements()
            .filter { it.getAttribute("translatable") != "false" }
            .associate { it.getAttribute("name") to it.textContent }
        val plurals = doc.getElementsByTagName("plurals").elements().associate { plural ->
            plural.getAttribute("name") to plural.getElementsByTagName("item").elements()
                .associate { it.getAttribute("quantity") to it.textContent }
        }
        return Strings(strings, plurals)
    }

    private fun org.w3c.dom.NodeList.elements(): List<Element> = (0 until length).map { item(it) as Element }

    /** The format placeholders in [text], e.g. {"%1$s", "%2$d"}; a bare %d counts as %1$d. */
    private fun placeholders(text: String): Set<String> =
        Regex("""%(\d+\$)?([sdf])""").findAll(text).map { "%" + (it.groupValues[1].ifEmpty { "1$" }) + it.groupValues[2] }.toSet()

    private val translations = listOf("es", "fr", "it")

    @Test
    fun everyTranslation_hasEveryStringAndPlural() {
        val english = read("values")
        translations.forEach { language ->
            val translated = read("values-$language")
            assertEquals("strings in $language", english.strings.keys, translated.strings.keys)
            assertEquals("plurals in $language", english.plurals.keys, translated.plurals.keys)
        }
    }

    @Test
    fun everyTranslation_keepsThePlaceholders() {
        val english = read("values")
        translations.forEach { language ->
            val translated = read("values-$language")
            english.strings.forEach { (name, text) ->
                assertEquals("$language $name", placeholders(text), placeholders(translated.strings.getValue(name)))
            }
            english.plurals.forEach { (name, items) ->
                val allowed = items.values.flatMap(::placeholders).toSet()
                val translatedItems = translated.plurals.getValue(name)
                assertEquals("$language $name other", placeholders(items.getValue("other")), placeholders(translatedItems.getValue("other")))
                translatedItems.forEach { (quantity, text) ->
                    assertTrue("$language $name $quantity uses only $allowed", allowed.containsAll(placeholders(text)))
                }
            }
        }
    }

    @Test
    fun everyTranslation_namesItsOwnLanguageForDrills() {
        translations.forEach { language ->
            assertEquals(language, read("values-$language").strings.getValue("content_language_tag"))
        }
    }

    @Test
    fun everyLanguageInSettings_hasATranslation() {
        val offered = AppLanguage.entries.mapNotNull { it.tag }.filter { it != "en" }
        assertEquals(translations.toSet(), offered.toSet())
    }
}
