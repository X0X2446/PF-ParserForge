package com.extforge.app.ui.editor

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.extforge.core.model.AccessPolicy
import com.extforge.core.model.ChapterOrder
import com.extforge.core.model.CookiePolicy
import com.extforge.core.model.ExtensionProfile
import com.extforge.core.model.ImageAttr
import com.extforge.core.model.PageSelectors
import com.extforge.core.model.RefererMode
import com.extforge.core.store.ExtensionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(
    private val repository: ExtensionRepository,
    private val extensionId: String,
) : ViewModel() {
    data class UiState(
        val profile: ExtensionProfile? = null,
        val notice: String = "",
        val warn: Boolean = false,
        val busy: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        _state.value = UiState(profile = repository.get(extensionId), notice = if (repository.get(extensionId) == null) "MISSING" else "")
    }

    fun edit(block: (ExtensionProfile) -> ExtensionProfile) {
        _state.update { current ->
            val profile = current.profile ?: return@update current
            current.copy(profile = block(profile))
        }
    }

    fun save(): ExtensionProfile? {
        val profile = _state.value.profile ?: return null
        return try {
            val next = profile.canonical()
            repository.upsert(next)
            _state.update { it.copy(profile = next, notice = "SAVED", warn = false) }
            next
        } catch (error: Exception) {
            _state.update { it.copy(notice = error.message ?: "INVALID", warn = true) }
            null
        }
    }

    fun shareJson(context: Context) {
        val saved = save() ?: return
        try {
            val file = ShareKit.writeJson(context, saved)
            ShareKit.publishDownloads(context, file, ShareKit.JSON)
            ShareKit.share(context, file, ShareKit.JSON)
            _state.update { it.copy(notice = "JSON  ${file.name}", warn = false) }
        } catch (error: Exception) {
            _state.update { it.copy(notice = error.message ?: "SHARE FAILED", warn = true) }
        }
    }

    fun export(context: Context, install: Boolean) {
        val saved = save() ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, notice = "BUILDING APK", warn = false) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { ShareKit.exportApk(context, repository, saved) }
            }
            val refreshed = repository.get(extensionId)
            outcome.onSuccess { exported ->
                try {
                    if (install) ShareKit.install(context, exported.file)
                    else ShareKit.share(context, exported.file, ShareKit.APK)
                    _state.update {
                        it.copy(profile = refreshed, busy = false, notice = exported.notice, warn = false)
                    }
                } catch (error: Exception) {
                    _state.update {
                        it.copy(
                            profile = refreshed,
                            busy = false,
                            notice = error.message ?: "SHARE FAILED",
                            warn = true,
                        )
                    }
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(profile = refreshed, busy = false, notice = error.message ?: "EXPORT FAILED", warn = true)
                }
            }
        }
    }

    companion object {
        fun factory(repository: ExtensionRepository, id: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    EditorViewModel(repository, id) as T
            }
    }
}

@Composable
fun EditorRoute(repository: ExtensionRepository, extensionId: String, nav: NavHostController) {
    val vm: EditorViewModel = viewModel(factory = EditorViewModel.factory(repository, extensionId))
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    EditorScreen(
        state = state,
        onBack = { nav.popBackStack() },
        onEdit = vm::edit,
        onSave = { vm.save() },
        onTest = {
            if (vm.save() != null) nav.navigate("test/$extensionId")
        },
        onShareJson = { vm.shareJson(context) },
        onShareApk = { vm.export(context, install = false) },
        onInstall = { vm.export(context, install = true) },
    )
}

