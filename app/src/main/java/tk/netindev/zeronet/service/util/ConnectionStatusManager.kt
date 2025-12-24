package tk.netindev.zeronet.service.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ConnectionStatusManager {
    private val _status: MutableStateFlow<ConnectionStatus> = MutableStateFlow(ConnectionStatus.LEVEL_NOT_CONNECTED)
    val status: StateFlow<ConnectionStatus> = _status

    @JvmStatic
    fun setStatus(newStatus: ConnectionStatus) {
        _status.value = newStatus
    }
}
