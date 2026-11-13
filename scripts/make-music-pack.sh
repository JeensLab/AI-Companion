#!/usr/bin/env bash
# Builds the release pack of Oleg's guitar tracks: oleg-guitar-tracks.zip with the ready structure
# config/aicompanion/music/*.ogg. The recordings live in music/ at the repository root, outside src/main/resources: nothing of
# them ships in the JAR, so this script is the only way the pack is made. CI runs it in the build job and uploads the zip as an
# artifact; the release attaches that artifact.
#
# Every recording's mood is the digit its name starts with (entity/CompanionMusicLibrary): 1 (sad) to 9 (happy), 1–4 calm, 6–9
# cheerful, 5 neither. The digit travels with the file into the pack, so «сыграй грустное» and «повеселее» find their kind there.
#
# The archive is reproducible: every entry gets one fixed time and mode, and the entries are written in a sorted order without
# extra fields, so two builds of the same music/ give the same bytes and the same sha256.
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
# position (entity/CompanionMusicLibrary) — so a file the evening would have to skip is refused here instead of shipping. The
# shipped set must also carry a mood: a name that starts with no digit, or with a longer number such as a year, is refused, so the
# pack never loses the calm and cheerful tunes «сыграй грустное» and «повеселее» need.
python3 - "$music" <<'PY'
import sys, pathlib

folder = pathlib.Path(sys.argv[1])
marker = b"\x01vorbis"
total = 0
count = 0
failed = False

def mood(name):
    if not name or name[0] not in "123456789":
        return None
    if len(name) > 1 and name[1].isdigit():
        return None
    return "calm" if int(name[0]) <= 4 else "happy" if int(name[0]) >= 6 else None

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
    kind = mood(file.name)
    problems = []
    if channels != 1:
        problems.append(f"{channels} channels, mono is needed to sound from a place")
    if rate != 32000:
        problems.append(f"{rate} Hz, 32000 is the pack's sample rate")
    if bps > 45000:
        problems.append(f"{bps:.0f} bps, over the 45 kbps the pack keeps to")
    if kind is None:
        problems.append("no mood: the name must start with 1–9 (1–4 calm, 6–9 happy)")
    print(f"{file.name}: {seconds:.1f} s, {size} bytes, {bps:.0f} bps, mono 32 kHz, {kind or 'no mood'}" if not problems
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
# One fixed mode and one fixed time on every entry (1980-01-01 00:00 UTC, the earliest a zip can carry), then the entries in a
# sorted order with no extra fields: two runs of this script give one archive, byte for byte.
chmod 644 "$stage/$folder"/*.ogg
chmod 755 "$stage" "$stage/config" "$stage/config/aicompanion" "$stage/$folder"
TZ=UTC touch -t 198001010000 "$stage" "$stage/config" "$stage/config/aicompanion" "$stage/$folder" "$stage/$folder"/*.ogg
rm -f "$out"
(cd "$stage" && find . -mindepth 1 | LC_ALL=C sort | sed 's|^\./||' | TZ=UTC zip -X -q "$root/$out" -@)

echo "built $root/$out"
unzip -l "$out"
