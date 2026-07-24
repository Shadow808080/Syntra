# Fix Unresolved reference 'TextFields' in TextStoryScreen.kt

The build is failing because the `TextFields` icon is being used without being imported. Since it is an extension property on `Icons.Filled` (provided by the `material-icons-extended` library), it must be explicitly imported even if the `Icons` object is already in scope.

## Proposed Changes

### [Component Name]

#### [MODIFY] [TextStoryScreen.kt](file:///C:/Users/user/Documents/PROJECTS/syntra/Syntra/app/src/main/java/com/example/syntra/TextStoryScreen.kt)

- Add `import androidx.compose.material.icons.filled.TextFields`.
- Replace the fully qualified name `androidx.compose.material.icons.Icons.Filled.TextFields` with the simpler `Icons.Filled.TextFields` for consistency and readability.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to verify the build error is resolved.
