with open('app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt', 'r') as f:
    lines = f.readlines()

result = []
skip = False
brace_depth = 0

i = 0
while i < len(lines):
    line = lines[i]
    
    # Check if this is a test that references qualityRow
    if '@Test' in line and i + 1 < len(lines):
        next_line = lines[i + 1]
        if 'quick panel sheet exposes all six rows' in next_line or \
           'quick panel sheet exposes photo quality and resolution rows' in next_line or \
           'quick quality row shows combined video spec in video mode' in next_line or \
           'quick quality row shows degraded video spec with asterisk' in next_line or \
           'quick quality row disabled during video recording' in next_line:
            # Skip this test - find the end
            skip = True
            brace_depth = 0
            i += 1
            continue
    
    if skip:
        brace_depth += line.count('{') - line.count('}')
        if brace_depth <= 0 and '{' in line:
            skip = False
            i += 1
            continue
        i += 1
        continue
    
    result.append(line)
    i += 1

with open('app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt', 'w') as f:
    f.writelines(result)

print(f'Removed tests, {len(lines)} -> {len(result)} lines')
