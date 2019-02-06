package be.geoffrey.fusion

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.dependency.StyleSheet
import com.vaadin.flow.component.html.*
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.orderedlayout.BoxSizing
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route


@Route("generation")
@StyleSheet("/style.css")
class GenerationView : HorizontalLayout() {
    init {

        width = "100%"
        boxSizing = BoxSizing.BORDER_BOX

        val leftPane = VerticalLayout(
                H1("Fusion"),
                H2("Active Project: FirstProject"),
                H2("Structure Sources"),
                UnorderedList(
                        ListItem("Java Sources - C:/tmp/java_sources"),
                        ListItem("XML Sources - C:/tmp/xml_sources")),
                Button("Click me") {
                    Notification.show("Hello Spring+Vaadin userzz!")
                })

        leftPane.width = "20%"

        val templateArea = TextArea("Template")
        templateArea.width = "100%"

        val resultArea = TextArea("Result")
        resultArea.isReadOnly = true
        resultArea.width = "100%"

        val rightPane = VerticalLayout(
                templateArea,
                resultArea
        )
        rightPane.width = "80%"

        rightPane.expand(templateArea, resultArea)

        add(leftPane, rightPane)

        expand()
    }
}