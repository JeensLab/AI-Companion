#!/usr/bin/env bash
# Builds the release pack of Oleg's guitar tracks: oleg-guitar-tracks.zip with the ready structure
# config/aicompanion/music/*.ogg. The recordings live in music/ at the repository root, outside src/main/resources: nothing of
# them ships in the JAR, so this script is the only way the pack is made. CI runs it in the build job and uploads the zip as an
# artifact; the release attaches that artifact.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

music="music"
out="oleg-guitar-tracks.zip"
folder="config/aicompanion/music"

fail() { echo "make-music-pack: $*" >&2; exit 1; }

[ -d "$music" ] || fail "no $music folder in $root"
mapfile -t tracks < <(find "$music" -maxdepth 1 -type f -name '*.ogg' | sort)
[ "${#tracks[@]}" -gt 0 ] || fail "no .ogg recordings in $music"

command -v python3 >/dev/null 2>&1 || fail "python3 is needed to check the recordings"
command -v zip >/dev/null 2>&1 || fail "zip is needed to build the pack"

# Every recording must be what the game can play from where he sits: mono, 32 kHz, and small (about 34 kbps, as the pair the mod
# used to carry). The check reads the file exactly as the game does — the Vorbis identification header and the last page's granule
# position (entity/CompanionMusicLibrary) — so a file the evening would have to skip is refused here instead of shipping.
python3 - "$music" <<'PY'
import sys, pathlib

folder = pathlib.Path(sys.argv[1])
marker = b"\x01vorbis"
total = 0
count = 0
failed = False
for file in sorted(folder.glob("*.ogg")):
    data = file.read_bytes()
    size = len(data)
    head = data[:65536]
    at = head.find(marker)
    if at < 0 or at + 16 > len(head):
        print(f"{file.name}: not Ogg Vorbis", file=sys.stderr)
        failed = True
        continue
    channels = data[at + 11]
    rate = int.from_bytes(data[at + 12:at + 16], "little")
    granule = -1
    tail = data[-65536:]
    page = tail.find(b"OggS")
    while page >= 0 and page + 14 <= len(tail):
        samples = int.from_bytes(tail[page + 6:page + 14], "little")
        if samples >= 0:
            granule = samples
        page = tail.find(b"OggS", page + 4)
    if rate <= 0 or granule <= 0:
        print(f"{file.name}: no sample rate or granule position", file=sys.stderr)
        failed = True
        continue
    seconds = granule / rate
    bps = size * 8 / seconds
    problems = []
    if channels != 1:
        problems.append(f"{channels} channels, mono is needed to sound from a place")
    if rate != 32000:
        problems.append(f"{rate} Hz, 32000 is the pack's sample rate")
    if bps > 45000:
        problems.append(f"{bps:.0f} bps, over the 45 kbps the pack keeps to")
    print(f"{file.name}: {seconds:.1f} s, {size} bytes, {bps:.0f} bps, mono 32 kHz" if not problems
          else f"{file.name}: " + "; ".join(problems), file=sys.stderr if problems else sys.stdout)
    failed = failed or bool(problems)
    total += size
    count += 1
print(f"total: {total} bytes in {count} recordings")
sys.exit(1 if failed else 0)
PY

stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT
mkdir -p "$stage/$folder"
cp "${tracks[@]}" "$stage/$folder/"
rm -f "$out"
(cd "$stage" && zip -X -q -r "$root/$out" "$folder")

echo "built $root/$out"
unzip -l "$out"
