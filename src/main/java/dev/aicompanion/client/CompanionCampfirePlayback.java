package dev.aicompanion.client;

/**
 * What this client does with the tune the server names for the evening ({@link CompanionCampfireAudio}): the server syncs the
 * sound id and a start counter with his pose, and a counter that moved means the recording is started over — the same one asked
 * for again is restarted, not left sounding — while one that has played out is not started again while the server still names it.
 * An id this client's sound engine does not know (on a server, a recording that is in the server's folder and not in this
 * player's) cannot start: the tune sounding goes on, and nothing is claimed to have changed. The decision is pure and lives apart
 * from the client's sound engine, so a GameTest can drive it without a client.
 */
public final class CompanionCampfirePlayback {
    /** A recording is taken as played out this close to its length: the sound engine ends it within a tick or two of it. */
    public static final int END_SLACK = 2;

    /** What a client tick does with the tune the server names. */
    public enum Action {
        /** The sound of that tune is playing on: it keeps sounding, and its played ticks are counted. */
        KEEP_SOUNDING,
        /** The start counter moved: the recording is started over from its beginning. */
        START_OVER,
        /** The recording has played out by its length: not started again while the server still names it. */
        PLAYED_OUT,
        /** It played out before and the server still names it: nothing to do. */
        FINISHED,
        /** Another tune is named: the one sounding stops and the new one starts. */
        START,
        /** The tune named is not one this client's sound engine knows: the one sounding goes on, and a change is not claimed. */
        KEEP_OLD,
        /** No tune is named — he stopped, stood up or the world went: the sound stops. */
        STOP
    }

    private CompanionCampfirePlayback() { }

    /**
     * @param wanted   the sound id the server names, empty when no tune is named
     * @param start    the start counter the server synced (-1 when no tune is named)
     * @param started  the start counter this client has begun (-1 for none)
     * @param sameTune whether the sound this client holds is the wanted tune
     * @param active   whether that sound is still being played by the sound engine
     * @param sounded  ticks the recording has sounded
     * @param length   its length in ticks, -1 when it cannot be told
     * @param finished the sound id that just played out, empty for none
     */
    public static Action decide(String wanted, int start, int started, boolean sameTune, boolean active,
                                int sounded, int length, String finished) {
        return decide(wanted, start, started, sameTune, active, sounded, length, finished, true);
    }

    /**
     * The same decision, with whether this client's sound engine knows the tune named at all: a sound id is known only from a
     * resource pack, so a recording the server has and this client does not cannot start here.
     *
     * @param known whether the sound engine has the wanted tune; only {@link Action#START} and {@link Action#START_OVER} care
     */
    public static Action decide(String wanted, int start, int started, boolean sameTune, boolean active,
                                int sounded, int length, String finished, boolean known) {
        if (wanted.isEmpty()) return Action.STOP;
        boolean restarted = start != started;
        if (!restarted && sameTune && active) return Action.KEEP_SOUNDING;
        boolean playedOut = !restarted && sameTune && length > 0 && sounded + END_SLACK >= length;
        if (restarted) return known ? Action.START_OVER : Action.KEEP_OLD;
        if (playedOut) return Action.PLAYED_OUT;
        if (wanted.equals(finished)) return Action.FINISHED;
        return known ? Action.START : Action.KEEP_OLD;
    }
}
