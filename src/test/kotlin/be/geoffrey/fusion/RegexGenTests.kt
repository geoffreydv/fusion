package be.geoffrey.fusion

import com.mifmif.common.regex.Generex
import org.junit.Test

class RegexGenTests {

    @Test
    fun test() {

        val ondernemingsNr = "[0-9]{9,10}"
        val dropserverNaam = "[A-Za-z0-9_\\-.% ]*\\.?[A-Za-z0-9.]+"
        val postcode = "[\\p{L}\\p{N}\\s\\-]*"
        val blie = "testing[A-Za-z]{70}testing"

        println(generateRegex(ondernemingsNr))
        println(generateRegex(dropserverNaam))
        println(generateRegex(postcode))
        println(generateRegex(blie))
    }

    // POSIX classes are used, defined on https://www.regular-expressions.info/posixbrackets.html#class

    private fun generateRegex(reg: String): String {

        var final = reg

        final = final.replace("\\p{L}", "a-z")
        final = final.replace("\\p{l}", "a-z")

        final = final.replace("\\p{N}", "0-9")
        final = final.replace("\\p{d}", "0-9")

        final = Regex(final).toString()

        println("Generating example for $final")

        val generator = Generex(final)
        return generator.random()
    }
}
