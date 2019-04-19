package be.geoffrey.fusion

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.xml.sax.SAXException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.StringReader
import java.util.*
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory


class RealXsdPlay {

    @Throws(FileNotFoundException::class)
    private fun getResource(filename: String): String {
        val resource = javaClass.classLoader.getResource(filename)
        Objects.requireNonNull(resource)
        return resource!!.file
    }

}