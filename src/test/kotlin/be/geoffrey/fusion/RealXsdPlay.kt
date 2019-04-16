package be.geoffrey.fusion

import org.junit.Test

class RealXsdPlay {

    @Test
    fun realTest() {
        val parser = SchemaParser()
        val knowledge = parser.readAllElementsAndTypesInFile("C:\\projects\\roots\\mow\\edelta-consumer-connector\\src\\main\\resources\\META-INF\\wsdl\\v20\\Aanbieden\\GeefOpdrachtDienst-05.00\\GeefOpdrachtWsResponse.xsd")

        val elementToRender = QName("http://webservice.geefopdrachtwsdienst-02_00.edelta.mow.vlaanderen.be", "GeefOpdrachtWsResponse")
        val element = knowledge.getElement(elementToRender)!!

        val asXml = XmlRenderer(knowledge).render(element)

        print(asXml)
    }

}