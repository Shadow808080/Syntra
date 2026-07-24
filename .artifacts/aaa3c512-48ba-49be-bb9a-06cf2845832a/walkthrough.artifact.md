# Walkthrough - Fixing Build Errors

I have fixed the build errors related to unresolved references in `TextStoryScreen.kt` and `AuthScreen.kt`.

## Changes Made

### [TextStoryScreen.kt](file:///C:/Users/user/Documents/PROJECTS/syntra/Syntra/app/src/main/java/com/example/syntra/TextStoryScreen.kt)
- Added missing import for `androidx.compose.material.icons.automirrored.filled.ArrowBack`.
- Updated usages of `ArrowBack` to use the correct `Icons.AutoMirrored.Filled.ArrowBack` reference instead of a fully qualified name that was failing to resolve correctly.

### [AuthScreen.kt](file:///C:/Users/user/Documents/PROJECTS/syntra/Syntra/app/src/main/java/com/example/syntra/AuthScreen.kt)
- Added missing import for `androidx.compose.animation.core.animateFloat` to resolve the error in the splash screen pulse animation.

## Verification Results

### Automated Tests
- Ran `./gradlew :app:compileDebugKotlin` which now completes successfully.

```
Build finished successfully.
```
