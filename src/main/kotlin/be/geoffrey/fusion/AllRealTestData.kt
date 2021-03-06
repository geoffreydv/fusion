package be.geoffrey.fusion

import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory

fun main() {

    val baseDir = File("C:\\projects\\roots\\mow\\edelta-connector\\src\\main\\resources\\META-INF\\wsdl\\v20")

    val xsdFiles = baseDir.walkTopDown()
            .filter { it.extension == "xsd" }
            .toList()

    val parser = XmlSchemaParser()

    xsdFiles.forEach {

        println("Reading file: " + it.absoluteFile)

        val knowledge = parser.readAllElementsAndTypesInFile(it.absolutePath)

        val amountOfElements = knowledge.getKnownElements().size

        if(amountOfElements > 0) {

            println("Found $amountOfElements elements to render in the file '${it.absolutePath}'")

            for (knownElement in knowledge.getKnownElements()) {

                println("Rendering ${knownElement.name}")

                val renderingConfig = RenderingConfig(listOf(
                        RegexValueForType(QName("http://generiek-02_00.vip.vlaanderen.be", "VersieType"), "00.00.0000"),
                        RegexValueForType(QName("http://generiek-02_00.vip.vlaanderen.be", "DatumType"), ",111-10-03"),
                        RegexValueForType(QName("http://generiek-02_00.vip.vlaanderen.be", "INSZType"), "01010101012"),
                        RegexValueForType(QName("http://generiek-edelta-common.edelta.mow.vlaanderen.be", "UuidType"), "aaaaaaaa-1111-2222-3333-abcdefghijkl"),
                        RegexValueForType(QName("http://generiek-02_00.vip.vlaanderen.be", "TijdType"), "29:01"),
                        RegexValueForType(QName("http://generiek-02_00.vip.vlaanderen.be", "UitzonderingIdentificatieType"), "1"),
                        RegexValueForType(QName("http://generiek-02_00.vip.vlaanderen.be", "UitzonderingOorsprongType"), "2"),
                        RegexValueForType(QName("http://generiek-02_00.vip.vlaanderen.be", "VolledigDatumType"), "1904-01-10"),
                        RegexValueForType(QName("http://generiek-02_00.vip.vlaanderen.be", "Datum2_0Type"), "2008-01-10")
                ))

                val asXml = XmlRenderer2(knowledge).render(knownElement, renderingConfig)

                if(!isXmlValid(asXml, it.absolutePath)) {
                    println(asXml)
                    throw IllegalArgumentException("Invalid xml generated.")
                }
            }
        }
    }
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