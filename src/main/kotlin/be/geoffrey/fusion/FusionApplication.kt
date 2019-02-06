package be.geoffrey.fusion

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FusionApplication

fun main(args: Array<String>) {
    runApplication<FusionApplication>(*args)
}