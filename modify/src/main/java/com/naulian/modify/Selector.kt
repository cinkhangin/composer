package com.naulian.modify

interface Selectable<T> {
    fun copyData(selected: Boolean): T
}

data class SelectableItem<T>(
    val item: T,
    val selected: Boolean = false

) : Selectable<SelectableItem<T>> {
    override fun copyData(selected: Boolean): SelectableItem<T> {
        return copy(selected = selected)
    }
}

fun <T : Selectable<T>> List<T>.singleSelectBy(
    selector: (T) -> Boolean,
) = map { it.copyData(selector(it)) }

fun <T : Selectable<T>> List<T>.selectAll(value: Boolean) = map { it.copyData(selected = value) }


fun <T : Selectable<T>> List<T>.singleSelectByIndex(
    selector: (Int) -> Boolean,
) = mapIndexed { index, item ->
    item.copyData(selector(index))
}


fun <T : Selectable<T>> List<T>.multiSelectBy(
    selector: (T) -> Pair<Boolean, Boolean>,
) = map {
    val (isSelect, selected) = selector(it)
    it.copyData(isSelect xor selected)
}