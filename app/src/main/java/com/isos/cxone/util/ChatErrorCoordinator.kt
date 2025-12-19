package com.isos.cxone.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ChatErrorCoordinator {
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors = _errors.asSharedFlow()

    fun emitError(errorType: String) {
        _errors.tryEmit(errorType)
    }
}