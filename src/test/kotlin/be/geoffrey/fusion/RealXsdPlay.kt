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

    @Test
    fun realTest() {
        val parser = SchemaParser()

        val schemaLocation = "C:\\projects\\roots\\mow\\edelta-consumer-connector\\src\\main\\resources\\META-INF\\wsdl\\v20\\Aanbieden\\GeefOpdrachtDienst-05.00\\GeefOpdrachtWsResponse.xsd"

        val knowledge = parser.readAllElementsAndTypesInFile(schemaLocation)

        val elementToRender = QName("http://webservice.geefopdrachtwsdienst-02_00.edelta.mow.vlaanderen.be", "GeefOpdrachtWsResponse")
        val element = knowledge.getElement(elementToRender)!!

        val asXml = XmlRenderer(knowledge).render(element)

        print(asXml)

        assertThat(isXmlValid(asXml, schemaLocation)).isTrue()
    }

    private fun isXmlValid(
            xml: String,
            xsdLocation: String
    ): Boolean {
        val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        return try {
            val schema = schemaFactory.newSchema(File(xsdLocation))
            val validator = schema.newValidator()
            val reader = StringReader(xml)
            validator.validate(StreamSource(reader))
            true
        } catch (e: SAXException) {
            e.printStackTrace()
            false
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    @Throws(FileNotFoundException::class)
    private fun getResource(filename: String): String {
        val resource = javaClass.classLoader.getResource(filename)
        Objects.requireNonNull(resource)
        return resource!!.file
    }

}