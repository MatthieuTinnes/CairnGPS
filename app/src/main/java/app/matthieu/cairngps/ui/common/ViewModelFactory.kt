package app.matthieu.cairngps.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

/**
 * Single-instance [ViewModelProvider.Factory] wrapping [create]. The app wires up its
 * ViewModels by hand (no DI framework), so every `XxxViewModel.factory(...)` companion function
 * across the app reduces to this one call instead of repeating the anonymous-object boilerplate.
 */
fun <T : ViewModel> factoryOf(create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>, extras: CreationExtras): VM {
            return create() as VM
        }
    }
