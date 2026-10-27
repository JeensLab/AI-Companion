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
            .comment("Outline the companion through walls when he is out of sight (toggled with the key G).")
            .define("companionMarker", true);
    public static final ModConfigSpec.BooleanValue CHUNK_LOADING = BUILDER.define("companionChunkLoading", true);
    public static final ModConfigSpec.BooleanValue DIAGNOSTICS = BUILDER
            .comment("Write local diagnostic events, dialogue and game state to logs/aicompanion. May include private conversations and coordinates.")
            .define("diagnosticLogging", true);
    public static final ModConfigSpec.IntValue VOICE_DISTANCE = BUILDER.defineInRange("voiceDistance", 32, 8, 64);

    public static final ModConfigSpec.ConfigValue<String> API_KEY = BUILDER
            .comment("Your OpenRouter API key. Stored in the local Minecraft config folder.")
            .translation("config.aicompanion.api_key")
            .define("apiKey", "");

    public static final ModConfigSpec.ConfigValue<String> MODEL = BUILDER
            .comment("OpenRouter model ID. Use openrouter/auto to let OpenRouter choose a model.")
            .translation("config.aicompanion.model")
            .define("model", "openrouter/auto");

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

    public static final ModConfigSpec SPEC = BUILDER.build();

    private OpenRouterConfig() {
    }
}
