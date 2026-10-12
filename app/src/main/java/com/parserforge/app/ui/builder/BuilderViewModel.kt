package com.parserforge.app.ui.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.parserforge.core.model.ElementCapture
import com.parserforge.core.model.ParserProfile
import com.parserforge.core.model.SelectorKey
import com.parserforge.core.model.SessionSnapshot
import com.parserforge.core.model.filledCount
import com.parserforge.core.net.CookieHeader
import com.parserforge.core.net.Urls
import com.parserforge.core.parse.FieldPreview
import com.parserforge.core.parse.JsoupPreviewEngine
import com.parserforge.core.store.ParserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BuilderViewModel(
    private val repository: ParserRepository,
    private val parserId: String,
) : ViewModel() {
    data class UiState(
        val profile: ParserProfile? = null,
        val missing: Boolean = false,
        val pickMode: Boolean = false,
        val capture: ElementCapture? = null,
        val previews: Map<String, FieldPreview> = emptyMap(),
        val status: String = "IDLE",
        val snapshotTicket: Int = 0,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var lastHtml: String = ""
    private var lastPage: String = ""
    private var previewJob: Job? = null

    init {
        val profile = repository.get(parserId)
        _state.value = if (profile == null) {
            UiState(missing = true, status = "MISSING PARSER")
        } else {
            UiState(profile = profile, status = "READY")
        }
    }

    fun setPickMode(enabled: Boolean) {
        _state.update { it.copy(pickMode = enabled, status = if (enabled) "PICK" else "READY") }
    }

    fun onCapture(capture: ElementCapture) {
        _state.update { it.copy(capture = capture, status = "PICKED") }
    }

    fun assign(key: SelectorKey) {
        val selector = _state.value.capture?.selector?.trim().orEmpty()
        if (selector.isEmpty()) {
            _state.update { it.copy(status = "PICK AN ELEMENT FIRST") }
            return
        }
        edit { profile -> profile.copy(selectors = key.assign(profile.selectors, selector)) }
        _state.update { it.copy(status = "ASSIGNED  ${key.label}", snapshotTicket = it.snapshotTicket + 1) }
    }

    fun clear(key: SelectorKey) {
        edit { profile -> profile.copy(selectors = key.assign(profile.selectors, "")) }
        rerunPreview()
        _state.update { it.copy(status = "CLEARED  ${key.label}") }
    }

    fun editSelector(key: SelectorKey, selector: String) {
        edit { profile -> profile.copy(selectors = key.assign(profile.selectors, selector.take(2000))) }
        rerunPreview()
    }

    fun rename(name: String) {
        edit { profile -> profile.copy(name = name.take(80)) }
    }

    fun setEntryUrl(raw: String): String? {
        val url = Urls.normalize(raw)
        if (url == null) {
            _state.update { it.copy(status = "URL MUST BE HTTP OR HTTPS") }
            return null
        }
        edit { profile -> profile.copy(entryUrl = url) }
        _state.update { it.copy(status = "LOADING") }
        return url
    }

    fun onSession(pageUrl: String, userAgent: String, cookieHeader: String) {
        val normalized = Urls.normalize(pageUrl)
        val cookies = CookieHeader.parse(cookieHeader, normalized ?: pageUrl)
        edit { profile ->
            profile.copy(
                entryUrl = normalized ?: profile.entryUrl,
                session = SessionSnapshot(
                    userAgent = userAgent,
                    capturedAtUrl = normalized ?: pageUrl,
                    cookies = cookies,
                ),
            )
        }
        val clearance = cookies.any { it.name.equals("cf_clearance", ignoreCase = true) }
        _state.update {
            it.copy(status = if (clearance) "SESSION  cf_clearance" else "SESSION  ${cookies.size} COOKIES")
        }
    }

    fun onHtml(html: String, pageUrl: String) {
        lastHtml = html
        lastPage = pageUrl
        rerunPreview()
    }

    fun hasSelectors(): Boolean = _state.value.profile?.selectors?.filledCount()?.let { it > 0 } == true

    fun anchorSelector(): String {
        val selectors = _state.value.profile?.selectors ?: return ""
        return SelectorKey.entries
            .firstOrNull { it.rule(selectors).selector.isNotBlank() }
            ?.rule(selectors)
            ?.selector
            .orEmpty()
    }

    fun setStatus(message: String) {
        _state.update { it.copy(status = message) }
    }

    private fun rerunPreview() {
        val html = lastHtml
        val profile = _state.value.profile ?: return
        if (html.isBlank()) return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val fields = profile.boundFields()
            val page = lastPage.ifBlank { profile.entryUrl }
            val result = withContext(Dispatchers.Default) {
                JsoupPreviewEngine.preview(html, page, fields)
            }
            if (!isActive) return@launch
            _state.update {
                it.copy(
                    previews = result.associateBy { preview -> preview.key },
                    status = "PARSED  ${result.sumOf { preview -> preview.matchCount }} MATCHES",
                )
            }
        }
    }

    private fun edit(block: (ParserProfile) -> ParserProfile) {
        val current = _state.value.profile ?: return
        val next = block(current).canonical()
        _state.update { it.copy(profile = next) }
        repository.upsert(next)
    }

    companion object {
        fun factory(repository: ParserRepository, id: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BuilderViewModel(repository, id) as T
            }
    }
}
