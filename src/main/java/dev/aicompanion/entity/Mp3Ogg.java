package dev.aicompanion.entity;

import com.mojang.logging.LogUtils;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import org.slf4j.Logger;

import biniu.ogg.Page;
import biniu.ogg.Packet;
import biniu.ogg.StreamState;
import biniu.vorbis.Block;
import biniu.vorbis.Comment;
import biniu.vorbis.DspState;
import biniu.vorbis.Info;
import biniu.vorbis.VorbisEnc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MP3 recordings made playable for the game's sound engine, which reads Ogg Vorbis only. An .mp3 in the player's folder is
 * converted once into an Ogg Vorbis file beside it ({@code name.mp3.ogg}) and that copy is what the evening plays and the client's
 * resource pack serves; the copy is the marker, and it is made again only when the .mp3 is newer than it.
 *
 * <p>No build dependency is added: the MP3 is decoded by a vendored copy of JLayer's decoder ({@code javazoom.jl.decoder}, LGPL)
 * and the Ogg Vorbis is written by a vendored copy of the pure-Java Vorbis encoder jVorbisEnc ({@code biniu.vorbis}, the Xiph
 * BSD-style licence). See THIRD_PARTY_NOTICES.md. The copy keeps the recording's length and the mood digit of the original name
 * (the copy is named after the same stem), and it is served under the sound id the MP3's name gives.
 *
 * <p>The encoder's own setup modes cover the usual music rates (32, 44.1 and 48 kHz); a lower-rate recording is resampled to
 * 44.1 kHz on the way in, so every MP3 the decoder reads can be written. The conversion streams frame by frame, so a long
 * recording costs no more memory than a frame (a resampled one is buffered, and those are small). A file that cannot be decoded
 * or written is remembered as such and not tried again on every scan.
 */
public final class Mp3Ogg {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** What an .mp3 is converted into, beside it: {@code name.mp3} becomes {@code name.mp3.ogg}. */
    public static final String CONVERTED = ".mp3.ogg";
    /** The quality the encoder is asked for: a VBR mode near 128 kbps stereo, the music the player put there. */
    private static final float QUALITY = 0.4f;
    /** Samples handed to the encoder at once: the encoder's own block size. */
    private static final int CHUNK = 1024;
    /** The rate a recording the encoder has no mode for is converted at. */
    private static final int RESAMPLE_RATE = 44100;

    private Mp3Ogg() { }

    /** Files already found unreadable, by absolute path and modification time, so a scan does not retry them every time. */
    private static final Map<String, Boolean> FAILED = new ConcurrentHashMap<>();

    /** The copy beside an .mp3, or null when the file cannot be converted (said once in the log). */
    static Path converted(Path mp3) {
        String name = mp3.getFileName().toString();
        Path target = mp3.resolveSibling(name.substring(0, name.length() - 4) + CONVERTED);
        if (fresh(mp3, target)) return target;
        String key = mp3.toAbsolutePath() + "|" + stamp(mp3);
        if (FAILED.containsKey(key)) return null;
        synchronized (Mp3Ogg.class) {
            // Another scan (the server's and the client's both read the folder) may have made the copy meanwhile.
            if (fresh(mp3, target)) return target;
            try {
                convert(mp3, target);
                return target;
            } catch (IOException | RuntimeException error) {
                FAILED.put(key, Boolean.TRUE);
                LOGGER.warn("AI Companion: {} could not be converted to Ogg Vorbis and is skipped: {}", mp3.getFileName(), error.toString());
                return null;
            }
        }
    }

    /** A request asks for the folder afresh: a file that failed before may work now (it was half copied, say). */
    static void forgetFailures() { FAILED.clear(); }

    /** Whether the copy is there, newer than the MP3 and readable Ogg Vorbis: the marker that the conversion is done. */
    private static boolean fresh(Path mp3, Path target) {
        try {
            return Files.isRegularFile(target) && Files.getLastModifiedTime(target).compareTo(Files.getLastModifiedTime(mp3)) >= 0
                    && CompanionMusicLibrary.ticks(target) >= 20;
        } catch (IOException error) {
            return false;
        }
    }

    private static String stamp(Path file) {
        try {
            return Files.size(file) + "@" + Files.getLastModifiedTime(file).toMillis();
        } catch (IOException error) {
            return "?";
        }
    }

    /**
     * Writes the Ogg Vorbis copy of an MP3: every frame is decoded to PCM and handed to the encoder, and the pages are written as
     * they come. A half-written copy is never left behind: the file is written under a temporary name and moved into place.
     */
    static void convert(Path mp3, Path target) throws IOException {
        Path temporary = target.resolveSibling("." + target.getFileName() + ".tmp");
        try (InputStream input = new BufferedInputStream(Files.newInputStream(mp3));
             OutputStream output = new BufferedOutputStream(Files.newOutputStream(temporary))) {
            Bitstream bitstream = new Bitstream(input);
            Decoder decoder = new Decoder();
            Header header = nextFrame(bitstream);
            if (header == null) throw new IOException("no MPEG audio frame");
            SampleBuffer samples = decode(decoder, header, bitstream);
            int rate = samples.getSampleFrequency();
            int channels = samples.getChannelCount();
            if (rate < 8000 || channels < 1 || channels > 2)
                throw new IOException("unsupported MPEG audio: " + channels + " channel(s) at " + rate + " Hz");

            Encoder encoder = new Encoder(output, channels, encoderRate(rate));
            // A rate the encoder has no mode for is resampled on the way in: the recording is buffered first (those are small).
            ByteArrayOutputStream buffered = encoderRate(rate) == rate ? null : new ByteArrayOutputStream();
            while (true) {
                if (buffered != null) write(buffered, samples);
                else encoder.write(samples);
                bitstream.closeFrame();
                header = nextFrame(bitstream);
                if (header == null) break;
                samples = decode(decoder, header, bitstream);
            }
            if (buffered != null) encoder.writeResampled(buffered.toByteArray(), rate);
            encoder.finish();
            output.flush();
        }
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException fallback) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** The rate the encoder is asked for: its own setup modes cover the usual music rates, anything else is resampled. */
    private static int encoderRate(int rate) {
        return rate == 32000 || rate == 44100 || rate == 48000 ? rate : RESAMPLE_RATE;
    }

