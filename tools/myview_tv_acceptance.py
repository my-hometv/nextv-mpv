#!/usr/bin/env python3
"""MyView on-device acceptance evidence collector. Never builds or publishes APKs.

Requires Android platform-tools (adb), Python 3.9+, a TV with USB/network debugging
authorized, and an independently installed MyView test build.
Only locally generated reports; no network uploads. Logs are filtered and redacted.
"""
import argparse
import datetime as dt
import json
import os
import pathlib
import re
import subprocess
import sys

CASES = [
    ("TV-01", "Fresh launch/resume: stable video/audio, responsive remote"),
    ("TV-02", "MPEG-TS: audio/video and no inappropriate alternative prompt"),
    ("TV-03", "1080p SDR: color, aspect, lip sync, motion"),
    ("TV-04", "HDR10 on HDR-capable display: no inverted/washed-out colors"),
    ("TV-05", "HDR10 on SDR display: correct tone mapping or fallback"),
    ("TV-06", "Decoder failure: bounded recovery without crash"),
    ("TV-07", "Brief network loss: confirmed URL recovers"),
    ("TV-08", "Alternative URL/engine fallback: no stale playback crossover"),
    ("TV-09", "Repeated rapid switching: no freezes/crash/orphaned audio"),
    ("TV-10", "First-run aspect defaults to 16:9 and override persists"),
    ("TV-11", "Quality defaults to Best Available"),
    ("TV-12", "UP/DOWN only previews until OK"),
    ("TV-13", "Preview closes after default 5 seconds"),
    ("TV-14", "Preview timeout settings persist after restart"),
    ("TV-15", "OK/BACK actions and drawer dismissal"),
    ("TV-16", "Numeric channels remain playlist-specific"),
    ("TV-17", "LEFT LEFT LEFT drawer navigation and DPAD focus"),
    ("TV-18", "Clock seconds advance; default 10s time / 5s date"),
    ("TV-19", "Clock settings persist and apply"),
    ("TV-20", "Adjacent preload uses at most one speculative decoder"),
    ("TV-21", "Ready prebuffer promotes without second stream startup"),
    ("TV-22", "Not-ready preload safely falls back"),
    ("TV-23", "Disabled preloading releases speculative player"),
    ("TV-24", "Locked/MultiView/LibVLC preloading respects restrictions"),
    ("TV-25", "2/4-way MultiView respects decoder limits"),
    ("TV-26", "HOME/background/resume has no duplicate audio"),
    ("TV-27", "50+ switches: decoder count/memory not continuously growing"),
    ("TV-28", "TV cold start resumes eligible channel safely"),
    ("TV-29", "Frame-rate matching is VOD-only and off by default"),
    ("TV-30", "Physical DPAD, numbers, CH+/-, menu and media keys"),
]
SECRET = re.compile(r"(?i)(https?://[^\s)\]>'\"]+|(?:token|password|authorization|apikey|api_key|license_key|secret)\s*[:=]\s*[^\s,;]+)")
CRASH = re.compile(r"(?i)(fatal exception|androidruntime|signal 11|sigsegv|sigabrt|ANR in |am_crash|tombstone|OutOfMemoryError|mediacodec.*error)")
def redact(s):
    return SECRET.sub("[REDACTED]", s)

def adb(serial, *args, timeout=25):
    command = ["adb"] + (["-s", serial] if serial else []) + list(args)
    try:
        res = subprocess.run(command, capture_output=True, text=True, timeout=timeout, check=False)
        return (res.stdout or "")[:250000], (res.stderr or "")[:1000], res.returncode
    except (OSError, subprocess.TimeoutExpired) as e:
        return "", str(e), 1

