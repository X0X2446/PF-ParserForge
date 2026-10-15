package com.extforge.app.ui.testrun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.extforge.app.ui.SpineAction
import com.extforge.app.ui.SpineHeader
import com.extforge.app.ui.SpineScreen
import com.extforge.app.ui.SpineText
import com.extforge.app.ui.theme.Spine
import com.extforge.core.apk.DexConfig
import com.extforge.core.model.CookiePolicy
import com.extforge.core.net.SessionHttpClient
import com.extforge.core.parse.ForgeHtml
import com.extforge.core.store.ExtensionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TestRunViewModel(
    private val repository: ExtensionRepository,
    private val extensionId: String,
) : ViewModel() {
    data class UiState(
        val title: String = "",
        val log: String = "READY",
        val warn: Boolean = false,
        val busy: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val profile = repository.get(extensionId)
        _state.value = UiState(title = profile?.name ?: "MISSING", warn = profile == null)
    }

    fun run() {
        val profile = repository.get(extensionId) ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, log = "FETCHING", warn = false) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { execute(profile) }
            }
            outcome.onSuccess { log ->
                _state.update { it.copy(busy = false, log = log, warn = false) }
            }.onFailure { error ->
                _state.update {
                    it.copy(busy = false, log = error.message ?: "TEST FAILED", warn = true)
                }
            }
        }
    }

    private fun execute(profile: com.extforge.core.model.ExtensionProfile): String {
        val source = profile.canonical()
        val config = ForgeHtml.Config.parse(DexConfig.encode(source))
        val client = SessionHttpClient()
        val lines = ArrayList<String>()
        lines.add("ACCESS  ${source.access.cookiePolicy.name}")
        if (source.access.cookiePolicy == CookiePolicy.NONE) {
            lines.add("COOKIES  OFF")
        }
        val listUrl = config.popularUrl(1)
        val listed = client.fetch(listUrl, source.access, source.baseUrl)
        lines.add("GET ${listed.code}  ${listed.finalUrl}")
        if (ForgeHtml.looksLikeCheck(listed.code, listed.body)) {
            lines.add("THIS LOOKS LIKE A CHECK PAGE. EXTFORGE DID NOT SOLVE IT.")
            lines.add("OPEN THE SITE IN YOUR BROWSER, COPY THE COOKIE HEADER, AND SET ACCESS TO MANUAL.")
            return lines.joinToString("\n")
        }
        val page = ForgeHtml.parseListing(config, listed.body, listed.finalUrl)
        lines.add("ITEMS  ${page.items.size}  NEXT ${page.hasNext}")
        page.items.take(12).forEach { item ->
            lines.add("  ${item.title}  ${item.url}")
        }
        val first = page.items.firstOrNull() ?: return lines.joinToString("\n")
        val detailUrl = source.baseUrl + first.url
        val detailFetch = client.fetch(detailUrl, source.access, source.baseUrl)
        lines.add("DETAIL ${detailFetch.code}  ${detailFetch.finalUrl}")
        val detail = ForgeHtml.parseDetail(config, detailFetch.body, detailFetch.finalUrl)
        lines.add("  ${detail.title}  status ${detail.status}")
        if (detail.description.isNotBlank()) lines.add("  ${detail.description.take(180)}")
        val chapters = ForgeHtml.parseChapters(config, detailFetch.body, detailFetch.finalUrl)
        lines.add("CHAPTERS  ${chapters.size}")
        chapters.take(8).forEach { chapter ->
            lines.add("  ${chapter.number}  ${chapter.name}")
        }
        val chapter = chapters.firstOrNull() ?: return lines.joinToString("\n")
        if (source.pages.image.isBlank()) return lines.joinToString("\n")
        val chapterUrl = source.baseUrl + chapter.url
        val pagesFetch = client.fetch(chapterUrl, source.access, source.baseUrl)
        lines.add("PAGES ${pagesFetch.code}")
        val images = ForgeHtml.parsePages(config, pagesFetch.body, pagesFetch.finalUrl)
        lines.add("IMAGES  ${images.size}")
        images.take(6).forEach { lines.add("  $it") }
        return lines.joinToString("\n")
    }

    companion object {
        fun factory(repository: ExtensionRepository, id: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TestRunViewModel(repository, id) as T
            }
    }
}

@Composable
fun TestRunRoute(repository: ExtensionRepository, extensionId: String, nav: NavHostController) {
    val vm: TestRunViewModel = viewModel(factory = TestRunViewModel.factory(repository, extensionId))
    val state by vm.state.collectAsState()
    SpineScreen {
        SpineHeader(title = "TEST", onBack = { nav.popBackStack() }) {
            SpineText(
                text = state.title,
                color = Spine.Muted,
                size = 11.sp,
                maxLines = 1,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpineAction(
                label = if (state.busy) "RUNNING" else "RUN",
                active = true,
                onClick = vm::run,
            )
            SpineText(
                text = state.log,
                color = if (state.warn) Spine.Warn else Spine.Text,
                size = 12.sp,
            )
        }
    }
}
