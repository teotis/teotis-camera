# 04-document-organizer-compact-close Status

**Status**: completed

## Worktree And Branch

- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/real-device-ux-regression-20260527/04-document-organizer-compact-close`
- Branch: `agent/real-device-ux-regression-20260527/04-document-organizer-compact-close`
- Base commit: `ade9380`
- Commit hash: `dcfb3b1`

## Verification

- CockpitPanelRouterTest: all 58 tests passed (including 3 new CloseDocumentBatchOrganizer tests)
- DocumentBatchOrganizerRenderModelTest: all tests passed
- DocumentBatchRailRenderModelTest: all tests passed
- SessionUiRenderModelTest: all tests passed
- assembleDebug: BUILD SUCCESSFUL

## Evidence

### Before
- Organizer panel constrained from `topPanel` to `secondaryPanelBottomGuide` (0.7), occupying ~60% of screen height
- Close button used `ToggleDocumentBatchOrganizer` (toggle behavior, unreliable dismiss)
- Item rows used 48dp thumbnails, 8dp padding, 32dp buttons

### After
- Organizer panel constrained from `secondaryPanelBottomGuide` to `modeTrackScroll`, max 240dp height, compact bottom sheet
- Close button uses `CloseDocumentBatchOrganizer` (explicit dismiss, one-tap close)
- Item rows use 36dp thumbnails, 6dp padding, 28dp buttons
- Close button text changed to dedicated「关闭」string

### Changed Files
- `CockpitPanelRouter.kt`: added `CloseDocumentBatchOrganizer` command and handler
- `DocumentBatchOrganizerRenderer.kt`: reduced item dimensions for compact layout
- `MainActivityActionBinder.kt`: close button wired to `CloseDocumentBatchOrganizer`
- `activity_main.xml`: organizer panel constraints changed to compact bottom sheet
- `values/strings.xml`: added `button_document_batch_organizer_close` string
- `CockpitPanelRouterTest.kt`: added 3 tests for close command

## Risks / Residual Device QA

- Real-device visual confirmation needed for compact panel height and close behavior
- Organizer item row density should be verified on actual device
