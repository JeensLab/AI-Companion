package dev.aicompanion;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-local settings for the OpenRouter connection and companion personality. */
public final class OpenRouterConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> COMPANION_NAME = BUILDER
            .comment("Companion name, used in speech and above the character.")
            .define("companionName", CompanionIdentity.DEFAULT_NAME);

    public static final ModConfigSpec.IntValue SEARCH_RADIUS = BUILDER
            .comment("Maximum independent resource-search and roaming distance, in loaded chunks.")
            .defineInRange("searchRadius", 64, 16, 128);
    public static final ModConfigSpec.BooleanValue AUTONOMOUS_SPEECH = BUILDER.define("autonomousSpeech", true);
    public static final ModConfigSpec.BooleanValue COMPANION_MARKER = BUILDER
            .comment("Outline the companion through walls when he is out of sight, and mark him on the screen when he is too far to be seen (toggled with the key G).")
            .define("companionMarker", true);
    public static final ModConfigSpec.BooleanValue MOOD_PANEL = BUILDER
            .comment("Show the companion's mood, bond and character as a thin panel on the right of the screen (toggled with the key N).")
            .define("moodPanel", true);
    public static final ModConfigSpec.BooleanValue CHUNK_LOADING = BUILDER.define("companionChunkLoading", true);
    public static final ModConfigSpec.BooleanValue DIAGNOSTICS = BUILDER
            .comment("Write local diagnostic events, dialogue and game state to logs/aicompanion. May include private conversations and coordinates.")
            .define("diagnosticLogging", true);
    public static final ModConfigSpec.IntValue VOICE_DISTANCE = BUILDER.defineInRange("voiceDistance", 32, 8, 64);

    public static final ModConfigSpec.ConfigValue<String> API_KEY = BUILDER
            .comment("Your API key: required for OpenRouter, usually not needed for a local server. Stored in the local Minecraft config folder.")
            .translation("config.aicompanion.api_key")
            .define("apiKey", "");

    public static final ModConfigSpec.ConfigValue<String> MODEL = BUILDER
            .comment("Model ID. With OpenRouter, openrouter/auto lets OpenRouter choose; with your own server, the name of a model it has loaded.")
            .translation("config.aicompanion.model")
            .define("model", "openrouter/auto");

    public static final ModConfigSpec.ConfigValue<String> API_URL = BUILDER
            .comment("OpenAI-compatible chat server. Empty means OpenRouter; for example http://localhost:11434/v1 for Ollama or http://localhost:1234/v1 for LM Studio. A local server needs no API key.")
            .translation("config.aicompanion.api_url")
            .define("apiUrl", "");

    public static final ModConfigSpec.ConfigValue<String> INWORLD_API_KEY = BUILDER
            .comment("Inworld Basic credential copied from the Inworld portal. Leave blank to disable voice output.")
            .translation("config.aicompanion.inworld_api_key")
            .define("inworldApiKey", "");

    public static final ModConfigSpec.ConfigValue<String> INWORLD_VOICE_ID = BUILDER
            .comment("Inworld voice ID used for the companion's speech.")
            .translation("config.aicompanion.inworld_voice_id")
            .define("inworldVoiceId", "Ashley");

    public static final ModConfigSpec.ConfigValue<String> PERSONALITY = BUILDER
            .comment("Additional personality preferences. Shared character directions also apply to existing saved settings.")
            .translation("config.aicompanion.personality")
            .define("personality", "Ты мой товарищ по выживанию в Minecraft: язвительный, разговорчивый и надёжный. Помни наш разговор, интересуйся происходящим, шути и матерись естественно, без постоянных комплиментов.");

    /** How many times he arrives at one place before letting it lie, and how many deaths going for it are too many. */
    public static final ModConfigSpec.IntValue DEATH_RECOVERY_ATTEMPTS = BUILDER
            .comment("Tries at one death place before he lets it lie (1-20).")
            .translation("config.aicompanion.death_recovery_attempts")
            .defineInRange("deathRecoveryAttempts", 5, 1, 20);
    public static final ModConfigSpec.IntValue DEATH_RECOVERY_DEATHS = BUILDER
            .comment("Deaths going for one death place, on the way or at it, after which he lets it lie (1-50).")
            .translation("config.aicompanion.death_recovery_deaths")
            .defineInRange("deathRecoveryDeaths", 10, 1, 50);

    /** Whether he goes back for his own things after a death. On unless the player turns it off. */
    public static final ModConfigSpec.BooleanValue DEATH_RECOVERY = BUILDER
            .comment("Go back for his own belongings after a death: the dearest place first, junk left alone.")
            .translation("config.aicompanion.death_recovery")
            .define("deathRecovery", true);

    /** Whether the guard order goes for the monster nearest to the player. On unless the player turns it off. */
    public static final ModConfigSpec.BooleanValue GUARD_NEAREST_TO_PLAYER = BUILDER
            .comment("On the guard order, go for the monster nearest to the player within fifty blocks of him. Off: only the monsters near the player or near the companion, the nearest to the companion first.")
            .translation("config.aicompanion.guard_nearest_to_player")
            .define("guardNearestToPlayer", true);

    public static final ModConfigSpec.BooleanValue LONG_TERM_MEMORY = BUILDER
            .comment("Let the companion remember facts, promises and recent talk between sessions, adapt his character and raise topics of his own. Stored in the world save.")
            .translation("config.aicompanion.long_term_memory")
            .define("longTermMemory", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private OpenRouterConfig() {
    }
}
