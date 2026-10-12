package com.parserforge.app.ui.builder

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.parserforge.app.ui.SpineAction
import com.parserforge.app.ui.SpineField
import com.parserforge.app.ui.SpineHeader
import com.parserforge.app.ui.SpineScreen
import com.parserforge.app.ui.SpineText
import com.parserforge.app.ui.theme.Spine
import com.parserforge.app.web.SelectorWebView
import com.parserforge.core.model.ElementCapture
import com.parserforge.core.model.ExtractMode
import com.parserforge.core.model.ParserProfile
import com.parserforge.core.model.SelectorKey
import com.parserforge.core.parse.FieldPreview
import com.parserforge.core.store.ParserRepository

@Composable
fun BuilderRoute(repository: ParserRepository, parserId: String, nav: NavHostController) {
    val vm: BuilderViewModel = viewModel(
        key = "builder-$parserId",
        factory = BuilderViewModel.factory(repository, parserId),
    )
    val state by vm.state.collectAsState()
    val profile = state.profile
    if (state.missing || profile == null) {
        MissingParser(onBack = { nav.popBackStack() })
        return
    }
    BuilderBody(
        vm = vm,
        state = state,
        profile = profile,
        onBack = { nav.popBackStack() },
    )
}

@Composable
private fun MissingParser(onBack: () -> Unit) {
    SpineScreen {
        SpineHeader(title = "BUILDER", onBack = onBack)
        SpineText(
            text = "MISSING PARSER",
            color = Spine.Muted,
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Composable
private fun BuilderBody(
    vm: BuilderViewModel,
    state: BuilderViewModel.UiState,
    profile: ParserProfile,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val web = remember { SelectorWebView(context) }
    var progress by remember { mutableIntStateOf(0) }
    var draftUrl by remember(profile.id) { mutableStateOf(profile.entryUrl) }
    var urlFocused by remember { mutableStateOf(false) }

    DisposableEffect(web) {
        onDispose {
            web.listener = null
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
    }
    SideEffect {
        web.listener = object : SelectorWebView.Listener {
            override fun onProgress(value: Int) {
                if (progress != value) progress = value
            }

            override fun onStatus(message: String) {
                vm.setStatus(message)
            }

            override fun onScriptReady() {
                if (vm.hasSelectors()) web.requestSnapshot(vm.anchorSelector())
            }

            override fun onSession(url: String, userAgent: String, cookieHeader: String) {
                vm.onSession(url, userAgent, cookieHeader)
            }

            override fun onElementPicked(capture: ElementCapture) {
                vm.onCapture(capture)
            }

            override fun onHtmlSnapshot(selector: String, html: String, pageUrl: String) {
                vm.onHtml(html, pageUrl)
            }
        }
    }
    LaunchedEffect(profile.id) {
        web.loadAddress(profile.entryUrl)
    }
    LaunchedEffect(state.pickMode) {
        web.setPickMode(state.pickMode)
    }
    LaunchedEffect(state.snapshotTicket) {
        if (state.snapshotTicket == 0) return@LaunchedEffect
        web.requestSnapshot(state.capture?.selector.orEmpty())
    }
    LaunchedEffect(profile.entryUrl, urlFocused) {
        if (!urlFocused) draftUrl = profile.entryUrl
    }
    BackHandler {
        when {
            state.pickMode -> vm.setPickMode(false)
            web.canGoBack() -> web.goBack()
            else -> onBack()
        }
    }

    fun go() {
        val normalized = vm.setEntryUrl(draftUrl) ?: return
        web.loadAddress(normalized)
    }

    SpineScreen {
        SpineHeader(
            title = profile.name.ifBlank { "BUILDER" },
            onBack = onBack,
        ) {
            SpineAction(
                label = if (state.pickMode) "PICK ON" else "PICK",
                active = state.pickMode,
                onClick = { vm.setPickMode(!state.pickMode) },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SpineText(text = state.status, color = Spine.Muted, size = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            SpineText(text = "URL", color = Spine.Muted, size = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SpineField(
                    value = draftUrl,
                    onValueChange = { draftUrl = it },
                    hint = "https://",
                    modifier = Modifier.weight(1f),
                    onFocus = { urlFocused = it },
                    onDone = ::go,
                )
                SpineAction(label = "GO", active = true, onClick = ::go)
                SpineAction(label = "RELOAD", onClick = { web.reload() })
            }
            SpineText(text = "NAME", color = Spine.Muted, size = 11.sp)
            SpineField(
                value = profile.name,
                onValueChange = vm::rename,
                hint = "source name",
            )
            SessionLine(profile = profile, onCapture = { web.captureSession() })
        }
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            val sheetHeight = maxHeight * 0.48f
            if (maxWidth >= 720.dp) {
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WebPane(
                        web = web,
                        progress = progress,
                        pickMode = state.pickMode,
                        modifier = Modifier.weight(1.3f).fillMaxHeight(),
                    )
                    SelectorSheet(
                        profile = profile,
                        capture = state.capture,
                        previews = state.previews,
                        onAssign = vm::assign,
                        onClear = vm::clear,
                        onEdit = vm::editSelector,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    WebPane(
                        web = web,
                        progress = progress,
                        pickMode = state.pickMode,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    SelectorSheet(
                        profile = profile,
                        capture = state.capture,
                        previews = state.previews,
                        onAssign = vm::assign,
                        onClear = vm::clear,
                        onEdit = vm::editSelector,
                        modifier = Modifier
                            .heightIn(min = 180.dp, max = sheetHeight)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionLine(profile: ParserProfile, onCapture: () -> Unit) {
    val session = profile.session
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpineText(
            text = "UA  ${if (session.userAgent.isBlank()) "DEFAULT" else "CAPTURED"}",
            color = Spine.Muted,
            size = 11.sp,
        )
        SpineText(
            text = "COOKIES  ${session.cookies.size}",
            color = Spine.Muted,
            size = 11.sp,
        )
        SpineText(
            text = "CF_CLEARANCE  ${if (session.hasClearance()) "YES" else "NO"}",
            color = if (session.hasClearance()) Spine.Accent else Spine.Muted,
            size = 11.sp,
        )
    }
    SpineAction(label = "CAPTURE SESSION", onClick = onCapture)
}

@Composable
private fun WebPane(
    web: SelectorWebView,
    progress: Int,
    pickMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.border(1.dp, if (pickMode) Spine.Accent else Spine.White),
    ) {
        if (progress in 1..99) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Spine.Grey),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress / 100f)
                        .background(Spine.Accent),
                )
            }
        }
        AndroidView(
            factory = { _ -> web },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun SelectorSheet(
    profile: ParserProfile,
    capture: ElementCapture?,
    previews: Map<String, FieldPreview>,
    onAssign: (SelectorKey) -> Unit,
    onClear: (SelectorKey) -> Unit,
    onEdit: (SelectorKey, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .border(1.dp, Spine.White)
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SpineText(text = "SELECTORS", size = 12.sp)
        SpineText(
            text = "LAST  ${capture?.selector ?: "—"}",
            color = if (capture == null) Spine.Muted else Spine.Accent,
            size = 12.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (capture != null && capture.text.isNotBlank()) {
            SpineText(text = "TEXT  ${capture.text}", color = Spine.Muted, size = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (capture != null && capture.href.isNotBlank()) {
            SpineText(text = "HREF  ${capture.href}", color = Spine.Muted, size = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (capture != null && capture.src.isNotBlank()) {
            SpineText(text = "SRC  ${capture.src}", color = Spine.Muted, size = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SelectorKey.entries.forEach { key ->
            SelectorRow(
                key = key,
                selector = key.rule(profile.selectors).selector,
                preview = previews[key.id],
                onAssign = { onAssign(key) },
                onClear = { onClear(key) },
                onEdit = { onEdit(key, it) },
            )
        }
    }
}

@Composable
private fun SelectorRow(
    key: SelectorKey,
    selector: String,
    preview: FieldPreview?,
    onAssign: () -> Unit,
    onClear: () -> Unit,
    onEdit: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Spine.Grey)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpineText(text = key.label.uppercase(), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            SpineText(text = key.extract.wireName(), color = Spine.Muted, size = 11.sp)
        }
        SpineField(
            value = selector,
            onValueChange = onEdit,
            hint = "css selector",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SpineAction(label = "ASSIGN", active = true, onClick = onAssign)
            SpineAction(label = "CLEAR", onClick = onClear)
            if (preview != null && preview.error == null && selector.isNotBlank()) {
                SpineText(
                    text = "${preview.matchCount} MATCHES",
                    color = Spine.Muted,
                    size = 11.sp,
                )
            }
        }
        when {
            preview?.error != null -> SpineText(text = "ERR  ${preview.error}", color = Spine.Muted, size = 11.sp, maxLines = 2)
            selector.isNotBlank() && preview != null && preview.values.isEmpty() ->
                SpineText(text = "NO VALUES", color = Spine.Muted, size = 11.sp)
            preview != null -> preview.values.take(3).forEach { value ->
                SpineText(text = value, color = Spine.Muted, size = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun ExtractMode.wireName(): String = when (this) {
    ExtractMode.TEXT -> "text"
    ExtractMode.HREF -> "href"
    ExtractMode.SRC -> "src"
}
