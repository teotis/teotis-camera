package com.opencamera.app

internal fun <T> nextListValue(current: T, values: List<T>): T {
    return nextListValueOrNull(current, values) ?: current
}

internal fun <T> nextListValueOrNull(current: T, values: List<T>): T? {
    if (values.isEmpty()) {
        return null
    }
    val currentIndex = values.indexOf(current)
    return if (currentIndex == -1 || currentIndex == values.lastIndex) {
        values.first()
    } else {
        values[currentIndex + 1]
    }
}

internal fun <T> nextNullableListValue(current: T?, values: List<T?>): T? {
    if (values.isEmpty()) {
        return current
    }
    val currentIndex = values.indexOf(current)
    return if (currentIndex == -1 || currentIndex == values.lastIndex) {
        values.first()
    } else {
        values[currentIndex + 1]
    }
}
