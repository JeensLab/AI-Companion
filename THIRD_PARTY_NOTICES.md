# Third-party code in this repository

The mod itself is released under the licence in `gradle.properties` (`mod_license`). Two small third-party libraries are vendored
into `src/main/java` so that MP3 recordings in the player's folder can be converted to Ogg Vorbis (the only format the game's sound
engine reads) without adding a build or runtime dependency:

## JLayer 1.0.1 — MPEG audio decoder

- Files: `src/main/java/javazoom/jl/decoder/**`, resources `src/main/resources/javazoom/jl/decoder/*.ser`.
- Upstream: JavaZOOM, <https://www.javazoom.net/javalayer/javalayer.html>, artefact `javazoom:jlayer:1.0.1`.
- Licence: GNU Library General Public License, version 2 (LGPL-2.1). Every source file keeps its original copyright and licence
  header. The mod uses it as an unmodified library through its public API (`javazoom.jl.decoder.Decoder`, `Bitstream`,
  `SampleBuffer`); the mod's own code stays under its own licence.
- Local change: `@SuppressWarnings("rawtypes")` on `JavaLayerUtils` and `SynthesisFilter` only, because the build treats warnings as
  errors and the upstream 1.0.1 API takes raw `Class` objects. No behaviour is changed.

## jVorbisEnc (biniu) — pure-Java Ogg Vorbis encoder

- Files: `src/main/java/biniu/**` (packages `biniu.ogg` and `biniu.vorbis`).
- Upstream: <https://github.com/umjammer/jVorbisEnc>, a Java port of Xiph.Org's libvorbis encoder by Zbigniew Sudnik; artefact
  `biniu:jVorbisEnc:0.0.2`. It is self-contained: it carries its own Ogg writer (`biniu.ogg`) and needs nothing from the game.
- Licence: the OggVorbis BSD-style source licence (Xiph.Org). Every source file keeps its original copyright and licence header.
- Local changes, all in `biniu/vorbis/VorbisEnc.java` and `biniu/vorbis/modes/Psych44.java`, so that the port can encode every
  recording the decoder reads:
  - `Psych44._vp_tonemask_adj_otherblock`: two entries wrote `-7 - 3` instead of `-7, -3`, which left them a value short and made
    the 32/44.1/48 kHz modes fail at higher qualities.
  - `setTonemask`, `setCompandBlock`, `setPeak`, `setNoisebias`: the interpolated setting is kept inside the table it indexes (some
    ported setup tables are a value shorter than the setting a quality mode asks of them, where the upstream C tables carry the
    extra entry), and the low-rate noise guards are indexed to their last entry instead of failing for the third and fourth block.
    The result is a slightly different tuning at the very top of those tables, never a broken stream.
