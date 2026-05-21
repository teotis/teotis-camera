file_path = "app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt"

with open(file_path, 'r') as f:
    content = f.read()

# 1. sessionSummaryText(state) -> sessionSummaryText(state, TestAppTextResolver())
content = content.replace(
    "val summary = sessionSummaryText(state)",
    "val summary = sessionSummaryText(state, TestAppTextResolver())"
)

# 2. sessionSettingsPageRenderModel(defaultSessionState()) - single line
content = content.replace(
    "sessionSettingsPageRenderModel(defaultSessionState())",
    "sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())"
)

# 3. Multi-line sessionSettingsPageRenderModel with defaultSessionState( ... )
# Pattern: sessionSettingsPageRenderModel(\n            defaultSessionState(\n ... \n            )\n        )
# Need to add TestAppTextResolver() after the defaultSessionState closing paren
import re
content = re.sub(
    r'(sessionSettingsPageRenderModel\(\s*defaultSessionState\([^)]*(?:\([^)]*\))*[^)]*\)\s*)\n(\s*\))',
    r'\1,\n            TestAppTextResolver()\n\2',
    content
)

# 4. watermarkLabSelectorRenderModel(defaultSessionState()) - single line
content = content.replace(
    "watermarkLabSelectorRenderModel(defaultSessionState())",
    "watermarkLabSelectorRenderModel(defaultSessionState(), TestAppTextResolver())"
)

# 5. Multi-line portraitLabPageRenderModel with baseline.copy( ... )
content = re.sub(
    r'(portraitLabPageRenderModel\(\s*baseline\.copy\([^)]*(?:\([^)]*\))*[^)]*\)\s*)\n(\s*\))',
    r'\1,\n            TestAppTextResolver()\n\2',
    content
)

with open(file_path, 'w') as f:
    f.write(content)

print("Fixed remaining test render function calls")
