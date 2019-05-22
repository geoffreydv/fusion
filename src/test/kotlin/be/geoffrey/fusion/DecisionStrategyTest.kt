package be.geoffrey.fusion

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class DecisionStrategyTest {

    @Test
    fun testDecisionStrategy() {

        val blocks = XmlBuildingBlocks()

        val strategy = FollowValidRulesAndAddAsMuchVarietyAsPossibleInOneGo()

        val result = strategy.getPossibilitiesToRender(TopLevelElement(QName("", "JustAnElement"), QName(XMLNS, "string")), blocks)

        assertThat(result).hasSize(1)
        assertThat(result[0].decisions).isEmpty()
    }

    @Test
    fun testDecideImplementationPicksFirstOne() {

        val blocks = XmlBuildingBlocks()

        val base = ComplexType(QName("", "BaseType"), listOf(
                Element("FieldOne", QName(XMLNS, "string"))
        ), abstract = true)

        val impl1 = ComplexType(QName("", "Impl1"), extensionOf = QName("", "BaseType"))
        val impl2 = ComplexType(QName("", "Impl2"), extensionOf = QName("", "BaseType"))

        blocks.add(base)
        blocks.add(impl1)
        blocks.add(impl2)

        val strategy = FollowValidRulesAndAddAsMuchVarietyAsPossibleInOneGo()

        val result = strategy.getPossibilitiesToRender(
                TopLevelElement(QName("", "JustAnElement"), QName("", "BaseType"))
        , blocks)

        assertThat(result).hasSize(1)
        assertThat(result[0].decisions).contains(
                ImplementationDecision("/JustAnElement", QName("", "Impl1"))
        )
    }
}