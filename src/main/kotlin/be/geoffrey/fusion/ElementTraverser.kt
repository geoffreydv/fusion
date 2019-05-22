package be.geoffrey.fusion

data class StackMetadata(var concreteImplementationMarker: QName? = null,
                         var choiceMarker: Int? = null)

open class ChosenPaths(private val elements: MutableList<Pair<Trackable, StackMetadata>> = mutableListOf()) {

    private fun isEmpty() = elements.isEmpty()

    fun size() = elements.size

    open fun push(item: Trackable) = elements.add(Pair(item, StackMetadata()))

    fun peek(): Pair<Trackable, StackMetadata>? {
        return elements.lastOrNull()
    }

    open fun pop(): Pair<Trackable, StackMetadata>? {
        val item = elements.lastOrNull()
        if (!isEmpty()) {
            elements.removeAt(elements.size - 1)
        }
        return item
    }

    fun getElements(): MutableList<Pair<Trackable, StackMetadata>> {
        return elements.toMutableList() // Creates a new list
    }

    override fun toString(): String {
        return "/" + elements.joinToString(separator = "/") {

            var output = it.first.shortName()

            if (it.second.concreteImplementationMarker != null) {
                output += "[impl=${it.second.concreteImplementationMarker!!.name}]"
            } else if (it.second.choiceMarker != null) {
                output += "[${it.second.choiceMarker}]"
            }

            output
        }
    }

    fun recursionWillStartWhenAdding(element: Element, maxDepth: Int = 2): Boolean {

        val previousOccurrences = elements
                .filter {
                    it.first is Element
                }
                .count {
                    (it.first as Element).getStructureReference() == element.elementType
                }

        return previousOccurrences >= maxDepth
    }

    fun getCurrentElementMetadata(): StackMetadata {
        val last = elements.last()
        return last.second
    }
}

class ReadOnlyChosenPaths(regular: ChosenPaths) : ChosenPaths(regular.getElements()) {
    override fun push(item: Trackable): Boolean {
        throw IllegalArgumentException("This class is readonly")
    }

    override fun pop(): Pair<Trackable, StackMetadata>? {
        throw IllegalArgumentException("This class is readonly")
    }
}

interface Choice

data class ImplementationChoice(val path: String, val choices: List<QName>) : Choice

data class ChoiceIndexChoice(val path: String, val choices: List<Int>) : Choice

data class AmountOfTimesToFollowPathChoice(val path: String, val choices: List<Int>) : Choice

interface Decision

data class ImplementationDecision(val path: String, val decision: QName) : Decision

data class ChoiceDecision(val path: String, val index: Int) : Decision

class Decisions(val decisions: List<Decision> = listOf()) {

    fun add(decision: Decision): Decisions {
        return Decisions(this.decisions + decision)
    }

    fun getImplementationDecision(path: String): ImplementationDecision? {
        return decisions.filterIsInstance(ImplementationDecision::class.java).firstOrNull { it.path == path }
    }

    fun getChoiceDecision(path: String): ChoiceDecision? {
        return decisions.filterIsInstance(ChoiceDecision::class.java).firstOrNull { it.path == path }
    }
}

class AllPossibleOptions(private val typeDb: KnownBuildingBlocks) {

    fun getAvailablePathForksThroughElement(element: TopLevelElement, decisions: Decisions = Decisions()): MutableList<Choice> {

        val options = mutableListOf<Choice>()

        val traverser = ElementTraverser(typeDb, decisions,
                TraverseHooks(
                        availableImplementationPaths = { stack, possibilities -> options.add(ImplementationChoice(stack.toString(), possibilities)) },
                        choicePossible = { stack, indexes -> options.add(ChoiceIndexChoice(stack.toString(), indexes)) },
                        timesToFollowPathDecision = { stack, possibleTimes -> options.add(AmountOfTimesToFollowPathChoice(stack.toString(), possibleTimes)) }
                )
        )

        traverser.traverseElement(element)
        return options
    }
}

class TraverseHooks(val availableImplementationPaths: (stack: ReadOnlyChosenPaths, possibilities: List<QName>) -> Unit = { _, _ -> },
                    val choicePossible: (stack: ReadOnlyChosenPaths, indexes: List<Int>) -> Unit = { _, _ -> },
                    val timesToFollowPathDecision: (stack: ReadOnlyChosenPaths, possibleTimes: List<Int>) -> Unit = { _, _ -> },

                    val simpleElementHit: (element: ElementBase, type: SimpleType) -> Unit = { _, _ -> },
                    val startRenderingComplexElement: (element: ElementBase, stack: ReadOnlyChosenPaths) -> Unit = { _, _ -> },
                    val finishedRenderingComplexElement: (stack: ReadOnlyChosenPaths) -> Unit = { _ -> }
)

