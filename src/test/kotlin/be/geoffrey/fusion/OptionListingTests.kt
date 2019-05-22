package be.geoffrey.fusion

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

// TODO: Test for traversing with element ref. Too lazy now.

class OptionListingTests {

    private val STRING = QName("http://www.w3.org/2001/XMLSchema", "string")

    @Test
    fun testNoOptionsForSimpleType() {
        val blocks = XmlBuildingBlocks()

        val output = AllPossibleOptions(blocks)

        assertThat(output.getAvailablePathForksThroughElement(TopLevelElement(QName("shwoep", "MyName"), STRING))).isEmpty()
    }

    @Test
    fun testNoOptionsForRegularComplexType() {

        val blocks = XmlBuildingBlocks()

        val typeName = QName("shwoep", "SomeType")

        blocks.add(ComplexType(typeName, listOf(SequenceOfElements(listOf(
                Element("FieldOne", STRING),
                Element("FieldTwo", STRING)
        )))))

        val output = AllPossibleOptions(blocks)

        assertThat(output.getAvailablePathForksThroughElement(TopLevelElement(QName("", "Element"), typeName))).isEmpty()
    }

    @Test
    fun testImplementationChoicesOnlyOneChoice() {

        val blocks = XmlBuildingBlocks()

        val baseType = QName("", "BaseType")
        val implementation = QName("", "Implementation")

        blocks.add(ComplexType(baseType, listOf(), true))
        blocks.add(ComplexType(implementation, listOf(SequenceOfElements(listOf(Element("SomeField", STRING)))), false, baseType))

        val output = AllPossibleOptions(blocks)

        assertThat(output.getAvailablePathForksThroughElement(TopLevelElement(QName("", "SomeElement"), baseType))).isEmpty()
    }

    @Test
    fun testImplementationChoicesMultipleChanges() {

        val blocks = XmlBuildingBlocks()

        val baseType = QName("", "BaseType")

        val implementation = QName("", "Implementation1")
        val implementation2 = QName("", "Implementation2")

        blocks.add(ComplexType(baseType, listOf(), true))
        blocks.add(ComplexType(implementation, listOf(SequenceOfElements(listOf(Element("SomeField", STRING)))), false, baseType))
        blocks.add(ComplexType(implementation2, listOf(SequenceOfElements(listOf(Element("SomeField", STRING)))), false, baseType))

        val output = AllPossibleOptions(blocks)

        assertThat(output.getAvailablePathForksThroughElement(TopLevelElement(QName("", "SomeElement"), baseType)))
                .contains(ImplementationChoice("/SomeElement", listOf(implementation, implementation2)))
    }

    @Test
    fun testImplementationChoicesOneSelected() {

        val blocks = XmlBuildingBlocks()

        val baseType = QName("", "BaseType")
        val implementation = QName("", "Implementation1")
        val implementation2 = QName("", "Implementation2")

        blocks.add(ComplexType(baseType, listOf(), true))
        blocks.add(ComplexType(implementation, listOf(SequenceOfElements(listOf(Element("SomeFieldOne", STRING)))), false, baseType))
        blocks.add(ComplexType(implementation2, listOf(SequenceOfElements(listOf(Element("SomeFieldTwo", STRING)))), false, baseType))

        val output = AllPossibleOptions(blocks)

        assertThat(output.getAvailablePathForksThroughElement(
                TopLevelElement(QName("", "SomeElement"), baseType),
                Decisions(listOf(ImplementationDecision("/SomeElement", implementation2)
                )))).isEmpty()
    }

    @Test
    fun testChoiceOptionsListingOnlyOneChoice() {
        val blocks = XmlBuildingBlocks()

        val typeName = QName("shwoep", "SomeType")

        blocks.add(ComplexType(typeName, listOf(ChoiceOfElements(listOf(
                Element("FieldOne", STRING)
        )))))

        val output = AllPossibleOptions(blocks)

        assertThat(output.getAvailablePathForksThroughElement(TopLevelElement(QName("", "Element"), typeName))).isEmpty()
    }

