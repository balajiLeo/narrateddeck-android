# NarratedDeck Studio (Android MVP)

Kotlin + Jetpack Compose Android client for **NarratedDeck Studio**: pick a `.pptx`, edit speaker notes, preview with system Text-to-Speech, and export a zip of per-slide audio + `scripts.txt`.

Package: `com.narrateddeck.android` · Min SDK 26 · Target / compile SDK 34

Desktop sibling: [balajiLeo/narrateddeck-studio](https://github.com/balajiLeo/narrateddeck-studio)

## Open in Android Studio

1. Install [Android Studio](https://developer.android.com/studio) (Hedgehog / Iguana / Koala or newer recommended) with SDK 34.
2. **File → Open** and select this folder (`narrateddeck-android`).
3. Sync Gradle. Wrapper properties + `gradle-wrapper.jar` + `gradlew` scripts are included (Gradle 8.7). If the JAR is stripped by git LFS/filters, regenerate with Android Studio or `gradle wrapper --gradle-version 8.7`.
4. Wait for dependency download (Compose BOM, Material 3, Navigation, etc.).
5. Run on an emulator (API 26+) or a physical device.

### Run

- Select the `app` run configuration → **Run**.
- Unit tests: right-click `app/src/test` → **Run Tests**, or:

```bash
./gradlew :app:testDebugUnitTest
```

(Requires local Android SDK / JDK 17; this repo was scaffolded without compiling on the authoring machine.)

## MVP flow

1. **Import** — Storage Access Framework document picker for `.pptx` (no broad `READ_EXTERNAL_STORAGE`).
2. **Parse** — Pure Kotlin `java.util.zip` + DOM/XML over Open XML parts:
   - `ppt/slides/slideN.xml` → title hint (first `a:t`)
   - `ppt/notesSlides/notesSlideN.xml` → speaker notes
3. **Review** — Editable notes list; **Preview** (live TTS) and **Regenerate** (synthesize to WAV via `TextToSpeech.synthesizeToFile`).
4. **Export** — Zip under app cache: `scripts.txt` + `slide_NNN.wav` (extension follows the TTS output file).

## Project layout

```
narrateddeck-android/
├── app/
│   ├── build.gradle.kts
│   └── src/main/java/com/narrateddeck/android/
│       ├── MainActivity.kt
│       ├── domain/          # SlideNote, PptxParser, TtsNarrator, AudioExporter
│       ├── viewmodel/       # DeckViewModel
│       └── ui/              # theme, screens, navigation
├── app/src/test/            # PptxParser + AudioExporter tests + sample_deck.pptx
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
└── gradle/wrapper/gradle-wrapper.properties
```

## MVP scope vs desktop sibling

| Area | Android MVP | Desktop (`narrateddeck-studio`) |
|------|-------------|----------------------------------|
| PPTX parse | Slides + notes text only | Richer pipeline / tooling |
| Voices | System `TextToSpeech` only | Broader / higher-quality options |
| Audio format | Typically WAV from TTS | May include MP3/OGG pipelines |
| Export UX | Zip path in app cache (share sheet deferred) | Full desktop export UX |
| Offline | Yes (device TTS engine) | Depends on desktop stack |

**Limitations (intentional for MVP):**

- No cloud TTS, no custom neural voices.
- No slide thumbnail rendering / WYSIWYG deck preview.
- Notes matching is by `slideN` / `notesSlideN` numbers (standard packaging); exotic rearrangements may need richer relationship parsing.
- Export zip lives in cache; use Device File Explorer or add `ACTION_SEND` / SAF create-document in a follow-up.
- A `gradle-wrapper.jar` is included for convenience; if sync still fails, regenerate with Android Studio or `gradle wrapper --gradle-version 8.7`.

## License

MIT © 2026 Balaji Sampath — see [LICENSE](LICENSE).