@Composable
private fun EditorScreen(
    state: EditorViewModel.UiState,
    onBack: () -> Unit,
    onEdit: ((ExtensionProfile) -> ExtensionProfile) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onShareJson: () -> Unit,
    onShareApk: () -> Unit,
    onInstall: () -> Unit,
) {
    val profile = state.profile
    SpineScreen {
        SpineHeader(title = "EDIT", onBack = onBack) {
            SpineText(text = profile?.lang ?: "", color = Spine.Muted, size = 11.sp)
        }
        if (profile == null) {
            SpineText(
                text = "MISSING",
                color = Spine.Warn,
                modifier = Modifier.padding(8.dp),
            )
            return@SpineScreen
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section("IDENTITY")
            SpineField(profile.name, { value -> onEdit { it.copy(name = value) } }, "name")
            SpineField(profile.lang, { value -> onEdit { it.copy(lang = value) } }, "en")
            SpineField(profile.baseUrl, { value -> onEdit { it.copy(baseUrl = value) } }, "https://example.com")
            SpineField(
                profile.versionId.toString(),
                { value -> onEdit { it.copy(versionId = value.toIntOrNull() ?: it.versionId) } },
                "version id",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpineAction(
                    label = "SAFE",
                    active = !profile.nsfw,
                    modifier = Modifier.weight(1f),
                    onClick = { onEdit { it.copy(nsfw = false) } },
                )
                SpineAction(
                    label = "NSFW",
                    active = profile.nsfw,
                    modifier = Modifier.weight(1f),
                    onClick = { onEdit { it.copy(nsfw = true) } },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpineAction(
                    label = "NO LATEST",
                    active = !profile.supportsLatest,
                    modifier = Modifier.weight(1f),
                    onClick = { onEdit { it.copy(supportsLatest = false) } },
                )
                SpineAction(
                    label = "LATEST",
                    active = profile.supportsLatest,
                    modifier = Modifier.weight(1f),
                    onClick = { onEdit { it.copy(supportsLatest = true) } },
                )
            }

            Section("ACCESS")
            SpineText(
                text = "NONE SENDS NO COOKIES. MANUAL SENDS ONLY THE HEADER YOU PASTE. CHECK PAGES ARE NOT SOLVED.",
                color = Spine.Muted,
                size = 11.sp,
                maxLines = 4,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpineAction(
                    label = "NO COOKIES",
                    active = profile.access.cookiePolicy == CookiePolicy.NONE,
                    modifier = Modifier.weight(1f),
                    onClick = { onEdit { it.copy(access = it.access.copy(cookiePolicy = CookiePolicy.NONE)) } },
                )
                SpineAction(
                    label = "MANUAL",
                    active = profile.access.cookiePolicy == CookiePolicy.MANUAL,
                    modifier = Modifier.weight(1f),
                    onClick = { onEdit { it.copy(access = it.access.copy(cookiePolicy = CookiePolicy.MANUAL)) } },
                )
            }
            SpineField(
                profile.access.userAgent,
                { value -> onEdit { it.copy(access = it.access.copy(userAgent = value)) } },
                "user agent, blank uses Mihon's",
            )
            if (profile.access.cookiePolicy == CookiePolicy.MANUAL) {
                SpineField(
                    profile.access.cookieHeader,
                    { value -> onEdit { it.copy(access = it.access.copy(cookieHeader = value)) } },
                    "cookie header",
                    singleLine = false,
                )
            }
            SpineField(
                profile.access.delayMs.toString(),
                { value ->
                    onEdit {
                        it.copy(access = it.access.copy(delayMs = value.toIntOrNull() ?: 0))
                    }
                },
                "delay ms, 0-10000",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RefererChip(profile.access, RefererMode.NONE, "REF NONE", onEdit, Modifier.weight(1f))
                RefererChip(profile.access, RefererMode.BASE, "REF BASE", onEdit, Modifier.weight(1f))
                RefererChip(profile.access, RefererMode.PAGE, "REF PAGE", onEdit, Modifier.weight(1f))
            }

            Section("LIST")
            SpineText(
                text = "TEMPLATES USE {base} {page} {query}. NEXT ONLY MARKS THAT ANOTHER PAGE EXISTS.",
                color = Spine.Muted,
                size = 11.sp,
                maxLines = 3,
            )
            Labeled("POPULAR", profile.list.popularTemplate) { value ->
                onEdit { it.copy(list = it.list.copy(popularTemplate = value)) }
            }
            Labeled("SEARCH", profile.list.searchTemplate) { value ->
                onEdit { it.copy(list = it.list.copy(searchTemplate = value)) }
            }
            Labeled("LATEST", profile.list.latestTemplate) { value ->
                onEdit { it.copy(list = it.list.copy(latestTemplate = value)) }
            }
            Labeled("NEXT", profile.list.nextPage) { value ->
                onEdit { it.copy(list = it.list.copy(nextPage = value)) }
            }
            Labeled("ITEM", profile.list.item) { value ->
                onEdit { it.copy(list = it.list.copy(item = value)) }
            }
            Labeled("TITLE", profile.list.title) { value ->
                onEdit { it.copy(list = it.list.copy(title = value)) }
            }
            Labeled("URL", profile.list.url) { value ->
                onEdit { it.copy(list = it.list.copy(url = value)) }
            }
            Labeled("COVER", profile.list.cover) { value ->
                onEdit { it.copy(list = it.list.copy(cover = value)) }
            }

            Section("DETAILS")
            Labeled("TITLE", profile.details.title) { value ->
                onEdit { it.copy(details = it.details.copy(title = value)) }
            }
            Labeled("DESCRIPTION", profile.details.description) { value ->
                onEdit { it.copy(details = it.details.copy(description = value)) }
            }
            Labeled("AUTHOR", profile.details.author) { value ->
                onEdit { it.copy(details = it.details.copy(author = value)) }
            }
            Labeled("ARTIST", profile.details.artist) { value ->
                onEdit { it.copy(details = it.details.copy(artist = value)) }
            }
            Labeled("GENRE", profile.details.genre) { value ->
                onEdit { it.copy(details = it.details.copy(genre = value)) }
            }
            Labeled("STATUS", profile.details.status) { value ->
                onEdit { it.copy(details = it.details.copy(status = value)) }
            }
            Labeled("ONGOING WORDS", profile.details.ongoingWords) { value ->
                onEdit { it.copy(details = it.details.copy(ongoingWords = value)) }
            }
            Labeled("COMPLETED WORDS", profile.details.completedWords) { value ->
                onEdit { it.copy(details = it.details.copy(completedWords = value)) }
            }

            Section("CHAPTERS")
            Labeled("ITEM", profile.chapters.item) { value ->
                onEdit { it.copy(chapters = it.chapters.copy(item = value)) }
            }
            Labeled("NAME", profile.chapters.name) { value ->
                onEdit { it.copy(chapters = it.chapters.copy(name = value)) }
            }
            Labeled("URL", profile.chapters.url) { value ->
                onEdit { it.copy(chapters = it.chapters.copy(url = value)) }
            }
            Labeled("DATE", profile.chapters.date) { value ->
                onEdit { it.copy(chapters = it.chapters.copy(date = value)) }
            }
            Labeled("DATE PATTERN", profile.chapters.datePattern) { value ->
                onEdit { it.copy(chapters = it.chapters.copy(datePattern = value)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpineAction(
                    label = "DOCUMENT",
                    active = profile.chapters.order == ChapterOrder.DOCUMENT,
                    modifier = Modifier.weight(1f),
                    onClick = { onEdit { it.copy(chapters = it.chapters.copy(order = ChapterOrder.DOCUMENT)) } },
                )
                SpineAction(
                    label = "REVERSE",
                    active = profile.chapters.order == ChapterOrder.REVERSE,
                    modifier = Modifier.weight(1f),
                    onClick = { onEdit { it.copy(chapters = it.chapters.copy(order = ChapterOrder.REVERSE)) } },
                )
            }

            Section("PAGES")
            Labeled("IMAGE", profile.pages.image) { value ->
                onEdit { it.copy(pages = it.pages.copy(image = value)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AttrChip(profile.pages, ImageAttr.AUTO, "AUTO", onEdit, Modifier.weight(1f))
                AttrChip(profile.pages, ImageAttr.SRC, "SRC", onEdit, Modifier.weight(1f))
                AttrChip(profile.pages, ImageAttr.DATA_SRC, "DATA", onEdit, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AttrChip(profile.pages, ImageAttr.DATA_LAZY, "LAZY", onEdit, Modifier.weight(1f))
                AttrChip(profile.pages, ImageAttr.SRCSET, "SRCSET", onEdit, Modifier.weight(1f))
            }

            SpineAction(label = "SAVE", active = true, onClick = onSave)
            SpineAction(label = "TEST RUN", onClick = onTest)
            SpineAction(label = "SHARE JSON", onClick = onShareJson)
            SpineAction(label = if (state.busy) "BUILDING" else "SHARE APK", onClick = onShareApk)
            SpineAction(label = "INSTALL APK", onClick = onInstall)
            if (state.notice.isNotBlank()) {
                SpineText(
                    text = state.notice,
                    color = if (state.warn) Spine.Warn else Spine.Muted,
                    size = 11.sp,
                    maxLines = 4,
                )
            }
            SpineText(
                text = profile.packageName() + "  " + profile.versionName(),
                color = Spine.Muted,
                size = 11.sp,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    SpineText(text = "// $title", color = Spine.Accent, size = 12.sp)
}

@Composable
private fun Labeled(label: String, value: String, onValue: (String) -> Unit) {
    SpineText(text = label, color = Spine.Muted, size = 11.sp)
    SpineField(value = value, onValueChange = onValue, hint = label.lowercase())
}

@Composable
private fun RefererChip(
    access: AccessPolicy,
    mode: RefererMode,
    label: String,
    onEdit: ((ExtensionProfile) -> ExtensionProfile) -> Unit,
    modifier: Modifier,
) {
    SpineAction(
        label = label,
        active = access.referer == mode,
        modifier = modifier,
        onClick = { onEdit { it.copy(access = it.access.copy(referer = mode)) } },
    )
}

@Composable
private fun AttrChip(
    pages: PageSelectors,
    attr: ImageAttr,
    label: String,
    onEdit: ((ExtensionProfile) -> ExtensionProfile) -> Unit,
    modifier: Modifier,
) {
    SpineAction(
        label = label,
        active = pages.attribute == attr,
        modifier = modifier,
        onClick = { onEdit { it.copy(pages = it.pages.copy(attribute = attr)) } },
    )
}