    @Test
    fun testChoiceOptionsListingMultiple() {
        val blocks = XmlBuildingBlocks()

        val typeName = QName("shwoep", "SomeType")

        blocks.add(ComplexType(typeName, listOf(ChoiceOfElements(listOf(
                Element("FieldOne", STRING),
                Element("FieldTwo", STRING)
        )))))

        val output = AllPossibleOptions(blocks)

        assertThat(output.getAvailablePathForksThroughElement(TopLevelElement(QName("", "Element"), typeName))).contains(
                ChoiceIndexChoice("/Element/Choice", listOf(0, 1))
        )
    }

    @Test
    fun testMultipleChoicesButDecisionMade() {
        val blocks = XmlBuildingBlocks()

        val typeName = QName("shwoep", "SomeType")

        blocks.add(ComplexType(typeName, listOf(ChoiceOfElements(listOf(
                Element("FieldOne", STRING),
                Element("FieldTwo", STRING)
        )))))

        val output = AllPossibleOptions(blocks)

        assertThat(output.getAvailablePathForksThroughElement(
                TopLevelElement(QName("", "Element"), typeName),
                Decisions(listOf(ChoiceDecision("/Element/Choice", 0))))).isEmpty()
    }

    @Test
    fun testMinOccursOneChoices() {

        val blocks = XmlBuildingBlocks()

        val typeName = QName("shwoep", "SomeType")

        blocks.add(ComplexType(typeName, listOf(ChoiceOfElements(listOf(
                Element("FieldOne", STRING, minOccurs = 1, maxOccurs = 1)
        )))))

        val output = AllPossibleOptions(blocks)

        assertThat(output.getAvailablePathForksThroughElement(
                TopLevelElement(QName("", "Element"), typeName))).isEmpty()
    }

    @Test
    fun testMinOccursZeroOrOne() {

        val blocks = XmlBuildingBlocks()

        val typeName = QName("shwoep", "SomeType")

        blocks.add(ComplexType(typeName, listOf(ChoiceOfElements(listOf(
                Element("FieldOne", STRING, minOccurs = 0, maxOccurs = 1)
        )))))

        val output = AllPossibleOptions(blocks)

        assertThat(output.getAvailablePathForksThroughElement(TopLevelElement(QName("", "Element"), typeName)))
                .contains(AmountOfTimesToFollowPathChoice("/Element/Choice/FieldOne", listOf(0, 1)))
    }

    @Test
    fun testOccurrencesRange() {

        val blocks = XmlBuildingBlocks()

        val typeName = QName("shwoep", "SomeType")

        blocks.add(ComplexType(typeName, listOf(ChoiceOfElements(listOf(
                Element("FieldOne", STRING, minOccurs = 0, maxOccurs = 3)
        )))))

        val output = AllPossibleOptions(blocks)

        assertThat(output.getAvailablePathForksThroughElement(TopLevelElement(QName("", "Element"), typeName)))
                .contains(AmountOfTimesToFollowPathChoice("/Element/Choice/FieldOne", listOf(0, 1, 2, 3)))
    }

    @Test
    fun testUnbounded() {

        val blocks = XmlBuildingBlocks()

        val typeName = QName("shwoep", "SomeType")

        blocks.add(ComplexType(typeName, listOf(ChoiceOfElements(listOf(
                Element("FieldOne", STRING, minOccurs = 0, maxOccurs = Integer.MAX_VALUE)
        )))))

        val output = AllPossibleOptions(blocks)

        val availableForks = output.getAvailablePathForksThroughElement(TopLevelElement(QName("", "Element"), typeName))

        assertThat(availableForks).contains(AmountOfTimesToFollowPathChoice("/Element/Choice/FieldOne", listOf(0, Integer.MAX_VALUE)))
    }

    // TODO: Test Decisions that have something in their path like [impl=bla] or [0]
    // TODO: Also things like "the first time you follow, pick this impl, the second time this one..."
}