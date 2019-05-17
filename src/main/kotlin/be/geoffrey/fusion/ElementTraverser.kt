package be.geoffrey.fusion

data class StackMetadata(var concreteImplementationMarker: QName? = null,
                         var choiceMarker: Int? = null)

class TrackStack {

    private val elements: MutableList<Pair<Trackable, StackMetadata>> = mutableListOf()

    private fun isEmpty() = elements.isEmpty()

    fun size() = elements.size

    fun push(item: Trackable) = elements.add(Pair(item, StackMetadata()))

    fun pop(): Pair<Trackable, StackMetadata>? {
        val item = elements.lastOrNull()
        if (!isEmpty()) {
            elements.removeAt(elements.size - 1)
        }
        return item
    }

    override fun toString(): String {
        return "/" + elements.joinToString(separator = "/") {

            var output = it.first.shortName()

            if (it.second.concreteImplementationMarker != null) {
                output += "[impl=${it.second.concreteImplementationMarker!!.name}]"
            } else if(it.second.choiceMarker != null) {
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

interface Choice

data class ImplementationPath(val path: String, val choices: List<QName>) : Choice

data class ChoicePath(val path: String, val choices: List<Int>) : Choice

interface Decision

data class ImplementationDecision(val path: String, val decision: QName) : Decision

data class ChoiceDecision(val path: String, val index: Int) : Decision

class Decisions(private val decisions: List<Decision> = listOf()) {
    fun getImplementationDecision(path: String): ImplementationDecision? {
        return decisions.filterIsInstance(ImplementationDecision::class.java).firstOrNull { it.path == path }
    }

    fun getChoiceDecision(path: String): ChoiceDecision? {
        return decisions.filterIsInstance(ChoiceDecision::class.java).firstOrNull { it.path == path }
    }
}

class PossibleOptions(
        typeDb: KnownBuildingBlocks,
        element: TopLevelElement,
        decisions: Decisions = Decisions()
) : ElementTraverser(typeDb, decisions) {

    private val options = mutableListOf<Choice>()

    init {
        traverseElement(element)
    }

    override fun signalThatChoosingAnImplementationIsPossible(stack: TrackStack, possibilities: List<QName>) {
        super.signalThatChoosingAnImplementationIsPossible(stack, possibilities)
        options.add(ImplementationPath(stack.toString(), possibilities))
    }

    override fun signalThatChoosingAChoicePathIsPossible(stack: TrackStack, indexes: List<Int>) {
        super.signalThatChoosingAChoicePathIsPossible(stack, indexes)
        options.add(ChoicePath(stack.toString(), indexes))
    }

    fun getChoices(): List<Choice> {
        return options
    }
}

/**
 *
 * General Idea: Decide which trees to take / traverse
 * Every step:
 * - Log which step you took
 * - Log all the next steps that can be taken from here
 * - If a choice is made -> Follow that branch
 * - If no choice is made -> Follow every possibility
 */

open class ElementTraverser(private val typeDb: KnownBuildingBlocks,
                            private val decisions: Decisions = Decisions()) {

    open fun signalThatChoosingAnImplementationIsPossible(stack: TrackStack, possibilities: List<QName>) {
        println("$stack: Multiple implementations possible: $possibilities")
    }

    open fun signalThatChoosingAChoicePathIsPossible(stack: TrackStack, indexes: List<Int>) {
        println("$stack: Please choose a choice path to follow: $indexes")
    }

    fun traverseElement(element: ElementBase, stack: TrackStack = TrackStack()) {

        stack.push(element)

        when (val structure = typeDb.getStructure(element.getStructureReference())
                ?: throw IllegalArgumentException("The required type for element ${element.getDisplayName()} was not found: ${element.getStructureReference()}")) {
            is ComplexType -> {

                val possibleImplementations = allConcreteImplementations(structure)
                val pathsToFollow = decidePossibleImplementationPathsToFollow(possibleImplementations, stack)

                if (pathsToFollow.size > 1) {
                    // If not decided, allow decision
                    signalThatChoosingAnImplementationIsPossible(stack, pathsToFollow.map { it.name })
                }

                for (possibleType in pathsToFollow) {

                    if (possibleImplementations.size > 1) {
                        stack.getCurrentElementMetadata().concreteImplementationMarker = possibleType.name
                    }

                    println(stack.toString())

                    traverseComplexTypeChildren(possibleType, stack, element)
                }
            }
            is SimpleField -> {
                println(stack.toString())
            }
            else -> throw IllegalArgumentException("blahoe")
        }

        stack.pop()
    }

    private fun decidePossibleImplementationPathsToFollow(allPossible: List<ComplexType>,
                                                          stack: TrackStack): List<ComplexType> {

        val decision = decisions.getImplementationDecision(stack.toString()) ?: return allPossible
        val decidedType = typeDb.getStructure(decision.decision) as ComplexType
        return listOf(decidedType)
    }

    private fun traverseComplexTypeChildren(possibleType: ComplexType, stack: TrackStack, element: ElementBase) {
        if (!possibleType.abstract) {
            stack.push(element)
            val children = allChildrenIncludingOnesFromParentTypes(possibleType)
            traverseGroupChildren(children, stack)
            stack.pop()
        }
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
                                      stack: TrackStack) {

        for (child in children) {

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
    }

    private fun traverseElementOfGroup(child: StructureElement, stack: TrackStack) {
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

    private fun traverseSequence(stack: TrackStack, child: SequenceOfElements) {
        stack.push(child)

        if (child.allElements().isNotEmpty()) {
            println(stack)
            traverseGroupChildren(child.allElements(), stack)
        }

        stack.pop()
    }

    private fun traverseChoice(stack: TrackStack, child: ChoiceOfElements) {
        stack.push(child)

        val possibleChoicePaths = child.allElements()

        if (possibleChoicePaths.isNotEmpty()) {

            val pathsToFollow = decidePossibleChoicePathsToFollow(possibleChoicePaths, stack)

            if (pathsToFollow.size > 1) {
                // If not decided, allow decision
                signalThatChoosingAChoicePathIsPossible(stack, (0 until pathsToFollow.size).toList())
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

    private fun decidePossibleChoicePathsToFollow(allPossible: List<StructureElement>,
                                                  stack: TrackStack): List<StructureElement> {
        val decision = decisions.getChoiceDecision(stack.toString()) ?: return allPossible
        return listOf(allPossible[decision.index])
    }
}