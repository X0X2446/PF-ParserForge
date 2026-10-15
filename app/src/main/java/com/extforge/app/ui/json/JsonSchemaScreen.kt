package com.extforge.app.ui.json

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.extforge.app.share.ShareKit
import com.extforge.app.ui.SpineAction
import com.extforge.app.ui.SpineField
import com.extforge.app.ui.SpineHeader
import com.extforge.app.ui.SpineScreen
import com.extforge.app.ui.SpineText
import com.extforge.app.ui.theme.Spine
import com.extforge.core.json.ExtensionSchema
import com.extforge.core.store.ExtensionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class JsonViewModel(
    private val repository: ExtensionRepository,
    private val extensionId: String,
) : ViewModel() {
    data class UiState(val text: String = "", val notice: String = "", val warn: Boolean = false)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val profile = repository.get(extensionId)
        _state.value = if (profile == null) {
            UiState(notice = "MISSING", warn = true)
        } else {
            UiState(text = ExtensionSchema.encode(profile))
        }
    }

    fun fail(message: String) {
        _state.update { it.copy(notice = message, warn = true) }
    }

    fun save(raw: String): Boolean {
        return try {
            val parsed = ExtensionSchema.decode(raw).copy(id = extensionId)
            val next = parsed.canonical()
            repository.upsert(next)
            _state.update { it.copy(text = ExtensionSchema.encode(next), notice = "SAVED", warn = false) }
            true
        } catch (error: Exception) {
            _state.update { it.copy(notice = error.message ?: "INVALID JSON", warn = true) }
            false
        }
    }

    companion object {
        fun factory(repository: ExtensionRepository, id: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    JsonViewModel(repository, id) as T
            }
    }
}

@Composable
fun JsonRoute(repository: ExtensionRepository, extensionId: String, nav: NavHostController) {
    val vm: JsonViewModel = viewModel(factory = JsonViewModel.factory(repository, extensionId))
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var draft by remember { mutableStateOf(state.text) }
    LaunchedEffect(state.text) { draft = state.text }
    SpineScreen {
        SpineHeader(title = "JSON", onBack = { nav.popBackStack() })
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpineField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = false,
            )
            SpineAction(label = "SAVE", active = true, onClick = { vm.save(draft) })
            SpineAction(label = "SHARE JSON", onClick = {
                if (!vm.save(draft)) return@SpineAction
                val profile = repository.get(extensionId) ?: return@SpineAction
                try {
                    val file = ShareKit.writeJson(context, profile)
                    ShareKit.publishDownloads(context, file, ShareKit.JSON)
                    ShareKit.share(context, file, ShareKit.JSON)
                } catch (error: Exception) {
                    vm.fail(error.message ?: "SHARE FAILED")
                }
            })
            if (state.notice.isNotBlank()) {
                SpineText(
                    text = state.notice,
                    color = if (state.warn) Spine.Warn else Spine.Muted,
                    size = 11.sp,
                    maxLines = 4,
                )
            }
        }
    }
}
