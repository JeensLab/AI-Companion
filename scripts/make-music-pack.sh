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

command -v ffprobe >/dev/null 2>&1 || fail "ffprobe is needed to check the recordings (install ffmpeg)"
command -v zip >/dev/null 2>&1 || fail "zip is needed to build the pack"

# Every recording must be what the game can play from where he sits: mono, 32 kHz, and small (about 34 kbps, as the pair the mod
# used to carry). A file that is not is refused, so the pack never ships something the evening would have to skip.
total=0
for file in "${tracks[@]}"; do
  name="$(basename "$file")"
  probe="$(ffprobe -v error -select_streams a:0 -show_entries stream=codec_name,channels,sample_rate \
           -show_entries format=duration -of default=nw=1:nk=0 "$file")" || fail "$name: ffprobe failed"
  codec="$(printf '%s\n' "$probe" | sed -n 's/^codec_name=//p')"
  channels="$(printf '%s\n' "$probe" | sed -n 's/^channels=//p')"
  rate="$(printf '%s\n' "$probe" | sed -n 's/^sample_rate=//p')"
  duration="$(printf '%s\n' "$probe" | sed -n 's/^duration=//p')"
  [ "$codec" = "vorbis" ] || fail "$name: $codec, not Ogg Vorbis"
  [ "$channels" = "1" ] || fail "$name: $channels channels, mono is needed to sound from a place"
  [ "$rate" = "32000" ] || fail "$name: $rate Hz, 32000 is the pack's sample rate"
  size="$(stat -c%s "$file")"
  bps="$(awk -v s="$size" -v d="$duration" 'BEGIN { printf "%d", s * 8 / d }')"
  [ "$bps" -le 45000 ] || fail "$name: $bps bps, over the 45 kbps the pack keeps to"
  printf '%s: %s s, %s bytes, %s bps, mono 32 kHz\n' "$name" "$duration" "$size" "$bps"
  total=$((total + size))
done
printf 'total: %s bytes in %s recordings\n' "$total" "${#tracks[@]}"

stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT
mkdir -p "$stage/$folder"
cp "${tracks[@]}" "$stage/$folder/"
rm -f "$out"
(cd "$stage" && zip -X -q -r "$root/$out" "$folder")

echo "built $root/$out"
unzip -l "$out"
