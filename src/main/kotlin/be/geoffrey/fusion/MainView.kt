package be.geoffrey.fusion

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.Route

@Route
class MainView : VerticalLayout() {
    init {
        add(Button("Click me") { Notification.show("Hello Spring+Vaadin userzz!") })
    }
}