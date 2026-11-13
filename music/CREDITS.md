# Guitar recordings / Записи гитары

Real recordings of solo acoustic (classical) guitar, instrumental only, taken from Wikimedia Commons.
Every file here was changed in the same way: mixed down to mono, silence trimmed at both ends, loudness evened out
(EBU R128, −17 LUFS), a short fade added at the end, re-encoded as Ogg Vorbis 32 kHz. The changed files keep the
licence of their source; for CC BY-SA sources the changed files are shared under the same CC BY-SA licence.

The campfire evening, `/sit` and these recordings were written by 0r1k (PR #142); this mod keeps them and ships no
music of its own. The whole set is distributed as a separate archive, `oleg-guitar-tracks.zip`, built only by
`scripts/make-music-pack.sh` and attached to the release; unpacked, it is `config/aicompanion/music` (the folder
entity/CompanionMusicLibrary reads). All seven files were re-encoded once more at a lower bitrate (Ogg Vorbis,
quality 0, mono 32 kHz): 4 238 203 bytes instead of 6 373 127, 34% smaller, with the audible band kept up to the
16 kHz the format allows at this sample rate.

Настоящие записи сольной акустической (классической) гитары, без слов, с Wikimedia Commons. Каждый файл сведён
в моно, обрезана тишина по краям, выровнена громкость, добавлено затухание в конце, перекодирован в Ogg Vorbis.
Изменённые файлы распространяются на условиях лицензии исходника (для CC BY-SA — на тех же условиях CC BY-SA).

Вечер у костра, `/sit` и эти записи написал 0r1k (PR #142); мод их хранит и своей музыки не везёт. Весь набор
распространяется отдельным архивом `oleg-guitar-tracks.zip`, который собирает только `scripts/make-music-pack.sh`,
и прикладывается к выпуску; в распакованном виде это `config/aicompanion/music` — папка, которую читает
entity/CompanionMusicLibrary. Все семь файлов перекодированы ещё раз с меньшим битрейтом (Ogg Vorbis, качество 0,
моно 32 кГц): 4 238 203 байта вместо 6 373 127 — на 34% меньше, слышимый диапазон сохранён до 16 кГц, которые
допускает формат при такой частоте дискретизации.

| File | Piece | Performer | Licence | Source |
| --- | --- | --- | --- | --- |
| romance.ogg | Romance Anónimo (Spanish Romance), composer unknown | Jim Greeninger (via Musopen) | CC0 1.0 | https://commons.wikimedia.org/wiki/File:Romance_An%C3%B3nimo_(Jeux_interdits).ogg |
| gavotte.ogg | Suite in F-sharp minor, P. 439 — III. Gavotte, Johann Pachelbel | Bert Alink | CC BY 4.0 | https://commons.wikimedia.org/wiki/File:Pachelbel_-_Suite_in_F-sharp_minor,_P._439_-_III._Gavotte_(Guitar).ogg |
| noi.ogg | El Noi de la Mare, Catalan folk song | Jujutacular | Public domain | https://commons.wikimedia.org/wiki/File:El_Noi_de_la_Mare_(guitar).ogg |
| recuerdos.ogg | Recuerdos de la Alhambra, Francisco Tárrega | Carlo Alberto Boni | CC BY-SA 3.0 | https://commons.wikimedia.org/wiki/File:Recuerdos_de_la_Alhambra.ogg |
| fugue143.ogg | Fugue in C major (no. 4, bicinium), P. 143, Johann Pachelbel | Bert Alink | CC BY 4.0 | https://commons.wikimedia.org/wiki/File:Pachelbel_-_Fugue_in_C_major_(no._4,_bicinium),_P._143_(Guitar).ogg |
| fugue144.ogg | Fugue in C major (no. 5, bicinium), P. 144, Johann Pachelbel | Bert Alink | CC BY 4.0 | https://commons.wikimedia.org/wiki/File:Pachelbel_-_Fugue_in_C_major_(no._5,_bicinium),_P._144_(Guitar).ogg |
| canon.ogg | Canon in D major, P. 37, Johann Pachelbel | Aitua (Free Music Archive) | CC BY-SA 4.0 | https://commons.wikimedia.org/wiki/File:Pachelbel_-_Canon_in_D_major,_P._37_(Guitar).ogg |

Licences: CC0 1.0 — https://creativecommons.org/publicdomain/zero/1.0/ ;
CC BY 4.0 — https://creativecommons.org/licenses/by/4.0/ ; CC BY-SA 3.0 — https://creativecommons.org/licenses/by-sa/3.0/ ;
CC BY-SA 4.0 — https://creativecommons.org/licenses/by-sa/4.0/

## The originals / Исходники

The recordings above are trimmed and re-encoded; the originals are longer and not trimmed, under the same licences.
Any Ogg Vorbis file works in the folder — the game reads a file's length itself — so these can be used instead.

Записи выше обрезаны и перекодированы; исходники длиннее и без обрезки, лицензии те же. В папке подходит любой
файл Ogg Vorbis — длину игра читает сама, — поэтому можно взять и их.
