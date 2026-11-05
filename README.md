# X-HEX PRO

X-HEX PRO is an advanced, high-performance Hex Editor application for Android, built entirely with modern Kotlin and Jetpack Compose. 

It allows you to view and edit the raw, binary data of files directly on your device. Unlike normal text editors, X-HEX PRO displays the fundamental bytes stored on the disk, represented as hexadecimal or binary values.

## Features

- **Paging & Large File Support**: Seamlessly browse large binary files without loading the entire file into memory at once.
- **Hex & Binary Views**: Toggle between base-16 (Hexadecimal) and base-2 (Binary) display modes for precise analysis.
- **Multiple Text Encodings**: Interpret raw bytes into human-readable text using ASCII, UTF-8, UTF-16, ISO-8859-1, and Windows-1252.
- **Advanced Search Tool**: Quickly find sequences of bytes, strings, or specific numerical values within the file.
- **Goto Address**: Jump instantly to any precise data offset by providing a specific Hex or Decimal address.
- **Data Inspector**: Displays the data at the currently selected address interpreted as generic types (Signed/Unsigned Int8, Int16, Int32, Int64, Float, Double).
- **Hex Keyboard Panel**: Built-in, on-screen hex pad controls for precisely overwriting existing byte values one nibble or bit at a time.
- **Tabbed Editing**: Open multiple files simultaneously and easily switch between them.
- **Export Capabilities**: Write all unsaved patches back directly onto a new file without corrupting the original.
- **Responsive UI**: Optimized for both phones and tablets/desktop environments using Material 3 design and dynamic layouts.

## Architecture & Tech Stack

- **UI Toolkit**: Jetpack Compose (Material 3)
- **Language**: Kotlin
- **Architecture**: MVI/MVVM pattern with `ViewModel` and `StateFlow`
- **Coroutines**: For asynchronous file reading, searching, and chunk loading
- **Target Platform**: Android (SDK 24+)

## Getting Started

### Prerequisites
- Android Studio Ladybug or newer.
- Gradle (configured within the wrapper).
- Kotlin compiler toolchain.

### Building the Project

1. Clone the repository to your local machine:
   ```bash
   git clone https://github.com/your-username/x-hex-pro.git
   ```
2. Open the project in Android Studio.
3. Sync the project with Gradle files.
4. Select your target device (Emulator or Physical Device).
5. Build and run the `app` module.

## Screenshots

*(You can add screenshots of your application here)*

## Contact

- **Developer**: bytewave-coder
- **Email**: bytewavext@gmail.com

---

*Thank you for using X-HEX PRO. Built with Jetpack Compose.*
