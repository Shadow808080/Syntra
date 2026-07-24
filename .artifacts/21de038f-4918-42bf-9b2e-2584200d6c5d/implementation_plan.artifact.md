# Implementation Plan - Fix Unresolved reference 'TextFields'

The build is failing because the `TextFields` icon from the `material-icons-extended` library is being used in `TextStoryScreen.kt` without a proper import. In Jetpack Compose, icons in the extended library are implemented as extension properties on `Icons.Filled` and require an explicit import even if the "parent" object (`Icons.Filled`) is accessed via its fully qualified name.

## Proposed Changes

### [Component Name]

#### [MODIFY] [TextStoryScreen.kt](file:///C:/Users/user/Documents/PROJECTS/syntra/Syntra/app/src/main/java/com/example/syntra/TextStoryScreen.kt)
- Add `import androidx.compose.material.icons.filled.TextFields`.
- Add `import androidx.compose.material.icons.automirrored.filled.Send` (to ensure it also works correctly without fully qualified names).
- Remove unused `import androidx.compose.material.icons.filled.Close`.
- Simplify usages of `TextFields` and `Send` icons to use `Icons.Filled.TextFields` and `Icons.AutoMirrored.Filled.Send` respectively.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to verify that the unresolved reference error is resolved and the project builds successfully.
