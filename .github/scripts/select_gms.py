"""Preserve Build PR's path exclusions without skipping FOSS checks."""
import json
import os
from pathlib import Path
import re
import subprocess


IGNORED = re.compile(
    rb"(?:app/src/main/res/values-[^/]*/(?:strings|metrolist_strings)\.xml|fastlane/.*)",
    re.DOTALL,
)


def select(event_name, event, cwd=None):
    if event_name != "pull_request":
        return False, "GMS is PR-only; FOSS runs"
    try:
        pr = event["pull_request"]
        base, head = pr["base"]["sha"], pr["head"]["sha"]
        if not all(re.fullmatch(r"[0-9a-f]{40}", sha) for sha in (base, head)):
            raise ValueError("missing or invalid PR SHAs")

        def git(*args):
            return subprocess.run(
                ["git", *args], cwd=cwd, check=True, stdout=subprocess.PIPE,
                stderr=subprocess.PIPE, timeout=30,
            ).stdout

        bases = git("merge-base", "--all", base, head).splitlines()
        if len(bases) != 1:
            return True, "ambiguous merge base: conservatively run GMS"
        merge_base = bases[0].decode("ascii")
        # Disabling rename detection includes BOTH sides as delete/add records.
        paths = git("diff", "--no-renames", "--name-only", "-z", merge_base, head, "--")
        if not paths or not paths.endswith(b"\0"):
            return True, "empty or incomplete diff: conservatively run GMS"
        if all(IGNORED.fullmatch(path) for path in paths[:-1].split(b"\0")):
            return False, "only localized strings / fastlane changed; FOSS still runs"
        return True, "PR includes paths outside the GMS exclusions"
    except (KeyError, TypeError, ValueError, OSError, subprocess.SubprocessError):
        return True, "PR diff unavailable: conservatively run GMS"


if __name__ == "__main__":
    event_name = os.environ["GITHUB_EVENT_NAME"]
    try:
        event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
    except (OSError, ValueError):
        event = {}
    selected, reason = select(event_name, event)
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        output.write(f"gms={str(selected).lower()}\n")
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
        summary.write(f"FOSS: selected. GMS: {selected}. {reason}.\n")
