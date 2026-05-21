file_path = "app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt"

with open(file_path, 'r') as f:
    content = f.read()

# 1. sessionSettingsRenderModel(state) -> sessionSettingsRenderModel(state, TestAppTextResolver())
content = content.replace(
    "val model = sessionSettingsRenderModel(state)",
    "val model = sessionSettingsRenderModel(state, TestAppTextResolver())"
)

# 2. watermarkLabDetailRenderModel(state=..., templateId=...) - single line
content = content.replace(
    'watermarkLabDetailRenderModel(\n            state = defaultSessionState(),\n            templateId = "classic-overlay"\n        )',
    'watermarkLabDetailRenderModel(\n            state = defaultSessionState(),\n            templateId = "classic-overlay",\n            text = TestAppTextResolver()\n        )'
)

# 3. watermarkLabDetailRenderModel - multi-line with templateId on separate line (travel-polaroid)
# This one has templateId = "travel-polaroid" after a long state block
content = content.replace(
    '            templateId = "travel-polaroid"\n        )',
    '            templateId = "travel-polaroid",\n            text = TestAppTextResolver()\n        )'
)

# 4. filterLabPageRenderModel - single line state with activeMode
content = content.replace(
    'val model = filterLabPageRenderModel(\n            defaultSessionState(\n                activeMode = ModeId.PORTRAIT,',
    'val model = filterLabPageRenderModel(\n            defaultSessionState(\n                activeMode = ModeId.PORTRAIT,'
)
# Need to find the closing of this call and add text param
# Let me use a different approach - find all filterLabPageRenderModel calls and add text

import re

# 5. filterLabPageRenderModel(state = defaultSessionState(), selectedFamily = ...)
content = content.replace(
    'filterLabPageRenderModel(\n            state = defaultSessionState(),\n            selectedFamily = FilterLabFamily.HUMANISTIC\n        )',
    'filterLabPageRenderModel(\n            state = defaultSessionState(),\n            text = TestAppTextResolver(),\n            selectedFamily = FilterLabFamily.HUMANISTIC\n        )'
)

# 6. filterLabPageRenderModel with state=state, selectedFamily=..., showAdjustmentPanel=true, adjustmentMode=...
# Pattern 1: LIGHT
content = content.replace(
    'filterLabPageRenderModel(\n            state = state,\n            selectedFamily = FilterLabFamily.PORTRAIT,\n            showAdjustmentPanel = true,\n            adjustmentMode = FilterAdjustmentMode.LIGHT\n        )',
    'filterLabPageRenderModel(\n            state = state,\n            text = TestAppTextResolver(),\n            selectedFamily = FilterLabFamily.PORTRAIT,\n            showAdjustmentPanel = true,\n            adjustmentMode = FilterAdjustmentMode.LIGHT\n        )'
)

# Pattern 2: ADVANCED
content = content.replace(
    'filterLabPageRenderModel(\n            state = defaultSessionState(activeMode = ModeId.PORTRAIT),\n            selectedFamily = FilterLabFamily.PORTRAIT,\n            showAdjustmentPanel = true,\n            adjustmentMode = FilterAdjustmentMode.ADVANCED\n        )',
    'filterLabPageRenderModel(\n            state = defaultSessionState(activeMode = ModeId.PORTRAIT),\n            text = TestAppTextResolver(),\n            selectedFamily = FilterLabFamily.PORTRAIT,\n            showAdjustmentPanel = true,\n            adjustmentMode = FilterAdjustmentMode.ADVANCED\n        )'
)

# 7. modeDirectoryRenderModel(state) - all occurrences
content = content.replace(
    "val model = modeDirectoryRenderModel(state)",
    "val model = modeDirectoryRenderModel(state, TestAppTextResolver())"
)

# 8. modeDirectoryText(state) - all occurrences
content = content.replace(
    "modeDirectoryText(state)",
    "modeDirectoryText(state, TestAppTextResolver())"
)

with open(file_path, 'w') as f:
    f.write(content)

print("Fixed all remaining test render function calls")
