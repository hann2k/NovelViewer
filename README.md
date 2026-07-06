# Library Manager (소설/웹툰 도서관)

An Android application built with Jetpack Compose for reading and managing novel (TXT, ZIP) and webtoon (Images, ZIP) files.
Jetpack Compose로 구축된 소설(TXT, ZIP) 및 웹툰(이미지, ZIP) 읽기 및 관리용 안드로이드 애플리케이션입니다.

## Key Features (주요 기능)

### 📖 Smart Viewer (똑똑한 뷰어)
*   **Novel & Webtoon Support**: High-performance viewer for both text novels and image-based webtoons.
    *   **소설 및 웹툰 지원**: 텍스트 기반 소설과 이미지 기반 웹툰을 위한 고성능 뷰어를 제공합니다.
*   **Reading Position Recovery**: Automatically saves and restores the last book, file, and scroll position (text or image index) when restarting the app.
    *   **읽던 위치 복구**: 앱 재실행 시 마지막으로 읽던 도서, 파일, 스크롤 위치(텍스트 위치 또는 이미지 인덱스)를 자동으로 저장하고 복구합니다.
*   **Pinch-to-Scroll Consistency**: Maintains the exact reading progress (%) even when changing font size or line spacing in the novel viewer.
    *   **진행률 유지**: 소설 뷰어에서 글자 크기나 줄 간격을 변경해도 읽던 맥락(진행률 %)을 정확하게 유지합니다.
*   **Progress Information**: Displays total character count (novel) or image count (webtoon) and current reading progress percentage.
    *   **진행 정보 표시**: 전체 글자 수(소설) 또는 이미지 장수(웹툰)와 현재 읽기 진행률(%)을 실시간으로 표시합니다.

### 🎮 Intuitive Navigation (직관적인 탐색)
*   **Swipe Gestures (Novel)**: Swipe left or right to move between previous and next files.
    *   **스와이프 제스처 (소설)**: 좌우 스와이프를 통해 이전/다음 파일로 간편하게 이동합니다.
*   **Double-Tap Navigation (Novel)**: Double-tap the bottom-left area for the previous file, and the bottom-right area for the next file.
    *   **더블 탭 이동 (소설)**: 화면 좌측 하단 더블 탭 시 이전 파일, 우측 하단 더블 탭 시 다음 파일로 이동합니다.
*   **Back Handler**: Hierarchical navigation (Viewer -> File List -> Book List -> Exit) using the system back button.
    *   **뒤로가기 제어**: 시스템 뒤로가기 버튼 사용 시 계층적 이동(뷰어 -> 목차 -> 도서 목록 -> 종료)을 지원합니다.

### 📚 Library Management (도서 관리)
*   **Unified List**: Shows all novels and webtoons in a single list with clear type indicators like `(소설)` or `(웹툰)`.
    *   **통합 목록**: 소설과 웹툰을 하나의 리스트에서 볼 수 있으며, `(소설)` 또는 `(웹툰)` 접미사로 종류를 쉽게 구분할 수 있습니다.
*   **ZIP Support**: Supports reading text and images directly from ZIP archives without extraction.
    *   **ZIP 지원**: 압축 해제 없이 ZIP 파일 내의 텍스트와 이미지를 직접 읽을 수 있습니다.
*   **Sorting**: Sort all items by "Latest Modified" or "Name" collectively.
    *   **통합 정렬**: 모든 도서를 '최신순' 또는 '가나다순'으로 한꺼번에 정렬할 수 있습니다.
*   **Per-Chapter Progress**: Shows the reading progress (%) for each chapter in the file list.
    *   **챕터별 진행률**: 파일 목록에서 각 챕터별 읽기 진행률(%)을 확인할 수 있습니다.

### 🎨 Customizable Experience (개인 맞춤형 설정)
*   **Themes**: Multiple background and text color presets (Light, Dark, Hanji).
    *   **테마**: 다양한 배경 및 글자 색상 프리셋(밝게, 어둡게, 한지)을 제공합니다.
*   **Typography**: Adjustable font size and line spacing for novels.
    *   **타이포그래피**: 소설 읽기 시 글자 크기와 줄 간격을 자유롭게 조절할 수 있습니다.
*   **Memory Efficiency**: Automatically recycles images in the webtoon viewer to maintain low memory usage.
    *   **메모리 효율**: 웹툰 뷰어에서 이미지를 자동으로 관리(Recycle)하여 낮은 메모리 사용량을 유지합니다.

## Tech Stack (기술 스택)
*   Language: Kotlin
*   UI Framework: Jetpack Compose
*   Storage: SharedPreferences (Settings & Progress)
*   Architecture: State-driven UI with Material 3
*   Image Loading: BitmapFactory with IO Coroutines
