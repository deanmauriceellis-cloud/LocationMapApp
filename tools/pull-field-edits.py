#!/usr/bin/env python3
"""
S229 — pull WickedSalem field-edit JSONL files (and any referenced recon
photos) off the Lenovo and organize them per session under
/mnt/sdb-images/LMASalemFieldEdits/.

Workflow:

  1. python3 tools/pull-field-edits.py
        → adb pulls /sdcard/Documents/WickedSalemFieldEdits/edits-*.jsonl
        → adb pulls every photo_filenames[] entry from
          /sdcard/Pictures/WickedSalem-Recon/ (the KatrinaCameraManager folder)
        → groups into one folder per JSONL session
        → writes summary.txt with edit count + POI list

  2. (optional) wipe the device-side files once you've confirmed the
     local copies are good:
        ./tools/pull-field-edits.py --wipe

This sibling to pull-and-organize-burst-photos.py is intentionally simple:
each JSONL file IS a session (one process boot writes one file), so we
don't need the gap-rule splitting that the burst-photos tool needs.

JSONL schema written by FieldEditManager.kt (S229):
  {
    "schema": 1,
    "ts": <epoch_ms>,
    "session_ts": "YYYYMMDD-HHMMSS",
    "device_model": "...",
    "poi_id": "...",
    "poi_name": "...",
    "current_lat": ..., "current_lng": ...,
    "current_category": "...", "current_subcategory": "...",
    "proposed_lat": ..., "proposed_lng": ...,
    "proposed_category": "...", "proposed_subcategory": "...",
    "note": "...",
    "photo_filenames": ["recon_YYYYMMDD-HHMMSS.jpg", ...]
  }
"""
import argparse
import json
import os
import shutil
import subprocess
import sys
from datetime import datetime, timezone

ADB_SERIAL = os.environ.get("LMA_ADB_SERIAL", "HNY0CY0W")

# Device-side paths.
DEVICE_JSONL_DIR = "/sdcard/Documents/WickedSalemFieldEdits"
DEVICE_RECON_DIR = "/sdcard/Pictures/WickedSalem-Recon"

# Local-side paths.
STAGE_ROOT = "/tmp/lma-field-edits-stage"
ROOT_DIR = os.environ.get(
    "LMA_FIELD_EDITS_ROOT", "/mnt/sdb-images/LMASalemFieldEdits"
)


def adb(*args, capture: bool = False) -> str:
    cmd = ["adb", "-s", ADB_SERIAL, *args]
    if capture:
        out = subprocess.check_output(cmd, text=True, stderr=subprocess.STDOUT)
        return out
    subprocess.check_call(cmd)
    return ""


def adb_listdir(remote_dir: str) -> list[str]:
    """Return device-side filenames under remote_dir, or [] if absent."""
    try:
        out = adb("shell", f"ls -1 {remote_dir} 2>/dev/null", capture=True)
    except subprocess.CalledProcessError:
        return []
    return [line.strip() for line in out.splitlines() if line.strip()]


def adb_pull(remote: str, local: str) -> bool:
    try:
        adb("pull", remote, local)
        return True
    except subprocess.CalledProcessError as e:
        print(f"  ! adb pull failed for {remote}: {e}", file=sys.stderr)
        return False


def parse_session_ts(filename: str) -> str | None:
    """edits-20260506-191234.jsonl → '20260506-191234'."""
    if not filename.startswith("edits-") or not filename.endswith(".jsonl"):
        return None
    return filename[len("edits-") : -len(".jsonl")]


def humanize_session(session_ts: str) -> str:
    try:
        dt = datetime.strptime(session_ts, "%Y%m%d-%H%M%S")
        return dt.strftime("%Y-%m-%d %H:%M:%S")
    except ValueError:
        return session_ts


