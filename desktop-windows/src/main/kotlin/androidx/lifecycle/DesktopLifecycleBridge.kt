@file:Suppress("PackageDirectoryMismatch")

package androidx.lifecycle

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

open class ViewModel {
    internal val internalScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    protected open fun onCleared() {}
}

val ViewModel.viewModelScope: CoroutineScope
    get() = this.internalScope

open class AndroidViewModel(val application: Application) : ViewModel()

class ViewModelProvider {
    interface Factory {
        fun <T : ViewModel> create(modelClass: Class<T>): T
    }
}
