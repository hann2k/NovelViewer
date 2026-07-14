package com.example.mytestapp

import android.app.Activity
import android.content.Context
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import android.graphics.BitmapFactory
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

// 화면 정의
sealed class Screen {
    object BookList : Screen()
    data class FileList(val book: File, val type: BookType) : Screen()
    data class Viewer(
        val book: File,
        val currentPath: String,
        val allPaths: List<String>,
        val initialScrollPosition: Int = 0
    ) : Screen()
    data class WebtoonViewer(
        val book: File,
        val chapterPath: String,
        val allChapters: List<String>,
        val initialScrollPosition: Int = 0
    ) : Screen()
}

data class BookItem(
    val file: File,
    val type: BookType,
    val displayName: String
)

enum class BookType {
    NOVEL, WEBTOON
}

fun getDisplayName(file: File): String {
    var name = file.nameWithoutExtension
    if (file.isDirectory) {
        val titleFile = File(file, "title.txt")
        if (titleFile.exists()) {
            try { name = titleFile.readLines().firstOrNull() ?: name } catch (e: Exception) {}
        }
    } else if (file.extension.lowercase() == "zip") {
        try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("title.txt")
                if (entry != null) {
                    name = zip.getInputStream(entry).bufferedReader().readLine() ?: name
                }
            }
        } catch (e: Exception) {}
    }
    return name
}

fun getFilePaths(book: File, type: BookType): List<String> {
    return if (type == BookType.NOVEL) {
        if (book.extension.lowercase() == "zip") {
            try {
                ZipFile(book).use { zip ->
                    zip.entries().asSequence()
                        .filter { !it.isDirectory && it.name.lowercase().endsWith(".txt") }
                        .map { it.name }
                        .sorted()
                        .toList()
                }
            } catch (e: Exception) { emptyList() }
        } else {
            book.listFiles { file -> file.extension.lowercase() == "txt" }
                ?.sortedBy { it.name }
                ?.map { it.absolutePath } ?: emptyList()
        }
    } else { // WEBTOON
        if (book.extension.lowercase() == "zip") {
            try {
                ZipFile(book).use { zip ->
                    zip.entries().asSequence()
                        .filter { it.isDirectory }
                        .map { it.name.removeSuffix("/") }
                        .filter { it.isNotEmpty() && !it.contains("/") } // 최상위 폴더만 화로 인식
                        .distinct()
                        .sorted()
                        .toList()
                }
            } catch (e: Exception) { emptyList() }
        } else {
            book.listFiles { file -> file.isDirectory }
                ?.sortedBy { it.name }
                ?.map { it.absolutePath } ?: emptyList()
        }
    }
}

data class LastSession(
    val bookPath: String?,
    val filePath: String?,
    val scrollPosition: Int = 0
)

fun saveLastSession(context: Context, session: LastSession) {
    context.getSharedPreferences("novel_prefs", Context.MODE_PRIVATE).edit {
        putString("lastBookPath", session.bookPath)
        putString("lastFilePath", session.filePath)
        putInt("lastScrollPosition", session.scrollPosition)
    }
    // 책별 마지막 세션 저장
    if (session.bookPath != null && session.filePath != null) {
        context.getSharedPreferences("book_last_session_prefs", Context.MODE_PRIVATE).edit {
            putString("${session.bookPath}:filePath", session.filePath)
            putInt("${session.bookPath}:scrollPosition", session.scrollPosition)
        }
    }
}

fun getBookLastSession(context: Context, bookPath: String): Pair<String?, Int> {
    val prefs = context.getSharedPreferences("book_last_session_prefs", Context.MODE_PRIVATE)
    val lastFilePath = prefs.getString("$bookPath:filePath", null)
    val lastPos = prefs.getInt("$bookPath:scrollPosition", 0)
    
    if (lastFilePath != null) {
        return Pair(lastFilePath, lastPos)
    }

    // 구버전 호환용 (기존 전역 세션에서 현재 책의 기록인지 확인)
    val globalPrefs = context.getSharedPreferences("novel_prefs", Context.MODE_PRIVATE)
    val globalBookPath = globalPrefs.getString("lastBookPath", null)
    if (globalBookPath == bookPath) {
        return Pair(
            globalPrefs.getString("lastFilePath", null),
            globalPrefs.getInt("lastScrollPosition", 0)
        )
    }
    
    return Pair(null, 0)
}

