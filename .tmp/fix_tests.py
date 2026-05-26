import re

with open('app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt', 'r') as f:
    content = f.read()

# Remove tests that reference qualityRow
tests_to_remove = [
    'quick panel sheet exposes all six rows',
    'quick panel sheet exposes photo quality and resolution rows',
    'quick quality row shows combined video spec in video mode',
    'quick quality row shows degraded video spec with asterisk',
    'quick quality row disabled during video recording',
]

for test_name in tests_to_remove:
    pattern = r'    @Test\n    fun `' + re.escape(test_name) + r'` \{.*?\n    \}\n'
    content = re.sub(pattern, '', content, flags=re.DOTALL)

with open('app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt', 'w') as f:
    f.write(content)

print('Removed qualityRow tests')
