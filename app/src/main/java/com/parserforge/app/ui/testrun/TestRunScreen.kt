package com.parserforge.app.ui.testrun

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.parserforge.app.ui.SpineAction
import com.parserforge.app.ui.SpineHeader
import com.parserforge.app.ui.SpineScreen
import com.parserforge.app.ui.SpineText
import com.parserforge.app.ui.theme.Spine
import com.parserforge.core.model.ParserProfile
import com.parserforge.core.net.SessionHttpClient
import com.parserforge.core.parse.FieldPreview
import com.parserforge.core.parse.JsoupPreviewEngine
import com.parserforge.core.store.ParserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TestRunViewModel(
    private val repository: ParserRepository,
    private val parserId: String,
    private val http: SessionHttpClient = SessionHttpClient(),
) : ViewModel() {
    data class UiState(
        val profile: ParserProfile? = null,
        val missing: Boolean = false,
        val running: Boolean = false,
        val notice: String = "",
        val code: Int? = null,
        val finalUrl: String = "",
        val title: String = "",
        val bytes: Int = 0,
        val previews: List<FieldPreview> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val profile = repository.get(parserId)
        _state.value = if (profile == null) {
            UiState(missing = true, notice = "MISSING PARSER")
        } else {
            UiState(profile = profile, notice = "IDLE")
        }
    }

    fun run() {
        val profile = _state.value.profile ?: return
        if (_state.value.running) return
        viewModelScope.launch {
            _state.update { it.copy(running = true, notice = "GET") }
            try {
                val result = withContext(Dispatchers.IO) {
                    http.fetch(profile.entryUrl, profile.session)
                }
                val digest = withContext(Dispatchers.Default) {
                    JsoupPreviewEngine.inspect(result.body, result.finalUrl, profile.boundFields())
                }
                _state.update {
                    it.copy(
                        running = false,
                        code = result.code,
                        finalUrl = result.finalUrl,
                        title = digest.title,
                        bytes = result.body.length,
                        previews = digest.previews,
                        notice = "HTTP ${result.code}",
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(running = false, notice = error.message ?: "REQUEST FAILED") }
            }
        }
    }

    companion object {
        fun factory(repository: ParserRepository, id: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TestRunViewModel(repository, id) as T
            }
    }
}

@Composable
fun TestRunRoute(repository: ParserRepository, parserId: String, nav: NavHostController) {
    val vm: TestRunViewModel = viewModel(
        key = "test-$parserId",
        factory = TestRunViewModel.factory(repository, parserId),
    )
    val state by vm.state.collectAsState()
    TestRunScreen(state = state, onBack = { nav.popBackStack() }, onRun = vm::run)
}

@Composable
private fun TestRunScreen(
    state: TestRunViewModel.UiState,
    onBack: () -> Unit,
    onRun: () -> Unit,
) {
    val profile = state.profile
    SpineScreen {
        SpineHeader(title = "TEST RUN", onBack = onBack) {
            if (profile != null) {
                SpineAction(
                    label = if (state.running) "WAIT" else "RUN",
                    active = !state.running,
                    onClick = { if (!state.running) onRun() },
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpineText(text = state.notice, color = Spine.Muted, size = 11.sp, maxLines = 3)
            if (profile == null) {
                SpineText(text = "MISSING PARSER", color = Spine.Muted)
                return@Column
            }
            SpineText(text = profile.name.ifBlank { "untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            SpineText(text = profile.entryUrl, color = Spine.Muted, size = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            SpineText(
                text = "UA  ${if (profile.session.userAgent.isBlank()) "DEFAULT" else "CAPTURED"}",
                color = Spine.Muted,
                size = 11.sp,
            )
            SpineText(
                text = "COOKIES  ${profile.session.cookies.size}",
                color = Spine.Muted,
                size = 11.sp,
            )
            SpineText(
                text = "CF_CLEARANCE  ${if (profile.session.hasClearance()) "YES" else "NO"}",
                color = if (profile.session.hasClearance()) Spine.Accent else Spine.Muted,
                size = 11.sp,
            )
            if (state.code != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Spine.Grey)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SpineText(text = "HTTP  ${state.code}    ${state.bytes} CHARS", size = 12.sp)
                    if (state.finalUrl.isNotBlank()) {
                        SpineText(text = state.finalUrl, color = Spine.Muted, size = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (state.title.isNotBlank()) {
                        SpineText(text = "TITLE  ${state.title}", size = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            state.previews.forEach { preview ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Spine.Grey)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SpineText(text = preview.label.uppercase(), size = 12.sp)
                    SpineText(
                        text = if (preview.error != null) "ERR  ${preview.error}" else "${preview.matchCount} MATCHES",
                        color = Spine.Muted,
                        size = 11.sp,
                        maxLines = 2,
                    )
                    preview.values.take(8).forEach { value ->
                        SpineText(text = value, size = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (preview.error == null && preview.values.isEmpty() && preview.matchCount == 0) {
                        val selector = profile.boundFields().firstOrNull { it.key == preview.key }?.selector.orEmpty()
                        if (selector.isNotBlank()) {
                            SpineText(text = "NO VALUES", color = Spine.Muted, size = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
