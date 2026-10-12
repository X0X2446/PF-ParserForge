package com.parserforge.app.ui.json

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.parserforge.app.ui.SpineAction
import com.parserforge.app.ui.SpineHeader
import com.parserforge.app.ui.SpineScreen
import com.parserforge.app.ui.SpineText
import com.parserforge.app.ui.theme.Spine
import com.parserforge.core.json.ParserSchema
import com.parserforge.core.json.ParserSchemaException
import com.parserforge.core.net.Urls
import com.parserforge.core.store.ParserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class JsonViewModel(
    private val repository: ParserRepository,
    private val parserId: String,
) : ViewModel() {
    data class UiState(
        val text: String = "",
        val notice: String = "",
        val missing: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val profile = repository.get(parserId)
        _state.value = if (profile == null) {
            UiState(missing = true, notice = "MISSING PARSER")
        } else {
            UiState(text = ParserSchema.encode(profile))
        }
    }

    fun save(raw: String) {
        try {
            val parsed = ParserSchema.decode(raw).copy(id = parserId)
            val url = Urls.normalize(parsed.entryUrl)
                ?: throw ParserSchemaException("entryUrl must be http or https")
            val stored = parsed.copy(entryUrl = url).canonical()
            repository.upsert(stored)
            _state.update { it.copy(text = ParserSchema.encode(stored), notice = "SAVED") }
        } catch (error: Exception) {
            _state.update { it.copy(notice = error.message ?: "INVALID JSON") }
        }
    }

    fun reload() {
        val profile = repository.get(parserId) ?: return
        _state.update { it.copy(text = ParserSchema.encode(profile), notice = "RELOADED") }
    }

    companion object {
        fun factory(repository: ParserRepository, id: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    JsonViewModel(repository, id) as T
            }
    }
}

@Composable
fun JsonRoute(repository: ParserRepository, parserId: String, nav: NavHostController) {
    val vm: JsonViewModel = viewModel(
        key = "json-$parserId",
        factory = JsonViewModel.factory(repository, parserId),
    )
    val state by vm.state.collectAsState()
    JsonScreen(
        state = state,
        onBack = { nav.popBackStack() },
        onSave = vm::save,
        onReload = vm::reload,
    )
}

@Composable
private fun JsonScreen(
    state: JsonViewModel.UiState,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
    onReload: () -> Unit,
) {
    val scroll = rememberScrollState()
    var draft by remember { mutableStateOf(state.text) }
    LaunchedEffect(state.text) { draft = state.text }
    SpineScreen {
        SpineHeader(title = "JSON SCHEMA", onBack = onBack) {
            if (!state.missing) {
                SpineAction(label = "RELOAD", onClick = onReload)
                SpineAction(label = "SAVE", active = true, modifier = Modifier.padding(start = 6.dp), onClick = {
                    onSave(draft)
                })
            }
        }
        if (state.notice.isNotBlank()) {
            SpineText(
                text = state.notice,
                color = Spine.Muted,
                size = 11.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        if (state.missing) {
            SpineText(text = "MISSING PARSER", color = Spine.Muted, modifier = Modifier.padding(8.dp))
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, Spine.Grey)
                        .verticalScroll(scroll)
                        .padding(8.dp),
                    textStyle = TextStyle(
                        color = Spine.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                    cursorBrush = SolidColor(Spine.Accent),
                )
            }
        }
    }
}
