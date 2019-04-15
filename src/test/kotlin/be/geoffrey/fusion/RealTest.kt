package be.geoffrey.fusion

import org.junit.Test

class RealTest {

    @Test
    fun realTest() {
        val parser = SchemaParser()
        val knowledge = parser.readAllElementsAndTypesInFile("C:\\projects\\roots\\mow\\edelta-consumer-connector\\src\\main\\resources\\META-INF\\wsdl\\v20\\Aanbieden\\GeefOpdrachtDienst-05.00\\GeefOpdrachtWsResponse.xsd")
        println("HEY")
    }

}