def main():
    global ADB_SERIAL
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--wipe", action="store_true",
        help="After successful local copy, wipe the device-side JSONLs "
             "(photos in WickedSalem-Recon/ are NOT wiped — that's the "
             "recon-photo album you may want to keep).",
    )
    parser.add_argument("--root", default=ROOT_DIR, help=f"Local root (default: {ROOT_DIR})")
    parser.add_argument("--serial", default=ADB_SERIAL, help=f"ADB serial (default: {ADB_SERIAL})")
    args = parser.parse_args()
    ADB_SERIAL = args.serial

    os.makedirs(args.root, exist_ok=True)

    # 1. List + pull JSONL files.
    print(f"Listing {DEVICE_JSONL_DIR} on {ADB_SERIAL} …")
    jsonl_names = [n for n in adb_listdir(DEVICE_JSONL_DIR) if n.endswith(".jsonl")]
    if not jsonl_names:
        print("  (no field-edit files on device — nothing to do)")
        return 0
    print(f"  found {len(jsonl_names)} JSONL file(s): {', '.join(jsonl_names)}")

    if os.path.exists(STAGE_ROOT):
        shutil.rmtree(STAGE_ROOT)
    os.makedirs(STAGE_ROOT, exist_ok=True)
    for n in jsonl_names:
        adb_pull(f"{DEVICE_JSONL_DIR}/{n}", os.path.join(STAGE_ROOT, n))

    # 2. Per JSONL: parse, gather photo filenames, pull photos, write session folder.
    overall_summary = []
    for jsonl_name in jsonl_names:
        session_ts = parse_session_ts(jsonl_name)
        if not session_ts:
            print(f"  skip (bad name): {jsonl_name}", file=sys.stderr)
            continue
        local_jsonl = os.path.join(STAGE_ROOT, jsonl_name)
        edits = []
        bad = 0
        with open(local_jsonl) as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    edits.append(json.loads(line))
                except json.JSONDecodeError:
                    bad += 1
        n = len(edits)
        if n == 0:
            print(f"  {jsonl_name}: 0 edits (skipping)")
            continue

        session_dir = os.path.join(args.root, f"edits-{session_ts}-{n}edits")
        os.makedirs(session_dir, exist_ok=True)
        shutil.copy2(local_jsonl, os.path.join(session_dir, jsonl_name))

        # Photo filenames referenced by any edit in this session.
        photo_names: set[str] = set()
        for e in edits:
            for f in e.get("photo_filenames", []) or []:
                photo_names.add(f)

        photos_dir = os.path.join(session_dir, "photos")
        photos_pulled = 0
        photos_missing = 0
        if photo_names:
            os.makedirs(photos_dir, exist_ok=True)
            for fn in sorted(photo_names):
                # Try the recon album first.
                local_path = os.path.join(photos_dir, fn)
                if adb_pull(f"{DEVICE_RECON_DIR}/{fn}", local_path):
                    photos_pulled += 1
                else:
                    photos_missing += 1

        # summary.txt
        summary_path = os.path.join(session_dir, "summary.txt")
        with open(summary_path, "w") as fh:
            fh.write(f"WickedSalem field-edit session\n")
            fh.write("=" * 32 + "\n")
            fh.write(f"Session: {humanize_session(session_ts)} ({session_ts})\n")
            fh.write(f"JSONL:   {jsonl_name}\n")
            fh.write(f"Edits:   {n}{'  (' + str(bad) + ' malformed lines skipped)' if bad else ''}\n")
            if photo_names:
                fh.write(f"Photos:  {photos_pulled}/{len(photo_names)} pulled, {photos_missing} missing\n")
            else:
                fh.write("Photos:  none referenced\n")
            fh.write("\nEdits (chronological):\n")
            for i, e in enumerate(edits, 1):
                fh.write(f"\n  [{i}] {e.get('poi_name', '?')}  ({e.get('poi_id', '?')})\n")
                cur_lat, cur_lng = e.get("current_lat"), e.get("current_lng")
                p_lat, p_lng = e.get("proposed_lat"), e.get("proposed_lng")
                if p_lat is not None and p_lng is not None:
                    fh.write(f"      move:  {cur_lat:.5f},{cur_lng:.5f}  →  {p_lat:.5f},{p_lng:.5f}\n")
                if e.get("proposed_category"):
                    fh.write(f"      cat:   {e.get('current_category')}  →  {e.get('proposed_category')}\n")
                if e.get("proposed_subcategory"):
                    fh.write(f"      sub:   →  {e.get('proposed_subcategory')}\n")
                if e.get("note"):
                    fh.write(f"      note:  {e.get('note')}\n")
                pf = e.get("photo_filenames") or []
                if pf:
                    fh.write(f"      photos: {', '.join(pf)}\n")

        print(f"  {jsonl_name}: {n} edit(s) → {session_dir}"
              + (f" ({photos_pulled} photos)" if photo_names else ""))
        overall_summary.append((session_ts, n, photos_pulled, photos_missing))

    # 3. Optional device-side wipe of the JSONLs (kept photos as-is).
    if args.wipe and overall_summary:
        print("Wiping device-side JSONL files …")
        adb("shell", f"rm -f {DEVICE_JSONL_DIR}/*.jsonl")
        adb(
            "shell",
            f"am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE "
            f"-d file://{DEVICE_JSONL_DIR}/",
        )

    print("\nDone. Root:", args.root)
    return 0


if __name__ == "__main__":
    sys.exit(main())