fun loadLastSession(context: Context): LastSession {
    val prefs = context.getSharedPreferences("novel_prefs", Context.MODE_PRIVATE)
    return LastSession(
        bookPath = prefs.getString("lastBookPath", null),
        filePath = prefs.getString("lastFilePath", null),
        scrollPosition = prefs.getInt("lastScrollPosition", 0)
    )
}

enum class SortOrder {
    LATEST, NAME
}

fun saveSortOrder(context: Context, order: SortOrder) {
    context.getSharedPreferences("novel_prefs", Context.MODE_PRIVATE).edit {
        putString("sortOrder", order.name)
    }
}

fun loadSortOrder(context: Context): SortOrder {
    val name = context.getSharedPreferences("novel_prefs", Context.MODE_PRIVATE).getString("sortOrder", SortOrder.NAME.name)
    return SortOrder.valueOf(name ?: SortOrder.NAME.name)
}

fun saveFileProgress(context: Context, bookPath: String, filePath: String, progress: Float) {
    val key = "$bookPath:$filePath"
    context.getSharedPreferences("file_progress_prefs", Context.MODE_PRIVATE).edit {
        putFloat(key, progress)
    }
}

fun getFileProgress(context: Context, bookPath: String, filePath: String): Float {
    val key = "$bookPath:$filePath"
    return context.getSharedPreferences("file_progress_prefs", Context.MODE_PRIVATE).getFloat(key, 0f)
}

fun saveFilePosition(context: Context, bookPath: String, filePath: String, position: Int) {
    val key = "$bookPath:$filePath"
    context.getSharedPreferences("file_position_prefs", Context.MODE_PRIVATE).edit {
        putInt(key, position)
    }
}

fun getFilePosition(context: Context, bookPath: String, filePath: String): Int {
    val key = "$bookPath:$filePath"
    return context.getSharedPreferences("file_position_prefs", Context.MODE_PRIVATE).getInt(key, 0)
}

// 뷰어 설정 데이터
data class ViewerSettings(
    val fontSize: Float = 18f,
    val lineSpacing: Float = 1.6f,
    val backgroundColor: Color = Color(0xFFF4ECD8),
    val textColor: Color = Color(0xFF3C2F2F),
    val fontFamily: FontFamily = FontFamily.Default,
    val brightness: Float = -1f, // -1은 시스템 기본값
    val lastManualBrightness: Float = 0.5f, // 마지막으로 설정했던 수동 밝기 값 저장
    val customTextColor: Color = Color(0xFF3C2F2F) // 커스텀 글자 색상 저장
)

fun saveSettings(context: Context, settings: ViewerSettings) {
    context.getSharedPreferences("novel_prefs", Context.MODE_PRIVATE).edit {
        putFloat("fontSize", settings.fontSize)
        putFloat("lineSpacing", settings.lineSpacing)
        putInt("bgColor", settings.backgroundColor.toArgb())
        putInt("textColor", settings.textColor.toArgb())
        putFloat("brightness", settings.brightness)
        putFloat("lastManualBrightness", settings.lastManualBrightness)
        putInt("customTextColor", settings.customTextColor.toArgb())
    }
}

fun loadSettings(context: Context): ViewerSettings {
    val prefs = context.getSharedPreferences("novel_prefs", Context.MODE_PRIVATE)
    return ViewerSettings(
        fontSize = prefs.getFloat("fontSize", 18f),
        lineSpacing = prefs.getFloat("lineSpacing", 1.6f),
        backgroundColor = Color(prefs.getInt("bgColor", Color(0xFFF4ECD8).toArgb())),
        textColor = Color(prefs.getInt("textColor", Color(0xFF3C2F2F).toArgb())),
        brightness = prefs.getFloat("brightness", -1f),
        lastManualBrightness = prefs.getFloat("lastManualBrightness", 0.5f),
        customTextColor = Color(prefs.getInt("customTextColor", Color(0xFF3C2F2F).toArgb()))
    )
}

