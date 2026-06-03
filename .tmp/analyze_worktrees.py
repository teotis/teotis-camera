"""Analyze which worktrees can be safely removed."""
import subprocess
import re
import os

# Get worktree list
result = subprocess.run(
    ["git", "worktree", "list"],
    capture_output=True, text=True, cwd="/Volumes/Extreme_SSD/project/open_camera"
)

# Get merged branches
merged = subprocess.run(
    ["git", "branch", "--merged", "main"],
    capture_output=True, text=True, cwd="/Volumes/Extreme_SSD/project/open_camera"
)
merged_branches = set()
for line in merged.stdout.strip().split("\n"):
    line = line.strip().lstrip("+* ")
    if line:
        merged_branches.add(line)

# Parse worktrees
worktrees = []
main_path = "/Volumes/Extreme_SSD/project/open_camera"
for line in result.stdout.strip().split("\n"):
    # Format: /path/to/worktree  HASH [branch_name]
    parts = line.split()
    if len(parts) < 2:
        continue
    path = parts[0]
    rest = " ".join(parts[1:])
    # Extract branch name from brackets
    m = re.search(r'\[(.+)\]', rest)
    branch = m.group(1) if m else "(detached)"

    if path == main_path:
        continue

    category = "unknown"
    if "/runs/" in path:
        category = "runs-workspace"
    elif "docs/plans" in path:
        category = "plans-scratch"
    elif "/.claude/worktrees/" in path:
        category = "claude-worktree"
    elif "/.worktrees/" in path:
        category = "worktree"

    merged_status = "merged" if branch in merged_branches else "not-merged"

    worktrees.append({
        "path": path,
        "branch": branch,
        "category": category,
        "merged": merged_status,
    })

# Print categorized
print("=" * 80)
print("SAFE TO REMOVE (merged or temporary):")
print("=" * 80)

to_remove = []
to_keep = []

for w in worktrees:
    if w["category"] == "runs-workspace":
        to_remove.append(w)
    elif w["category"] == "plans-scratch":
        to_remove.append(w)
    elif w["merged"] == "merged":
        to_remove.append(w)
    else:
        to_keep.append(w)

for w in to_remove:
    print(f"  [{w['category']}] {w['branch']}")

print(f"\n  TOTAL safe to remove: {len(to_remove)}")

print()
print("=" * 80)
print("KEEP (not merged, still active):")
print("=" * 80)
for w in to_keep:
    print(f"  [{w['category']}] {w['branch']}")
print(f"\n  TOTAL to keep: {len(to_keep)}")

# Also check replay_checker
print()
print("=" * 80)
print("REPLAY_CHECKER runs/ (different repo):")
print("=" * 80)
replay_runs = "/Volumes/Extreme_SSD/project/replay_checker/runs"
if os.path.exists(replay_runs):
    count = 0
    for root, dirs, files in os.walk(replay_runs):
        for d in dirs:
            if d == "workspace":
                p = os.path.join(root, d)
                print(f"  [runs-workspace] {p}")
                count += 1
    print(f"\n  TOTAL: {count}")
