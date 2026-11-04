package dev.aicompanion.gametest;

/**
 * Development only. The probe's count of climbs to the surface, a pure step function so that recorded depth sequences can test it.
 * Crossing the entrance is not a climb: only a way up from the depth of a real descent is, and the stairs near the surface are
 * walked up and down more than once without it.
 */
final class CompanionMineWorldClimbs {
    /** Deeper than this a stair is a descent worth the name; the probe's owner also hovers above him from here. */
    static final int HOVER_DEPTH = 15;
    /** At or above this depth below the entrance he is back at it. */
    static final int ENTRANCE_SLACK = 2;

    /** {@code deep}: since the last time at the entrance he has been down to the hover depth. */
    record State(int count, boolean deep) {
        static final State START = new State(0, false);

        /** A new body stands at the entrance: the descent of the old one is not his. */
        State afterDeath() { return new State(count, false); }
    }

    private CompanionMineWorldClimbs() { }

    /** The state after one tick at {@code depth} below the entrance; {@code counted}: the goal is in a state where a climb is held against it. */
    static State next(State state, int depth, boolean counted) {
        boolean back = depth <= ENTRANCE_SLACK;
        boolean climbed = state.deep() && back && counted;
        return new State(state.count() + (climbed ? 1 : 0), back ? false : state.deep() || depth >= HOVER_DEPTH);
    }
}