@Composable
fun NovelViewerApp() {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(loadSettings(context)) }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.BookList) }
    var isInitialized by remember { mutableStateOf(false) }

    // 권한 허용 후 돌아왔을 때를 위해 세션 복구 로직을 별도 함수로 분리
    val restoreSession = {
        val session = loadLastSession(context)
        if (session.bookPath != null && session.filePath != null) {
            val book = File(session.bookPath)
            if (book.exists()) {
                val isWebtoon = book.absolutePath.contains("Webtoon")
                val type = if (isWebtoon) BookType.WEBTOON else BookType.NOVEL
                val allPaths = getFilePaths(book, type)
                if (allPaths.contains(session.filePath)) {
                    if (type == BookType.NOVEL) {
                        currentScreen = Screen.Viewer(book, session.filePath, allPaths, session.scrollPosition)
                    } else {
                        currentScreen = Screen.WebtoonViewer(book, session.filePath, allPaths, session.scrollPosition)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        restoreSession()
        isInitialized = true
    }

    if (!isInitialized) return

    // 전역 뒤로가기 처리
    BackHandler(enabled = currentScreen !is Screen.BookList) {
        val screen = currentScreen
        when (screen) {
            is Screen.FileList -> currentScreen = Screen.BookList
            is Screen.Viewer -> currentScreen = Screen.FileList(screen.book, BookType.NOVEL)
            is Screen.WebtoonViewer -> currentScreen = Screen.FileList(screen.book, BookType.WEBTOON)
            else -> {}
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = settings.backgroundColor
    ) {
        // 밝기 적용
        val activity = context as? Activity
        SideEffect {
            activity?.window?.let { window ->
                val params = window.attributes
                params.screenBrightness = if (settings.brightness < 0) -1f else settings.brightness.coerceIn(0.01f, 1.0f)
                window.attributes = params
            }
        }
        
        when (val screen = currentScreen) {
            is Screen.BookList -> BookListScreen(
                settings = settings,
                onBookClick = { bookItem ->
                    currentScreen = Screen.FileList(bookItem.file, bookItem.type)
                },
                onRecentClick = { book, path, allPaths, position, type ->
                    if (type == BookType.NOVEL) {
                        currentScreen = Screen.Viewer(book, path, allPaths, position)
                    } else {
                        currentScreen = Screen.WebtoonViewer(book, path, allPaths, position)
                    }
                }
            )
            is Screen.FileList -> {
                FileListScreen(
                    book = screen.book,
                    type = screen.type,
                    settings = settings,
                    onFileClick = { path, allPaths, position -> 
                        if (screen.type == BookType.NOVEL) {
                            currentScreen = Screen.Viewer(screen.book, path, allPaths, position)
                        } else {
                            currentScreen = Screen.WebtoonViewer(screen.book, path, allPaths, position)
                        }
                    }
                )
            }
            is Screen.Viewer -> {
                ViewerScreen(
                    book = screen.book,
                    currentPath = screen.currentPath,
                    allPaths = screen.allPaths,
                    initialScrollPosition = screen.initialScrollPosition,
                    settings = settings,
                    onSettingsChange = { newSettings ->
                        settings = newSettings
                        saveSettings(context, newSettings)
                    },
                    onFileChange = { nextPath ->
                        val savedPos = getFilePosition(context, screen.book.absolutePath, nextPath)
                        currentScreen = Screen.Viewer(screen.book, nextPath, screen.allPaths, savedPos)
                    }
                )
            }
            is Screen.WebtoonViewer -> {
                WebtoonViewerScreen(
                    book = screen.book,
                    chapterPath = screen.chapterPath,
                    allChapters = screen.allChapters,
                    initialScrollPosition = screen.initialScrollPosition,
                    settings = settings,
                    onSettingsChange = { newSettings ->
                        settings = newSettings
                        saveSettings(context, newSettings)
                    },
                    onChapterChange = { nextChapter ->
                        val savedPos = getFilePosition(context, screen.book.absolutePath, nextChapter)
                        currentScreen = Screen.WebtoonViewer(screen.book, nextChapter, screen.allChapters, savedPos)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(
    settings: ViewerSettings,
    onBookClick: (BookItem) -> Unit,
    onRecentClick: (File, String, List<String>, Int, BookType) -> Unit
) {
    val context = LocalContext.current
    val novelRoot = File(Environment.getExternalStorageDirectory(), "Books/소설")
    val webtoonRoot = File(Environment.getExternalStorageDirectory(), "Books/Webtoon")
    
    var sortOrder by remember { mutableStateOf(loadSortOrder(context)) }
    var isRefreshing by remember { mutableStateOf(false) }
    
    val allBooks = remember(sortOrder, isRefreshing) {
        val novelList = novelRoot.listFiles { file -> 
            file.isDirectory || file.extension.lowercase() == "zip" 
        }?.map { 
            BookItem(it, BookType.NOVEL, getDisplayName(it))
        } ?: emptyList()

        val webtoonList = webtoonRoot.listFiles { file -> 
            file.isDirectory || file.extension.lowercase() == "zip" 
        }?.map { 
            BookItem(it, BookType.WEBTOON, getDisplayName(it))
        } ?: emptyList()

        val combined = novelList + webtoonList
        
        when (sortOrder) {
            SortOrder.LATEST -> combined.sortedByDescending { it.file.lastModified() }
            SortOrder.NAME -> combined.sortedBy { it.displayName }
        }
    }

    val lastSession = remember(isRefreshing) {
        val session = loadLastSession(context)
        if (session.bookPath != null && session.filePath != null) {
            val book = File(session.bookPath)
            if (book.exists()) {
                val isWebtoon = book.absolutePath.contains("Webtoon")
                val type = if (isWebtoon) BookType.WEBTOON else BookType.NOVEL
                val allPaths = getFilePaths(book, type)
                if (allPaths.contains(session.filePath)) {
                    android.util.Log.d("BookListScreen", "Found last session: ${session.filePath}")
                    Triple(book, session, type)
                } else null
            } else null
        } else null
    }
    
    var showSortMenu by remember { mutableStateOf(false) }

    val onRefresh: () -> Unit = {
        isRefreshing = true
        isRefreshing = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("도서관", color = settings.textColor) },
                actions = {
                    if (lastSession != null) {
                        TextButton(onClick = {
                            val (book, session, type) = lastSession
                            onRecentClick(book, session.filePath!!, getFilePaths(book, type), session.scrollPosition, type)
                        }) {
                            Text("최근 도서", color = settings.textColor)
                        }
                    }
                    Box {
                        Text(
                            text = if (sortOrder == SortOrder.LATEST) "최신순" else "가나다순",
                            color = settings.textColor,
                            modifier = Modifier.padding(end = 16.dp).clickable { showSortMenu = true }
                        )
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(settings.backgroundColor)
                        ) {
                            DropdownMenuItem(
                                text = { Text("최신순", color = settings.textColor) },
                                onClick = {
                                    sortOrder = SortOrder.LATEST
                                    saveSortOrder(context, SortOrder.LATEST)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("가나다순", color = settings.textColor) },
                                onClick = {
                                    sortOrder = SortOrder.NAME
                                    saveSortOrder(context, SortOrder.NAME)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = settings.backgroundColor)
            )
        },
        containerColor = settings.backgroundColor
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            if (allBooks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("도서가 없습니다.\n아래로 당겨서 새로고침", color = settings.textColor)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(allBooks) { bookItem ->
                        val typeSuffix = if (bookItem.type == BookType.NOVEL) " (소설)" else " (웹툰)"
                        val displayName = bookItem.displayName + typeSuffix
                        
                        ListItem(
                            headlineContent = { Text(displayName, color = settings.textColor) },
                            colors = ListItemDefaults.colors(containerColor = settings.backgroundColor),
                            modifier = Modifier.clickable { onBookClick(bookItem) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    color: Color = Color.Gray
) {
    val coroutineScope = rememberCoroutineScope()
    
    // 드래그 중인지 여부 확인
    var isDragging by remember { mutableStateOf(false) }

    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (state.isScrollInProgress || isDragging) 0.8f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "scrollbarAlpha"
    )

    val thumbWidth by animateFloatAsState(
        targetValue = if (isDragging) 8f else 4f,
        animationSpec = tween(durationMillis = 200),
        label = "thumbWidth"
    )

    if (scrollbarAlpha > 0f || isDragging) {
        val layoutInfo = state.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isNotEmpty()) {
            val totalItemsCount = layoutInfo.totalItemsCount
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            
            // 대략적인 전체 높이 계산 (평균 아이템 높이 * 총 개수)
            val avgItemSize = visibleItems.map { it.size }.average().toFloat()
            val estimatedTotalHeight = avgItemSize * totalItemsCount
            
            // 스크롤바 크기 비율 (최소 5% 확보)
            val thumbHeightPercent = (viewportHeight / estimatedTotalHeight).coerceIn(0.05f, 1.0f)
            
            // 현재 스크롤 위치 비율
            val firstItemIndex = state.firstVisibleItemIndex
            val firstItemOffset = state.firstVisibleItemScrollOffset
            val scrollPercent = ((firstItemIndex * avgItemSize + firstItemOffset) / estimatedTotalHeight).coerceIn(0f, 1f)

            BoxWithConstraints(
                modifier = modifier
                    .fillMaxHeight()
                    .width(40.dp) // 넓은 터치 영역 확보
                    .graphicsLayer(alpha = scrollbarAlpha)
                    .pointerInput(estimatedTotalHeight, viewportHeight) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                // 드래그 양(픽셀)을 전체 리스트의 스크롤 양으로 변환
                                // 드래그한 픽셀 / 트랙 전체 높이 * 리스트 전체 높이
                                val scrollDelta = (dragAmount.y / (size.height * (1f - thumbHeightPercent))) * (estimatedTotalHeight - viewportHeight)
                                coroutineScope.launch {
                                    state.scrollBy(scrollDelta)
                                }
                            }
                        )
                    }
            ) {
                val trackHeight = maxHeight
                val thumbHeight = trackHeight * thumbHeightPercent
                val thumbOffset = (trackHeight - thumbHeight) * scrollPercent

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(thumbWidth.dp)
                        .fillMaxHeight()
                        .background(color.copy(alpha = 0.1f), shape = RoundedCornerShape(thumbWidth.dp / 2))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = thumbOffset)
                        .height(thumbHeight)
                        .width(thumbWidth.dp)
                        .background(color, shape = RoundedCornerShape(thumbWidth.dp / 2))
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(book: File, type: BookType, settings: ViewerSettings, onFileClick: (String, List<String>, Int) -> Unit) {
    val context = LocalContext.current
    val filePaths = remember(book) { getFilePaths(book, type) }
    val bookDisplayName = remember(book) { getDisplayName(book) }
    val listState = rememberLazyListState()

    // 책별 기록 불러오기
    val lastSession = remember(book, filePaths) {
        val (lastFilePath, lastPos) = getBookLastSession(context, book.absolutePath)
        if (lastFilePath != null && filePaths.contains(lastFilePath)) {
            Triple(lastFilePath, filePaths, lastPos)
        } else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(bookDisplayName, color = settings.textColor) },
                actions = {
                    if (lastSession != null) {
                        TextButton(onClick = { onFileClick(lastSession.first, lastSession.second, lastSession.third) }) {
                            Text("이어서 보기", color = settings.textColor)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = settings.backgroundColor)
            )
        },
        containerColor = settings.backgroundColor
    ) { padding ->
        if (filePaths.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(if (type == BookType.NOVEL) "텍스트 파일이 없습니다." else "화별 폴더가 없습니다.", color = settings.textColor)
            }
        } else {
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filePaths) { path ->
                        val displayName = path.substringAfterLast('/').removeSuffix(".txt")
                        val progress = getFileProgress(context, book.absolutePath, path)
                        val position = getFilePosition(context, book.absolutePath, path)
                        ListItem(
                            headlineContent = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = displayName,
                                        color = settings.textColor,
                                        modifier = Modifier.fillMaxWidth(0.7f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (progress > 0) {
                                        Text(
                                            text = String.format(Locale.getDefault(), "%.2f%%", progress),
                                            color = settings.textColor.copy(alpha = 0.5f),
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = settings.backgroundColor),
                            modifier = Modifier.clickable { onFileClick(path, filePaths, position) }
                        )
                    }
                }
                
                CustomScrollbar(
                    state = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    color = settings.textColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    book: File,
    currentPath: String,
    allPaths: List<String>,
    initialScrollPosition: Int = 0,
    settings: ViewerSettings,
    onSettingsChange: (ViewerSettings) -> Unit,
    onFileChange: (String) -> Unit
) {
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    
    // 문단 단위로 로드 (대용량 대응)
    val paragraphs = remember(currentPath) {
        try {
            if (book.extension.lowercase() == "zip") {
                ZipFile(book).use { zip ->
                    val entry = zip.getEntry(currentPath)
                    zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readLines()
                }
            } else {
                File(currentPath).readLines(Charsets.UTF_8)
            }
        } catch (e: Exception) { listOf("파일을 읽을 수 없습니다.\n${e.message}") }
    }

    val scrollState = key(currentPath) {
        rememberLazyListState(initialFirstVisibleItemIndex = initialScrollPosition)
    }

    val displayName = currentPath.substringAfterLast('/').removeSuffix(".txt")
    
    // 전체 글자 수 (진행률 표시 보조용)
    val totalChars = remember(paragraphs) { paragraphs.sumOf { it.length } }
    
    val progress by remember {
        derivedStateOf {
            if (paragraphs.isNotEmpty()) {
                (scrollState.firstVisibleItemIndex.toFloat() / paragraphs.size * 100)
            } else 0f
        }
    }

    LaunchedEffect(scrollState.firstVisibleItemIndex) {
        // 즉시 세션 저장
        saveLastSession(context, LastSession(book.absolutePath, currentPath, scrollState.firstVisibleItemIndex))
        saveFilePosition(context, book.absolutePath, currentPath, scrollState.firstVisibleItemIndex)
        
        if (paragraphs.isNotEmpty()) {
            saveFileProgress(context, book.absolutePath, currentPath, progress)
        }
        delay(500)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(displayName, color = settings.textColor, fontSize = 16.sp)
                        Text(
                            text = String.format(Locale.getDefault(), "전체: %d자 | 진행률: %.2f%%", totalChars, progress),
                            color = settings.textColor.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                },
                actions = {
                    Text("설정", color = settings.textColor, modifier = Modifier.padding(end = 16.dp).clickable { showSettings = true })
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = settings.backgroundColor,
                    titleContentColor = settings.textColor,
                    navigationIconContentColor = settings.textColor,
                    actionIconContentColor = settings.textColor
                )
            )
        },
        containerColor = settings.backgroundColor
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(settings.backgroundColor)
                .pointerInput(currentPath) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val width = size.width
                            val height = size.height
                            val isBottom = offset.y > height / 2
                            val isLeft = offset.x < width / 2
                            val isRight = offset.x >= width / 2

                            if (isBottom && isLeft) {
                                val index = allPaths.indexOf(currentPath)
                                if (index > 0) onFileChange(allPaths[index - 1])
                            } else if (isBottom && isRight) {
                                val index = allPaths.indexOf(currentPath)
                                if (index < allPaths.size - 1) onFileChange(allPaths[index + 1])
                            }
                        }
                    )
                }
                .pointerInput(currentPath) {
                    var totalDragX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount
                        },
                        onDragEnd = {
                            val threshold = 150f
                            if (totalDragX > threshold) {
                                val index = allPaths.indexOf(currentPath)
                                if (index > 0) onFileChange(allPaths[index - 1])
                            } else if (totalDragX < -threshold) {
                                val index = allPaths.indexOf(currentPath)
                                if (index < allPaths.size - 1) onFileChange(allPaths[index + 1])
                            }
                        }
                    )
                }
        ) {
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(paragraphs) { paragraph ->
                    Text(
                        text = if (paragraph.isEmpty()) "\n" else paragraph,
                        fontSize = settings.fontSize.sp,
                        lineHeight = (settings.fontSize * settings.lineSpacing).sp,
                        color = settings.textColor,
                        fontFamily = settings.fontFamily,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }

            CustomScrollbar(
                state = scrollState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                color = settings.textColor
            )
        }

        if (showSettings) {
            SettingsDialog(
                settings = settings,
                onSettingsChange = onSettingsChange,
                onDismiss = { showSettings = false }
            )
        }
    }
}

@Composable
fun CustomColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var red by remember { mutableIntStateOf((initialColor.red * 255).toInt()) }
    var green by remember { mutableIntStateOf((initialColor.green * 255).toInt()) }
    var blue by remember { mutableIntStateOf((initialColor.blue * 255).toInt()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("커스텀 색상 설정") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 미리보기 박스
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(Color(red, green, blue), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                ColorChannelInput("Red", red) { red = it }
                ColorChannelInput("Green", green) { green = it }
                ColorChannelInput("Blue", blue) { blue = it }
            }
        },
        confirmButton = {
            Button(onClick = { onColorSelected(Color(red, green, blue)) }) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
fun ColorChannelInput(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.width(50.dp))
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = 0f..255f,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = value.toString(),
                onValueChange = {
                    val newValue = it.toIntOrNull()?.coerceIn(0, 255) ?: value
                    onValueChange(newValue)
                },
                modifier = Modifier.width(70.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
    }
}

@Composable
fun SettingsDialog(settings: ViewerSettings, onSettingsChange: (ViewerSettings) -> Unit, onDismiss: () -> Unit) {
    var showColorPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("뷰어 설정") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("화면 밝기: ${if (settings.brightness < 0) "자동" else (settings.brightness * 100).toInt().toString() + "%"}", modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = if (settings.brightness < 0) settings.lastManualBrightness else settings.brightness,
                        onValueChange = { 
                            onSettingsChange(settings.copy(brightness = it, lastManualBrightness = it)) 
                        },
                        modifier = Modifier.weight(1f),
                        valueRange = 0.01f..1.0f
                    )
                    TextButton(
                        onClick = { 
                            val newBrightness = if (settings.brightness < 0) settings.lastManualBrightness else -1f
                            onSettingsChange(settings.copy(brightness = newBrightness)) 
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            "Auto",
                            color = if (settings.brightness < 0) MaterialTheme.colorScheme.primary else settings.textColor.copy(alpha = 0.5f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("글자 크기: ${settings.fontSize.toInt()}")
                Slider(value = settings.fontSize, onValueChange = { onSettingsChange(settings.copy(fontSize = it)) }, valueRange = 12f..40f)
                Text("줄 간격: ${String.format(Locale.getDefault(), "%.1f", settings.lineSpacing)}")
                Slider(value = settings.lineSpacing, onValueChange = { onSettingsChange(settings.copy(lineSpacing = it)) }, valueRange = 1.0f..3.0f)
                Spacer(Modifier.height(16.dp))
                Text("테마 (배경+글자)")
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    ThemeOption(Color.White, Color.Black, "밝게", settings, onSettingsChange)
                    ThemeOption(Color(0xFF1E1E1E), Color.LightGray, "어둡게", settings, onSettingsChange)
                    ThemeOption(Color(0xFFF4ECD8), Color(0xFF3C2F2F), "한지", settings, onSettingsChange)
                }
                Spacer(Modifier.height(16.dp))
                Text("글자 색상만 변경")
                Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // 프리셋 (검정, 흰색, 회색)
                    val presets = listOf(Color.Black, Color.White, Color.Gray)
                    presets.forEach { color ->
                        ColorOption(color, settings, onSettingsChange)
                    }

                    // 커스텀 색상 버튼 (오른쪽 끝 부근)
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(settings.customTextColor).border(
                            width = if (settings.textColor == settings.customTextColor) 2.dp else 1.dp,
                            color = if (settings.textColor == settings.customTextColor) MaterialTheme.colorScheme.primary else Color.LightGray,
                            shape = CircleShape
                        ).clickable { onSettingsChange(settings.copy(textColor = settings.customTextColor)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("C", color = if (settings.customTextColor.luminance() > 0.5f) Color.Black else Color.White, fontSize = 12.sp)
                    }
                    
                    TextButton(onClick = { showColorPicker = true }) {
                        Text("설정", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("확인") } }
    )

    if (showColorPicker) {
        CustomColorPickerDialog(
            initialColor = settings.customTextColor,
            onColorSelected = { newColor ->
                onSettingsChange(settings.copy(customTextColor = newColor, textColor = newColor))
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

@Composable
fun ColorOption(color: Color, settings: ViewerSettings, onSettingsChange: (ViewerSettings) -> Unit) {
    Box(
        modifier = Modifier.size(32.dp).clip(CircleShape).background(color).border(
            width = if (settings.textColor == color) 2.dp else 1.dp,
            color = if (settings.textColor == color) MaterialTheme.colorScheme.primary else Color.LightGray,
            shape = CircleShape
        ).clickable { onSettingsChange(settings.copy(textColor = color)) }
    )
}

fun Color.luminance(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}

@Composable
fun ThemeOption(bg: Color, fg: Color, label: String, current: ViewerSettings, onSettingsChange: (ViewerSettings) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp).clickable { onSettingsChange(current.copy(backgroundColor = bg, textColor = fg)) }) {
        Box(Modifier.size(40.dp).background(bg, shape = MaterialTheme.shapes.small).border(1.dp, Color.LightGray, MaterialTheme.shapes.small).padding(2.dp)) {
            if (current.backgroundColor == bg) Box(Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.3f)))
        }
        Text(label, fontSize = 10.sp)
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebtoonViewerScreen(
    book: File,
    chapterPath: String,
    allChapters: List<String>,
    initialScrollPosition: Int = 0,
    settings: ViewerSettings,
    onSettingsChange: (ViewerSettings) -> Unit,
    onChapterChange: (String) -> Unit
) {
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    val isZip = book.extension.lowercase() == "zip"
    
    val images = remember(chapterPath) {
        if (isZip) {
            try {
                ZipFile(book).use { zip ->
                    zip.entries().asSequence()
                        .filter { 
                            !it.isDirectory && 
                            it.name.startsWith(chapterPath + "/") &&
                            it.name.lowercase().let { n -> n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") } 
                        }
                        .map { it.name }
                        .sorted()
                        .toList()
                }
            } catch (e: Exception) { emptyList() }
        } else {
            File(chapterPath).listFiles { file -> 
                val ext = file.extension.lowercase()
                ext == "jpg" || ext == "jpeg" || ext == "png" || ext == "webp"
            }?.sortedBy { it.name }?.map { it.absolutePath } ?: emptyList()
        }
    }

    val listState = key(chapterPath) {
        androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = initialScrollPosition)
    }
    
    val progress by remember {
        derivedStateOf {
            if (images.isNotEmpty()) {
                (listState.firstVisibleItemIndex.toFloat() / images.size * 100)
            } else 0f
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex) {
        saveLastSession(context, LastSession(book.absolutePath, chapterPath, listState.firstVisibleItemIndex))
        saveFilePosition(context, book.absolutePath, chapterPath, listState.firstVisibleItemIndex)
        if (images.isNotEmpty()) {
            val currentProgress = (listState.firstVisibleItemIndex.toFloat() / images.size * 100)
            saveFileProgress(context, book.absolutePath, chapterPath, currentProgress)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(chapterPath.substringAfterLast('/'), color = settings.textColor, fontSize = 16.sp)
                        Text(
                            text = String.format(Locale.getDefault(), "이미지: %d장 | 진행률: %.2f%%", images.size, progress),
                            color = settings.textColor.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                },
                actions = {
                    Text("설정", color = settings.textColor, modifier = Modifier.padding(end = 16.dp).clickable { showSettings = true })
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = settings.backgroundColor,
                    titleContentColor = settings.textColor,
                    navigationIconContentColor = settings.textColor,
                    actionIconContentColor = settings.textColor
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(chapterPath) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val width = size.width
                            val height = size.height
                            val isBottom = offset.y > height / 2
                            val isLeft = offset.x < width / 2
                            val isRight = offset.x >= width / 2

                            if (isBottom && isLeft) {
                                // 좌측 하단 더블 탭 -> 이전 챕터
                                val index = allChapters.indexOf(chapterPath)
                                if (index > 0) onChapterChange(allChapters[index - 1])
                            } else if (isBottom && isRight) {
                                // 우측 하단 더블 탭 -> 다음 챕터
                                val index = allChapters.indexOf(chapterPath)
                                if (index < allChapters.size - 1) onChapterChange(allChapters[index + 1])
                            }
                        }
                    )
                }
                .pointerInput(chapterPath) {
                    var totalDragX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount
                        },
                        onDragEnd = {
                            val threshold = 150f
                            if (totalDragX > threshold) {
                                // 오른쪽으로 스와이프 -> 이전 챕터
                                val index = allChapters.indexOf(chapterPath)
                                if (index > 0) onChapterChange(allChapters[index - 1])
                            } else if (totalDragX < -threshold) {
                                // 왼쪽으로 스와이프 -> 다음 챕터
                                val index = allChapters.indexOf(chapterPath)
                                if (index < allChapters.size - 1) onChapterChange(allChapters[index + 1])
                            }
                        }
                    )
                }
        ) {
            if (images.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("이미지 파일이 없습니다.", color = Color.White)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(images) { imagePath ->
                        var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                        
                        LaunchedEffect(imagePath) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    if (isZip) {
                                        ZipFile(book).use { zip ->
                                            val entry = zip.getEntry(imagePath)
                                            zip.getInputStream(entry).use { BitmapFactory.decodeStream(it) }
                                        }
                                    } else {
                                        BitmapFactory.decodeFile(imagePath)
                                    }
                                } catch (e: Exception) { null }
                            }?.let { bitmap = it }
                        }

                        if (bitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxWidth().height(400.dp).background(Color.DarkGray))
                        }
                        
                        // DisposableEffect에서 recycle() 호출 제거 (Canvas에서 이미 사용 중인 비트맵이 해제되는 문제 방지)
                        // bitmap = null만 수행하거나 생략하여 가비지 컬렉터에 위임
                        DisposableEffect(imagePath) {
                            onDispose {
                                // bitmap?.recycle() <- 이 부분이 크래시의 원인
                                bitmap = null
                            }
                        }
                    }
                }
            }
        }

        if (showSettings) {
            SettingsDialog(
                settings = settings,
                onSettingsChange = onSettingsChange,
                onDismiss = { showSettings = false }
            )
        }
    }
}
