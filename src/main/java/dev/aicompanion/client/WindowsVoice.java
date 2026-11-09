package dev.aicompanion.client;

import com.sun.jna.WString;
import com.sun.jna.platform.win32.COM.COMException;
import com.sun.jna.platform.win32.COM.COMLateBindingObject;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.Dispatch;
import com.sun.jna.platform.win32.COM.IDispatch;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.OaIdl;
import com.sun.jna.platform.win32.OleAuto;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.Variant.VARIANT;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import dev.aicompanion.client.WindowsVoiceChoice.Candidate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Oleg's line in the system voice of Windows (SAPI), as a WAV for the same player as Inworld's: from Oleg, by distance, with
 * the «Voice» volume and the mouth. Windows ships the voice with its Russian language; nothing is downloaded or sent anywhere.
 */
public final class WindowsVoice {
    /** Why there is no speech: {@code not_windows} or {@code no_russian_voice}. */
    public static final class Silent extends RuntimeException {
        private final String reason;
        public Silent(String reason) { super(reason, null, false, false); this.reason = reason; }
        public String reason() { return reason; }
    }

    // A COM object lives in the apartment of the thread that made it: one thread does all of the speaking, off the game.
    private static final ExecutorService THREAD = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "AI Companion Windows voice");
        thread.setDaemon(true);
        return thread;
    });

    private WindowsVoice() { }

    public static boolean windows() { return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"); }

    /** The installed voices, Russian or not; the choice among them is {@link WindowsVoiceChoice}. */
    public static CompletableFuture<List<Candidate>> voices() {
        if (!windows()) return CompletableFuture.failedFuture(new Silent("not_windows"));
        return CompletableFuture.supplyAsync(Sapi::voices, THREAD);
    }

    /** The WAV of the line; fails with {@link Silent} when Windows has no voice for it. */
    public static CompletableFuture<byte[]> synthesize(String text) {
        if (!windows()) return CompletableFuture.failedFuture(new Silent("not_windows"));
        return CompletableFuture.supplyAsync(() -> Sapi.speak(text), THREAD);
    }

    /** The reason of a {@link Silent} failure, or null for any other error. */
    public static String silentReason(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause instanceof Silent silent ? silent.reason() : null;
    }

    /** All of COM, so that nothing of it is loaded off Windows. */
    private static final class Sapi {
        // OneCore holds the newer voices (the male Russian Pavel); SAPI speaks them when the token category is read from there.
        private static final String ONECORE = "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Speech_OneCore\\Voices";
        private static final String DESKTOP = "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Speech\\Voices";
        private static final int CREATE_FOR_WRITE = 3;   // SpeechStreamFileMode.SSFMCreateForWrite
        private static final int MONO_22KHZ_16BIT = 22;  // SpeechAudioFormatType.SAFT22kHz16BitMono
        private static final int NOT_XML = 16;           // SpeechVoiceSpeakFlags.SVSFIsNotXML: the model's text is never markup
        private static boolean comReady;

        private record Token(Candidate candidate, Com object) { }

        static List<Candidate> voices() {
            List<Token> tokens = tokens();
            try { return tokens.stream().map(Token::candidate).toList(); }
            finally { tokens.forEach(token -> token.object().release()); }
        }

        static byte[] speak(String text) {
            List<Token> tokens = tokens();
            Path file = null;
            Com voice = null, stream = null;
            try {
                Candidate chosen = WindowsVoiceChoice.pick(tokens.stream().map(Token::candidate).toList())
                        .orElseThrow(() -> new Silent("no_russian_voice"));
                Token token = tokens.stream().filter(t -> t.candidate() == chosen).findFirst().orElseThrow();
                file = Files.createTempFile("aicompanion-voice-", ".wav");
                stream = new Com("SAPI.SpFileStream");
                Com format = new Com(stream.property("Format"));
                try { format.put("Type", new VARIANT(MONO_22KHZ_16BIT)); } finally { format.release(); }
                stream.call("Open", new VARIANT(file.toString()), new VARIANT(CREATE_FOR_WRITE), new VARIANT(false));
                voice = new Com("SAPI.SpVoice");
                voice.putRef("Voice", token.object());
                voice.putRef("AudioOutputStream", stream);
                voice.call("Speak", new VARIANT(text), new VARIANT(NOT_XML));
                stream.call("Close");
                return Files.readAllBytes(file);
            } catch (java.io.IOException error) {
                throw new CompletionException(error);
            } finally {
                if (voice != null) voice.release();
                if (stream != null) stream.release();
                tokens.forEach(t -> t.object().release());
                if (file != null) try { Files.deleteIfExists(file); } catch (java.io.IOException ignored) { /* a temp file */ }
            }
        }

        private static List<Token> tokens() {
            // The multithreaded apartment: a synchronous Speak in a single-threaded one waits for a message loop this thread has not.
            if (!comReady) { Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED); comReady = true; }
            List<Token> found = new ArrayList<>();
            try {
                for (String path : List.of(ONECORE, DESKTOP)) {
                    Com category = null, list = null;
                    try {
                        category = new Com("SAPI.SpObjectTokenCategory");
                        category.call("SetId", new VARIANT(path), new VARIANT(false));
                        list = new Com(category.result("EnumerateTokens"));
                        int count = list.count();
                        for (int i = 0; i < count; i++) {
                            Com token = new Com(list.result("Item", new VARIANT(i)));
                            try {
                                found.add(new Token(new Candidate(token.text("Id"), token.attribute("Language"), token.attribute("Gender"),
                                        path.equals(ONECORE)), token));
                            } catch (COMException unreadable) {
                                token.release(); // one voice that cannot be read is skipped, not the rest of its category
                            }
                        }
                    } catch (COMException noSuchCategory) {
                        // An older Windows has no OneCore voices: the desktop ones are still read.
                    } finally {
                        if (list != null) list.release();
                        if (category != null) category.release();
                    }
                }
                return found;
            } catch (RuntimeException error) {
                found.forEach(token -> token.object().release());
                throw error;
            }
        }
    }

    /** The automation calls SAPI needs, including the by-reference property sets JNA does not offer. */
    private static final class Com extends COMLateBindingObject {
        Com(String progId) { super(progId, false); }
        Com(IDispatch object) { super(object); }

        // A VARIANT made from a String holds a BSTR the caller frees: each call clears its arguments, or every line leaks them.
        void call(String method, VARIANT... args) { try { invokeNoReply(method, args); } finally { clear(args); } }
        /** The object a call returns: its reference passes to the Com made of it, which releases it. */
        IDispatch result(String method, VARIANT... args) { try { return (IDispatch) invoke(method, args).getValue(); } finally { clear(args); } }
        IDispatch property(String name) { return getAutomationProperty(name); }
        String text(String name) { return getStringProperty(name); }
        int count() { return getIntProperty("Count"); }
        /** A voice token's attribute, or "" when the voice does not state it (SAPI answers SPERR_NOT_FOUND then). */
        String attribute(String name) {
            VARIANT key = new VARIANT(name);
            try {
                VARIANT value = invoke("GetAttribute", key);
                try { String text = value.stringValue(); return text == null ? "" : text; }
                finally { OleAuto.INSTANCE.VariantClear(value); }
            } catch (COMException notStated) {
                return "";
            } finally {
                OleAuto.INSTANCE.VariantClear(key);
            }
        }
        void put(String name, VARIANT value) { setProperty(name, value); }
        private static void clear(VARIANT... args) { for (VARIANT arg : args) OleAuto.INSTANCE.VariantClear(arg); }

        /** {@code object.name = value} for an object property (propputref): the voice and the output stream. */
        void putRef(String name, Com value) {
            OaIdl.DISPIDByReference id = new OaIdl.DISPIDByReference();
            COMUtils.checkRC(getIDispatch().GetIDsOfNames(new Guid.REFIID(Guid.IID_NULL), new WString[]{new WString(name)}, 1, LOCALE_USER_DEFAULT, id));
            OleAuto.DISPPARAMS.ByReference params = new OleAuto.DISPPARAMS.ByReference();
            params.setArgs(new VARIANT[]{new VARIANT((Dispatch) value.getIDispatch())});
            params.setRgdispidNamedArgs(new OaIdl.DISPID[]{OaIdl.DISPID_PROPERTYPUT});
            params.write();
            OaIdl.EXCEPINFO.ByReference failure = new OaIdl.EXCEPINFO.ByReference();
            IntByReference argument = new IntByReference();
            COMUtils.checkRC(getIDispatch().Invoke(id.getValue(), new Guid.REFIID(Guid.IID_NULL), LOCALE_USER_DEFAULT,
                    new WinDef.WORD(OleAuto.DISPATCH_PROPERTYPUTREF), params, null, failure, argument), failure, argument);
        }
    }
}
