def slugify(s):
    result = ""
    for c in s.lower():
        if c.isalnum():
            result += c
        else:
            result += "-"
    import re
    result = re.sub(r'-+', '-', result).strip('-')
    return result if result else "filter"

print(f"街头 Street -> '{slugify('街头 Street')}'")
print(f"'街头'.isalnum() = {'街头'.isalnum()}")
