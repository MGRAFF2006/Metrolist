"""Reject a failed or accidentally skipped required step in the single CI job."""
import json
import os


def failures(steps, gms_selected):
    required = ["checkout", "select", "java", "android", "sdk", "gradle",
                "executable", "foss_key", "foss", "foss_reports"]
    if gms_selected:
        required += ["pr_keystore", "gms", "gms_apk"]
        if steps.get("pr_keystore", {}).get("outputs", {}).get("cache-hit") != "true":
            required += ["gms_key", "save_key"]
    bad = {name for name in required if steps.get(name, {}).get("outcome") != "success"}
    bad.update(name for name, step in steps.items() if step.get("outcome") == "failure")
    return sorted(bad)


if __name__ == "__main__":
    bad = failures(json.loads(os.environ["STEPS_JSON"]), os.environ["GMS_SELECTED"] == "true")
    if bad:
        raise SystemExit("Required CI steps failed or skipped: " + ", ".join(bad))
