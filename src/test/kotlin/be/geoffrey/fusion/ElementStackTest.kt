package be.geoffrey.fusion

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ElementStackTest {
    @Test
    fun testCurrentPathIndication() {
        val element1 = TopLevelElement(QName("a", "element"), QName("a", "b"))
        val element2 = Element("AField", QName("does not", "matter"))

        val stack = ElementStack()
        stack.push(element1)
        stack.push(element2)

        assertThat(stack.visualizePath()).isEqualTo("/element/AField")

        stack.pop()

        assertThat(stack.visualizePath()).isEqualTo("/element")
    }

    @Test
    fun testRecursion() {
        val baseElement1 = TopLevelElement(QName("a", "element"), QName("a", "b"))
        val baseElement2 = Element("AField", QName("does not", "matter"))

        val stack = ElementStack()
        stack.push(baseElement1)
        stack.push(baseElement2)

        assertThat(stack.recursionWillStartWhenAdding(baseElement2)).isFalse()

        stack.push(baseElement2.copy())

        assertThat(stack.recursionWillStartWhenAdding(baseElement2)).isTrue()
    }
}