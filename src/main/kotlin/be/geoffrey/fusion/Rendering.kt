package be.geoffrey.fusion

interface Renderer {
    fun render(structure: TopLevelElement,
               db: TypeDb): String
}

class XmlRenderer : Renderer {

    override fun render(structure: TopLevelElement,
                        db: TypeDb): String {
        return ""
    }

}