    private static void write(ByteArrayOutputStream out, SampleBuffer samples) {
        short[] pcm = samples.getBuffer();
        int length = samples.getBufferLength();
        for (int i = 0; i < length; i++) {
            out.write(pcm[i] & 0xff);
            out.write((pcm[i] >> 8) & 0xff);
        }
    }

    /** The next MPEG frame, or null at the end of the file (a broken tail ends it too, rather than failing the whole recording). */
    private static Header nextFrame(Bitstream bitstream) {
        try {
            return bitstream.readFrame();
        } catch (BitstreamException error) {
            return null;
        }
    }

    /** One decoded frame's samples; a frame the decoder refuses ends the recording rather than failing it. */
    private static SampleBuffer decode(Decoder decoder, Header header, Bitstream bitstream) throws IOException {
        try {
            return (SampleBuffer) decoder.decodeFrame(header, bitstream);
        } catch (DecoderException error) {
            throw new IOException("an MPEG audio frame could not be decoded: " + error.getMessage(), error);
        }
    }

    /** The encoder and its Ogg stream: what the decoded (and, when needed, resampled) PCM is written into. */
    private static final class Encoder {
        private final OutputStream output;
        private final int channels;
        private final int rate;
        private final Info info = new Info();
        private final Comment comment = new Comment();
        private final DspState dsp = new DspState();
        private final Block block = new Block(dsp);
        private final StreamState stream = new StreamState();
        private final Page page = new Page();
        private final Packet packet = new Packet();
        private boolean ended;

        Encoder(OutputStream output, int channels, int rate) throws IOException {
            this.output = output;
            this.channels = channels;
            this.rate = rate;
            info.init();
            int setup = new VorbisEnc().initVBR(info, channels, rate, QUALITY);
            if (setup != 0) throw new IOException("the Vorbis encoder does not support " + channels + " channel(s) at " + rate + " Hz");
            comment.init();
            comment.addTag("ENCODER", "AI Companion (jVorbisEnc)");
            dsp.analysisInit(info);
            block.blockInit(dsp);
            stream.init(("aicompanion-" + rate + "-" + channels).hashCode());
            Packet identification = new Packet(), tags = new Packet(), setupHeader = new Packet();
            dsp.analysisHeaderOut(comment, identification, tags, setupHeader);
            stream.packetIn(identification);
            stream.packetIn(tags);
            stream.packetIn(setupHeader);
            while (stream.flush(page)) write(page);
        }

        /** Hands one decoded frame to the encoder, in the encoder's block size. */
        void write(SampleBuffer samples) throws IOException {
            short[] pcm = samples.getBuffer();
            int total = samples.getBufferLength() / channels;
            for (int at = 0; at < total; at += CHUNK) {
                int count = Math.min(CHUNK, total - at);
                float[][] buffer = dsp.analysisBuffer(CHUNK);
                for (int i = 0, l = dsp.pcm_current; i < count; i++, l++)
                    for (int channel = 0; channel < channels; channel++)
                        buffer[channel][l] = pcm[(at + i) * channels + channel] / 32768.f;
                dsp.analysisWrote(count);
                drain();
            }
        }

        /** Writes interleaved 16-bit PCM of another sample rate, resampled to the encoder's rate so the length stays the same. */
        void writeResampled(byte[] pcm, int rate) throws IOException {
            int frames = pcm.length / 2 / channels;
            int out = (int) Math.round(frames * (double) this.rate / rate);
            int at = 0;
            while (at < out) {
                int count = Math.min(CHUNK, out - at);
                float[][] buffer = dsp.analysisBuffer(CHUNK);
                for (int i = 0, l = dsp.pcm_current; i < count; i++, l++) {
                    double source = (at + i) * (double) rate / this.rate;
                    int left = (int) source;
                    double fraction = source - left;
                    int right = Math.min(left + 1, frames - 1);
                    for (int channel = 0; channel < channels; channel++) {
                        double a = sample(pcm, frames, channels, left, channel), b = sample(pcm, frames, channels, right, channel);
                        buffer[channel][l] = (float) ((a * (1. - fraction) + b * fraction) / 32768.);
                    }
                }
                at += count;
                dsp.analysisWrote(count);
                drain();
            }
        }

        private static double sample(byte[] pcm, int frames, int channels, int index, int channel) {
            int at = index < 0 ? 0 : Math.min(index, frames - 1);
            int offset = (at * channels + channel) * 2;
            return (short) ((pcm[offset + 1] << 8) | (pcm[offset] & 0xff));
        }

        void finish() throws IOException {
            dsp.analysisWrote(0);
            drain();
            while (stream.flush(page)) write(page);
        }

        /** Runs the encoder over every block it has ready; the stream's end page ends it. */
        private void drain() throws IOException {
            while (!ended && block.analysisBlockOut()) {
                block.analysis(null);
                block.bitrateAddBlock();
                while (dsp.bitrateFlushPacket(packet)) {
                    stream.packetIn(packet);
                    while (stream.pageOut(page)) {
                        write(page);
                        if (page.eos()) ended = true;
                    }
                }
            }
        }

        private void write(Page page) throws IOException {
            output.write(page.header_base, page.header, page.header_len);
            output.write(page.body_base, page.body, page.body_len);
        }
    }
}
