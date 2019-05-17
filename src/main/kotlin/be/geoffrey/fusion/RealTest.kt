package be.geoffrey.fusion

import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory

fun main() {
    val parser = XmlSchemaParser()

    val schemaLocation = "C:\\projects\\roots\\mow\\edelta-connector\\src\\main\\resources\\META-INF\\wsdl\\v20\\Opvragen\\GeefVastleggingen-02.00\\GeefVastleggingenWs.xsd"

    val knowledge = parser.readAllElementsAndTypesInFile(schemaLocation)

    val elementToRender = QName(namespace="http://webservice.geefvastleggingenws-02_00.edelta.mow.vlaanderen.be", name="GeefVastleggingenWs")
    val element = knowledge.getElement(elementToRender)!!

    val traverser = PossibleOptions(knowledge)

    println(traverser.getChoicesForTraversingElement(element))
//
//    val renderingConfig = RenderingConfig(listOf(
//            RegexValueForType(QName("http://generiek-02_00.vip.vlaanderen.be", "VersieType"), "00.00.0000"),
//            RegexValueForType(QName("http://generiek-02_00.vip.vlaanderen.be", "DatumType"), ",111-10-03"),
//            RegexValueForType(QName("http://generiek-02_00.vip.vlaanderen.be", "INSZType"), "01010101012"),
//            RegexValueForType(QName("http://generiek-edelta-common.edelta.mow.vlaanderen.be", "UuidType"), "aaaaaaaa-1111-2222-3333-abcdefghijkl"),
//            RegexValueForType(QName("http://generiek-02_00.vip.vlaanderen.be", "TijdType"), "29:01"),
//            RegexValueForType(QName("http://generiek-02_00.vip.vlaanderen.be", "UitzonderingIdentificatieType"), "1"),
//            RegexValueForType(QName("http://generiek-02_00.vip.vlaanderen.be", "UitzonderingOorsprongType"), "2")
//    ))
//    val asXml = XmlRenderer(knowledge).render(element, renderingConfig)
//
//    print(asXml)
//
//    print(if (isXmlValid(asXml, schemaLocation)) "VALID" else "INVALID")
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