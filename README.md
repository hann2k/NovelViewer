# Library Manager (소설 도서관)

An Android application built with Jetpack Compose for reading and managing novel files (TXT and ZIP).
Jetpack Compose로 구축된 소설(TXT, ZIP) 읽기 및 관리용 안드로이드 애플리케이션입니다.

## Key Features (주요 기능)

### 📖 Smart Viewer (똑똑한 뷰어)
*   **Reading Position Recovery**: Automatically saves and restores the last book, file, and scroll position when restarting the app.
    *   **읽던 위치 복구**: 앱 재실행 시 마지막으로 읽던 도서, 파일, 스크롤 위치를 자동으로 저장하고 복구합니다.
*   **Pinch-to-Scroll Consistency**: Maintains the exact reading progress (%) even when changing font size or line spacing.
    *   **진행률 유지**: 글자 크기나 줄 간격을 변경해도 읽던 맥락(진행률 %)을 정확하게 유지합니다.
*   **Progress Information**: Displays total character count and current reading progress percentage in the top bar.
    *   **진행 정보 표시**: 상단 바에 전체 글자 수와 현재 읽기 진행률(%)을 실시간으로 표시합니다.

### 🎮 Intuitive Navigation (직관적인 탐색)
*   **Swipe Gestures**: Swipe left or right to move between previous and next files.
    *   **스와이프 제스처**: 좌우 스와이프를 통해 이전/다음 파일로 간편하게 이동합니다.
*   **Double-Tap Navigation**: Double-tap the bottom-left area for the previous file, and the bottom-right area for the next file.
    *   **더블 탭 이동**: 화면 좌측 하단 더블 탭 시 이전 파일, 우측 하단 더블 탭 시 다음 파일로 이동합니다.
*   **Back Handler**: Hierarchical navigation (Viewer -> File List -> Book List -> Exit) using the system back button.
    *   **뒤로가기 제어**: 시스템 뒤로가기 버튼 사용 시 계층적 이동(뷰어 -> 목차 -> 도서 목록 -> 종료)을 지원합니다.

### 📚 Library Management (도서 관리)
*   **ZIP Support**: Supports reading text files directly from ZIP archives without extraction.
    *   **ZIP 지원**: 압축 해제 없이 ZIP 파일 내의 텍스트 파일을 직접 읽을 수 있습니다.
*   **Sorting**: Sort books by "Latest Modified" or "Name".
    *   **도서 정렬**: 도서 목록을 '최신순' 또는 '가나다순'으로 정렬할 수 있습니다.
*   **Per-Chapter Progress**: Shows the reading progress (%) for each chapter in the file list.
    *   **챕터별 진행률**: 파일 목록에서 각 챕터별 읽기 진행률(%)을 확인할 수 있습니다.

### 🎨 Customizable Experience (개인 맞춤형 설정)
*   **Themes**: Multiple background and text color presets (Light, Dark, Hanji).
    *   **테마**: 다양한 배경 및 글자 색상 프리셋(밝게, 어둡게, 한지)을 제공합니다.
*   **Typography**: Adjustable font size and line spacing.
    *   **타이포그래피**: 글자 크기와 줄 간격을 자유롭게 조절할 수 있습니다.

## Tech Stack (기술 스택)
*   Language: Kotlin
*   UI Framework: Jetpack Compose
*   Storage: SharedPreferences (Settings & Progress)
*   Architecture: State-driven UI with Material 3
