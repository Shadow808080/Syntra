# Walkthrough - Fixing Conflicting and Unresolved Imports

I have fixed the build error related to conflicting imports and a subsequent unresolved reference in `ShortsScreen.kt`.

## Changes Made

### [ShortsScreen.kt](file:///C:/Users/user/Documents/PROJECTS/syntra/Syntra/app/src/main/java/com/example/syntra/ShortsScreen.kt)

- Removed redundant imports for `WindowInsets`, `statusBars`, `windowInsetsPadding`, and `CircleShape` that were causing the "Conflicting import" error.
- Added `import androidx.compose.material.icons.automirrored.filled.ArrowBack`.
- Updated the usage of `ArrowBack` to use `Icons.AutoMirrored.Filled.ArrowBack` instead of the long FQN that was causing an "Unresolved reference" error.

```diff
-import androidx.compose.foundation.layout.WindowInsets
-import androidx.compose.foundation.layout.statusBars
-import androidx.compose.foundation.layout.windowInsetsPadding
-import androidx.compose.foundation.shape.CircleShape
-import androidx.compose.foundation.layout.WindowInsets
-import androidx.compose.foundation.layout.statusBars
-import androidx.compose.foundation.layout.windowInsetsPadding
-import androidx.compose.foundation.shape.CircleShape
+import androidx.compose.foundation.shape.CircleShape
 import androidx.compose.foundation.shape.RoundedCornerShape
 import androidx.compose.foundation.text.BasicTextField
 import androidx.compose.material.icons.Icons
+import androidx.compose.material.icons.automirrored.filled.ArrowBack
 import androidx.compose.material.icons.automirrored.filled.Send
...
         ) {
             Icon(
-                androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
+                Icons.AutoMirrored.Filled.ArrowBack,
                 "Kembali",
```

## Verification Results

- Ran `./gradlew :app:compileDebugKotlin` and the build finished successfully.
