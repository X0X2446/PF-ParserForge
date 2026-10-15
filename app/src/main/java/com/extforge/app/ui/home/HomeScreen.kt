package com.extforge.app.ui.home

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
import com.extforge.core.model.ExtensionProfile
import com.extforge.core.store.ExtensionRepository
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(private val repository: ExtensionRepository) : ViewModel() {
    data class UiState(
        val profiles: List<ExtensionProfile> = emptyList(),
        val notice: String = "",
        val busy: Boolean = false,
        val warn: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { current ->
            try {
                current.copy(profiles = repository.list(), warn = false)
            } catch (error: Exception) {
                current.copy(notice = error.message ?: "STORE UNREADABLE", warn = true)
            }
        }
    }

    fun create(name: String, lang: String, url: String): ExtensionProfile? {
        val profile = try {
            ExtensionProfile.create(name, lang, url)
        } catch (error: IllegalArgumentException) {
            _state.update { it.copy(notice = error.message ?: "INVALID URL", warn = true) }
            return null
        }
        repository.upsert(profile)
        refresh()
        _state.update { it.copy(notice = "CREATED  ${profile.name}", warn = false) }
        return profile
    }

    fun delete(id: String) {
        repository.delete(id)
        refresh()
        _state.update { it.copy(notice = "DROPPED", warn = false) }
    }

    fun import(raw: String) {
        try {
            val profiles = ExtensionSchema.decodeAny(raw)
            profiles.forEach { repository.upsert(it) }
            refresh()
            _state.update { it.copy(notice = "IMPORTED  ${profiles.size}", warn = false) }
        } catch (error: Exception) {
            _state.update { it.copy(notice = error.message ?: "IMPORT FAILED", warn = true) }
        }
    }

    fun shareJson(context: Context, id: String) {
        val profile = repository.get(id) ?: return
        try {
            val file = ShareKit.writeJson(context, profile)
            ShareKit.publishDownloads(context, file, ShareKit.JSON)
            ShareKit.share(context, file, ShareKit.JSON)
            _state.update { it.copy(notice = "JSON  ${file.name}", warn = false) }
        } catch (error: Exception) {
            _state.update { it.copy(notice = error.message ?: "SHARE FAILED", warn = true) }
        }
    }

    fun export(context: Context, id: String, install: Boolean) {
        val profile = repository.get(id) ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, notice = "BUILDING APK", warn = false) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { ShareKit.exportApk(context, repository, profile) }
            }
            refresh()
            outcome.onSuccess { exported ->
                try {
                    if (install) ShareKit.install(context, exported.file)
                    else ShareKit.share(context, exported.file, ShareKit.APK)
                    _state.update { it.copy(busy = false, notice = exported.notice, warn = false) }
                } catch (error: Exception) {
                    _state.update {
                        it.copy(busy = false, notice = error.message ?: "SHARE FAILED", warn = true)
                    }
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(busy = false, notice = error.message ?: "EXPORT FAILED", warn = true)
                }
            }
        }
    }

    companion object {
        fun factory(repository: ExtensionRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(repository) as T
            }
    }
}

@Composable
fun HomeRoute(repository: ExtensionRepository, nav: NavHostController) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(repository))
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.refresh() }
    HomeScreen(
        state = state,
        onCreate = vm::create,
        onDelete = vm::delete,
        onImport = vm::import,
        onEdit = { nav.navigate("edit/$it") },
        onJson = { nav.navigate("json/$it") },
        onTest = { nav.navigate("test/$it") },
        onShareJson = { vm.shareJson(context, it) },
        onShareApk = { vm.export(context, it, install = false) },
        onInstall = { vm.export(context, it, install = true) },
    )
}

@Composable
private fun HomeScreen(
    state: HomeViewModel.UiState,
    onCreate: (String, String, String) -> ExtensionProfile?,
    onDelete: (String) -> Unit,
    onImport: (String) -> Unit,
    onEdit: (String) -> Unit,
    onJson: (String) -> Unit,
    onTest: (String) -> Unit,
    onShareJson: (String) -> Unit,
    onShareApk: (String) -> Unit,
    onInstall: (String) -> Unit,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var lang by rememberSaveable { mutableStateOf("en") }
    var url by rememberSaveable { mutableStateOf("") }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDrop by rememberSaveable { mutableStateOf<String?>(null) }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
        if (text != null) onImport(text)
    }
    SpineScreen {
        SpineHeader(title = "EXTFORGE") {
            SpineText(
                text = "SOURCES  ${state.profiles.size}",
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
            SpineText(text = "// NEW SOURCE", color = Spine.Accent, size = 12.sp)
            SpineField(value = name, onValueChange = { name = it }, hint = "name")
            SpineField(value = lang, onValueChange = { lang = it }, hint = "en")
            SpineField(
                value = url,
                onValueChange = { url = it },
                hint = "https://example.com",
                onDone = {
                    onCreate(name, lang, url)?.let { created ->
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
                    onCreate(name, lang, url)?.let { created ->
                        name = ""
                        url = ""
                        openId = created.id
                    }
                },
            )
            SpineAction(label = "IMPORT JSON", onClick = {
                opener.launch(arrayOf("application/json", "text/plain", "*/*"))
            })
            if (state.notice.isNotBlank()) {
                SpineText(
                    text = state.notice,
                    color = if (state.warn) Spine.Warn else Spine.Muted,
                    size = 11.sp,
                    maxLines = 4,
                )
            }
            if (state.profiles.isEmpty()) {
                SpineText(text = "NO SOURCES", color = Spine.Muted, size = 12.sp)
            }
            state.profiles.forEach { profile ->
                SourceRow(
                    profile = profile,
                    open = openId == profile.id,
                    confirmingDrop = confirmDrop == profile.id,
                    busy = state.busy,
                    onToggle = {
                        openId = if (openId == profile.id) null else profile.id
                        confirmDrop = null
                    },
                    onEdit = { onEdit(profile.id) },
                    onTest = { onTest(profile.id) },
                    onJson = { onJson(profile.id) },
                    onShareJson = { onShareJson(profile.id) },
                    onShareApk = { onShareApk(profile.id) },
                    onInstall = { onInstall(profile.id) },
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
private fun SourceRow(
    profile: ExtensionProfile,
    open: Boolean,
    confirmingDrop: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onJson: () -> Unit,
    onShareJson: () -> Unit,
    onShareApk: () -> Unit,
    onInstall: () -> Unit,
    onAskDrop: () -> Unit,
    onCancelDrop: () -> Unit,
    onDelete: () -> Unit,
) {
    val host = runCatching { URI(profile.baseUrl).host }.getOrNull().orEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Spine.Line),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Column(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onToggle,
                )
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SpineText(text = profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            SpineText(
                text = "${profile.lang}  $host  ${profile.access.cookiePolicy.name}  ${profile.selectorCount()}/7  v${profile.apkVersionCode}",
                color = Spine.Muted,
                size = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (open) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SpineAction(label = "EDIT", active = true, onClick = onEdit)
                SpineAction(label = "TEST RUN", onClick = onTest)
                SpineAction(label = "JSON", onClick = onJson)
                SpineAction(label = "SHARE JSON", onClick = onShareJson)
                SpineAction(label = if (busy) "BUILDING" else "SHARE APK", onClick = onShareApk)
                SpineAction(label = "INSTALL APK", onClick = onInstall)
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