def command_output(serial, *args, max_output=18000):
    out, err, status = adb(serial, *args)
    # Redact before truncation; allow longer tails for diagnostics when requested.
    return {"ok": status == 0, "output": redact(out[-max_output:]), "error": redact(err),
            "truncated": len(out) >= max_output}

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--serial", help="adb serial (required if more than one device)")
    ap.add_argument("--package", default="com.softppj.myview")
    ap.add_argument("--output", default="myview-tv-evidence")
    ap.add_argument("--interactive", action="store_true", help="Ask tester to grade all 30 scenarios")
    ns = ap.parse_args()
    dest = pathlib.Path(ns.output)
    dest.mkdir(parents=True, exist_ok=True)
    devices, err, rc = adb(None, "devices")
    connected = [line.split()[0] for line in devices.splitlines()[1:] if line.endswith("\tdevice")]
    if rc != 0 or not connected or (len(connected) > 1 and not ns.serial):
        sys.exit("Connect and authorize exactly one TV (or specify --serial). " + redact(err))
    serial = ns.serial or connected[0]
    if serial not in connected:
        sys.exit("Specified TV is not in adb devices")
    props = {}
    for name in ("ro.product.manufacturer", "ro.product.model", "ro.build.version.release",
                 "ro.product.cpu.abi", "ro.build.version.sdk"):
        props[name] = command_output(serial, "shell", "getprop", name)["output"].strip()
    installed = command_output(serial, "shell", "pm", "path", ns.package)
    report = {
        "title": "MyView physical TV acceptance", "utc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "device": props, "package": ns.package,
        "installed": installed["ok"] and "package:" in installed["output"],
        "apk_generated": False, "apk_published": False,
        "method": "ADB evidence and optional human grading; no synthetic PASS",
        "checks": {code: {"description": description, "status": "NOT_TESTED", "notes": ""}
                   for code, description in CASES},
        "device_capabilities": {
            "display": command_output(serial, "shell", "dumpsys", "display"),
            "codec": command_output(serial, "shell", "dumpsys", "media.codec"),
            "app_process": command_output(serial, "shell", "pidof", ns.package),
        },
    }
    if ns.interactive:
        print("Observe each scenario on the physical TV; press Enter for NOT_TESTED.")
        for code, description in CASES:
            print(f"\n{code}: {description}")
            value = input("PASS / FAIL / BLOCKED / Enter (not tested): ").strip().upper()
            if value not in ("PASS", "FAIL", "BLOCKED"):
                value = "NOT_TESTED"
            note = input("Evidence/notes (avoid private URLs and license data): ").strip()
            # A bare affirmative response is insufficient release evidence.
            if value == "PASS" and not note:
                print("PASS requires a brief observation; recording NOT_TESTED instead.")
                value = "NOT_TESTED"
            report["checks"][code] = {"description": description, "status": value, "notes": redact(note)}
    logs = command_output(serial, "logcat", "-d", "-t", "3000", "-v", "brief", max_output=250000)
    report["crash_indicators"] = [redact(line[:600]) for line in logs["output"].splitlines()
                                  if CRASH.search(line)][:100]
    report["logcat_collection_ok"] = logs["ok"]
    report["logcat_truncated"] = logs["truncated"]
    statuses = [v["status"] for v in report["checks"].values()]
    report["release_gate"] = ("CANDIDATE_FOR_REVIEW" if report["installed"] and
                             logs["ok"] and not report["logcat_truncated"] and
                             all(x == "PASS" for x in statuses) and not report["crash_indicators"]
                             else "HOLD")
    # Even candidate status is NOT certification: an engineer must verify observations and logs.
    output = dest / "acceptance.json"
    # Keep device evidence private on supported POSIX filesystems.
    payload = json.dumps(report, indent=2, ensure_ascii=False) + "\n"
    fd = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as handle:
        handle.write(payload)
    print(f"Recorded {output}; gate={report['release_gate']}; "
          f"{statuses.count('PASS')}/30 observed PASS; "
          f"{len(report['crash_indicators'])} recent crash indicators")
    if not logs["ok"]:
        print("Could not collect logcat; release gate remains HOLD.")
    if report["logcat_truncated"]:
        print("Logcat output reached collection limit; release gate remains HOLD.")
    if not report["installed"]:
        print("MyView package not found. APK build remains a separate, explicitly approved step.")

if __name__ == "__main__":
    main()