class ElementTraverser(private val typeDb: KnownBuildingBlocks,
                       private val decisions: Decisions = Decisions(),
                       private val hooks: TraverseHooks = TraverseHooks()) {

    fun traverseElement(element: ElementBase, stack: ChosenPaths = ChosenPaths()) {

        val timesToFollow = determineTimesToFollowPath(element, stack)

        for (followIndex in 0 until timesToFollow) {

            stack.push(element)

            when (val structure = typeDb.getStructure(element.getStructureReference())
                    ?: throw IllegalArgumentException("The required type for element ${element.getDisplayName()} was not found: ${element.getStructureReference()}")) {
                is ComplexType -> {
                    val possibleImplementations = allConcreteImplementations(structure)
                    val pathsToFollow = decidePossibleImplementationPathsToFollow(possibleImplementations, stack)

                    if (pathsToFollow.size > 1) {
                        // If not decided, allow decision
                        hooks.availableImplementationPaths(ReadOnlyChosenPaths(stack), pathsToFollow.map { it.name })
                    }

                    for (possibleType in pathsToFollow) {

                        if (possibleImplementations.size > 1 || structure.abstract) {
                            stack.getCurrentElementMetadata().concreteImplementationMarker = possibleType.name
                        }

                        println(stack.toString())

                        hooks.startRenderingComplexElement(element, ReadOnlyChosenPaths(stack))

                        if (!possibleType.abstract) {
                            val children = allChildrenIncludingOnesFromParentTypes(possibleType)
                            traverseGroupChildren(children, stack)
                        }

                        hooks.finishedRenderingComplexElement(ReadOnlyChosenPaths(stack))
                    }
                }
                is SimpleType -> {
                    println(stack.toString())
                    hooks.simpleElementHit(element, structure)
                }
                else -> throw IllegalArgumentException("How did I even end up here?")
            }

            stack.pop()
        }
    }

    private fun decidePossibleImplementationPathsToFollow(allPossible: List<ComplexType>,
                                                          stack: ChosenPaths): List<ComplexType> {

        val decision = decisions.getImplementationDecision(stack.toString()) ?: return allPossible
        val decidedType = typeDb.getStructure(decision.decision) as ComplexType
        return listOf(decidedType)
    }

    private fun allConcreteImplementations(structure: ComplexType): MutableList<ComplexType> {
        val possibilities = mutableListOf<ComplexType>()

        if (!structure.abstract) {
            possibilities.add(structure)
        }

        possibilities.addAll(findAllMoreSpecificImplementations(structure))
        return possibilities
    }

    private fun findAllMoreSpecificImplementations(structure: ComplexType): List<ComplexType> {

        val myConcreteImplementations = typeDb.getConcreteImplementationsFor(structure.name).toMutableList()

        val tmp = mutableListOf<ComplexType>()
        for (implementation in myConcreteImplementations) {
            tmp.addAll(findAllMoreSpecificImplementations(implementation))
        }

        myConcreteImplementations.addAll(tmp)
        return myConcreteImplementations
    }

    private fun allChildrenIncludingOnesFromParentTypes(structure: ComplexType): List<StructureElement> {

        val parents = typeDb.getParentTypesFor(structure)

        val elements = mutableListOf<StructureElement>()

        for (parent in parents) {
            elements.addAll(parent.children)
        }

        elements.addAll(structure.children)
        return elements
    }

    private fun traverseGroupChildren(children: List<StructureElement>,
                                      stack: ChosenPaths) {
        for (child in children) {
            traverseElementOfGroup(child, stack)
        }
    }

    private fun determineTimesToFollowPath(element: ElementBase, stack: ChosenPaths): Int {
        if (element is Element) {
            if (element.minOccurs != element.maxOccurs) {

                stack.push(element) // Pretty lame, I have to add it to correct the path
                if (element.maxOccurs == Integer.MAX_VALUE) {
                    hooks.timesToFollowPathDecision(ReadOnlyChosenPaths(stack), listOf(0, Integer.MAX_VALUE))
                } else {
                    hooks.timesToFollowPathDecision(ReadOnlyChosenPaths(stack), IntRange(element.minOccurs, element.maxOccurs).toList())
                }
                stack.pop()

                // Default behavior
                return when {
                    element.minOccurs > 0 -> element.minOccurs
                    else -> 1
                }
            } else {
                return element.minOccurs
            }
        }
        return 1
    }

    private fun traverseElementOfGroup(child: StructureElement, stack: ChosenPaths) {

        when (child) {
            is Element -> {
                if (!stack.recursionWillStartWhenAdding(child)) {
                    traverseElement(child, stack)
                }
            }
            is SequenceOfElements -> {
                traverseSequence(stack, child)
            }
            is ChoiceOfElements -> {
                traverseChoice(stack, child)
            }
        }
    }

    private fun traverseSequence(stack: ChosenPaths, child: SequenceOfElements) {
        stack.push(child)

        if (child.allElements().isNotEmpty()) {
            println(stack)
            traverseGroupChildren(child.allElements(), stack)
        }

        stack.pop()
    }

    private fun traverseChoice(stack: ChosenPaths, child: ChoiceOfElements) {
        stack.push(child)

        val possibleChoicePaths = child.allElements()

        if (possibleChoicePaths.isNotEmpty()) {

            val pathsToFollow = decidePossibleChoicePathsToFollow(possibleChoicePaths, stack)

            if (pathsToFollow.size > 1) {
                // If not decided, allow decision
                hooks.choicePossible(ReadOnlyChosenPaths(stack), (0 until pathsToFollow.size).toList())
            }

            for ((index, pathToFollow) in pathsToFollow.withIndex()) {

                if (possibleChoicePaths.size > 1) {
                    stack.getCurrentElementMetadata().choiceMarker = index
                }

                println(stack)
                traverseElementOfGroup(pathToFollow, stack)
            }
        }

        stack.pop()
    }

    private fun decidePossibleChoicePathsToFollow(allPossible: List<StructureElement>, stack: ChosenPaths): List<StructureElement> {
        val decision = decisions.getChoiceDecision(stack.toString()) ?: return allPossible
        return listOf(allPossible[decision.index])
    }
}