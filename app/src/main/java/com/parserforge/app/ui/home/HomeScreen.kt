package com.parserforge.app.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.parserforge.app.ui.SpineAction
import com.parserforge.app.ui.SpineField
import com.parserforge.app.ui.SpineHeader
import com.parserforge.app.ui.SpineScreen
import com.parserforge.app.ui.SpineText
import com.parserforge.app.ui.theme.Spine
import com.parserforge.core.model.ParserProfile
import com.parserforge.core.model.filledCount
import com.parserforge.core.store.ParserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class HomeViewModel(private val repository: ParserRepository) : ViewModel() {
    data class UiState(
        val profiles: List<ParserProfile> = emptyList(),
        val notice: String = "",
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { current ->
            try {
                current.copy(profiles = repository.list())
            } catch (error: Exception) {
                current.copy(notice = error.message ?: "STORE UNREADABLE")
            }
        }
    }

    fun create(name: String, url: String): ParserProfile? {
        val profile = try {
            ParserProfile.create(name, url)
        } catch (error: IllegalArgumentException) {
            _state.update { it.copy(notice = error.message ?: "INVALID URL") }
            return null
        }
        repository.upsert(profile)
        refresh()
        _state.update { it.copy(notice = "CREATED  ${profile.name}") }
        return profile
    }

    fun delete(id: String) {
        repository.delete(id)
        refresh()
        _state.update { it.copy(notice = "DROPPED") }
    }

    companion object {
        fun factory(repository: ParserRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(repository) as T
            }
    }
}

@Composable
fun HomeRoute(repository: ParserRepository, nav: NavHostController) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(repository))
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    HomeScreen(
        state = state,
        onCreate = vm::create,
        onDelete = vm::delete,
        onEditJson = { nav.navigate("json/$it") },
        onTest = { nav.navigate("test/$it") },
        onBuild = { nav.navigate("builder/$it") },
    )
}

@Composable
private fun HomeScreen(
    state: HomeViewModel.UiState,
    onCreate: (String, String) -> ParserProfile?,
    onDelete: (String) -> Unit,
    onEditJson: (String) -> Unit,
    onTest: (String) -> Unit,
    onBuild: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDrop by rememberSaveable { mutableStateOf<String?>(null) }
    SpineScreen {
        SpineHeader(title = "PARSERFORGE") {
            SpineText(
                text = "SCRAPERS  ${state.profiles.size}",
                color = Spine.Muted,
                size = 11.sp,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpineText(text = "NAME", color = Spine.Muted, size = 11.sp)
            SpineField(value = name, onValueChange = { name = it }, hint = "source name")
            SpineText(text = "URL", color = Spine.Muted, size = 11.sp)
            SpineField(
                value = url,
                onValueChange = { url = it },
                hint = "https://example.com",
                onDone = {
                    onCreate(name, url)?.let { created ->
                        name = ""
                        url = ""
                        openId = created.id
                    }
                },
            )
            SpineAction(
                label = "CREATE",
                active = true,
                onClick = {
                    onCreate(name, url)?.let { created ->
                        name = ""
                        url = ""
                        openId = created.id
                    }
                },
            )
            if (state.notice.isNotBlank()) {
                SpineText(text = state.notice, color = Spine.Muted, size = 11.sp, maxLines = 2)
            }
            if (state.profiles.isEmpty()) {
                SpineText(text = "NO SCRAPERS", color = Spine.Muted, size = 12.sp)
            }
            state.profiles.forEach { profile ->
                ParserRow(
                    profile = profile,
                    open = openId == profile.id,
                    confirmingDrop = confirmDrop == profile.id,
                    onToggle = {
                        openId = if (openId == profile.id) null else profile.id
                        confirmDrop = null
                    },
                    onEditJson = { onEditJson(profile.id) },
                    onTest = { onTest(profile.id) },
                    onBuild = { onBuild(profile.id) },
                    onAskDrop = { confirmDrop = profile.id },
                    onCancelDrop = { confirmDrop = null },
                    onDelete = {
                        onDelete(profile.id)
                        confirmDrop = null
                        if (openId == profile.id) openId = null
                    },
                )
            }
        }
    }
}

@Composable
private fun ParserRow(
    profile: ParserProfile,
    open: Boolean,
    confirmingDrop: Boolean,
    onToggle: () -> Unit,
    onEditJson: () -> Unit,
    onTest: () -> Unit,
    onBuild: () -> Unit,
    onAskDrop: () -> Unit,
    onCancelDrop: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (open) Spine.Accent else Spine.Grey)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(modifier = Modifier.clickable(role = Role.Button, onClick = onToggle)) {
            SpineText(text = profile.name.ifBlank { "untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            SpineText(
                text = profile.entryUrl,
                color = Spine.Muted,
                size = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SpineText(
                text = "${profile.selectors.filledCount()}/4 SELECTORS",
                color = Spine.Muted,
                size = 11.sp,
            )
        }
        if (open) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SpineAction(label = "EDIT JSON", onClick = onEditJson)
                SpineAction(label = "TEST RUN", onClick = onTest)
                SpineAction(label = "LAUNCH VISUAL BUILDER", active = true, onClick = onBuild)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (confirmingDrop) {
                    SpineAction(label = "CONFIRM DROP", onClick = onDelete)
                    SpineAction(label = "CANCEL", onClick = onCancelDrop)
                } else {
                    SpineAction(label = "DROP", onClick = onAskDrop)
                }
            }
        }
    }
}
