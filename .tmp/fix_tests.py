import re

file_path = "app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt"

with open(file_path, 'r') as f:
    content = f.read()

# Fix all remaining render function calls that don't have text parameter
# sessionSettingsRenderModel(state) -> sessionSettingsRenderModel(state, TestAppTextResolver())
content = re.sub(
    r'sessionSettingsRenderModel\((?!.*text)',
    lambda m: m.group(0).rstrip('(') + '(',
    content
)

# sessionSettingsPageRenderModel(state) -> sessionSettingsPageRenderModel(state, TestAppTextResolver())
content = re.sub(
    r'sessionSettingsPageRenderModel\((\w+)\)',
    r'sessionSettingsPageRenderModel(\1, TestAppTextResolver())',
    content
)

# sessionSettingsPageRenderModel(state, selectedFamily = ...) -> sessionSettingsPageRenderModel(state, text = TestAppTextResolver(), selectedFamily = ...)
content = re.sub(
    r'sessionSettingsPageRenderModel\((\w+),\n\s*selectedFamily',
    r'sessionSettingsPageRenderModel(\1, text = TestAppTextResolver(), selectedFamily',
    content
)

# watermarkLabSelectorRenderModel(state) -> watermarkLabSelectorRenderModel(state, TestAppTextResolver())
content = re.sub(
    r'watermarkLabSelectorRenderModel\((\w+)\)',
    r'watermarkLabSelectorRenderModel(\1, TestAppTextResolver())',
    content
)

# portraitLabPageRenderModel(state) -> portraitLabPageRenderModel(state, TestAppTextResolver())
content = re.sub(
    r'portraitLabPageRenderModel\((\w+)\)',
    r'portraitLabPageRenderModel(\1, TestAppTextResolver())',
    content
)

# modeTrackRenderModel(state) -> modeTrackRenderModel(state, TestAppTextResolver())
content = re.sub(
    r'modeTrackRenderModel\((\w+)\)',
    r'modeTrackRenderModel(\1, TestAppTextResolver())',
    content
)

# primaryStatusRenderModel(state) -> primaryStatusRenderModel(state, TestAppTextResolver())
content = re.sub(
    r'primaryStatusRenderModel\((\w+)\)',
    r'primaryStatusRenderModel(\1, TestAppTextResolver())',
    content
)

# runtimeProControlsRenderModel(state) -> runtimeProControlsRenderModel(state, TestAppTextResolver())
content = re.sub(
    r'runtimeProControlsRenderModel\((\w+)\)',
    r'runtimeProControlsRenderModel(\1, TestAppTextResolver())',
    content
)

with open(file_path, 'w') as f:
    f.write(content)

print("Fixed all test render function calls")
