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
    public static final ModConfigSpec.BooleanValue JOKES = BUILDER
            .comment("Occasional short local jokes in quiet moments, without a model request.")
            .define("jokes", true);
    public static final ModConfigSpec.IntValue JOKE_INTERVAL_MINUTES = BUILDER
            .comment("Minimum minutes between jokes; a random delay up to the same duration is added. Each joke repeats no sooner than an hour.")
            .defineInRange("jokeIntervalMinutes", 5, 1, 60);
    public static final ModConfigSpec.BooleanValue SHORT_MURMURS = BUILDER
            .comment("Keep his own idle remarks short (at most 15 words, no digits) and, when you are out of earshot, show them in chat without voice. Off: long remarks are voiced only within earshot.")
            .translation("config.aicompanion.short_murmurs")
            .define("shortMurmurs", true);
    public static final ModConfigSpec.IntValue MURMUR_INTERVAL_PERCENT = BUILDER
            .comment("How long he waits between remarks of his own, in percent of the usual pause (100 = as designed, 200 = twice as rare).")
            .translation("config.aicompanion.murmur_interval_percent")
            .defineInRange("murmurIntervalPercent", 100, 25, 400);
    public static final ModConfigSpec.BooleanValue COMPANION_MARKER = BUILDER
            .comment("Outline the companion through walls when he is out of sight, and mark him on the screen when he is too far to be seen (toggled with the key G).")
            .define("companionMarker", true);
    public static final ModConfigSpec.BooleanValue MOOD_PANEL = BUILDER
            .comment("Show the companion's mood, bond and character as a thin panel on the right of the screen (toggled with the key N).")
            .define("moodPanel", true);
    public static final ModConfigSpec.BooleanValue DAMAGE_NUMBERS = BUILDER
            .comment("Show above the companion, to his owner, the health he lost or regained and «блок» when his shield stopped a hit.")
            .define("damageNumbers", true);
    public static final ModConfigSpec.BooleanValue CHUNK_LOADING = BUILDER.define("companionChunkLoading", true);
    public static final ModConfigSpec.BooleanValue DIAGNOSTICS = BUILDER
            .comment("Write local diagnostic events, dialogue and game state to logs/aicompanion. May include private conversations and coordinates.")
            .define("diagnosticLogging", true);
    public static final ModConfigSpec.IntValue VOICE_DISTANCE = BUILDER.defineInRange("voiceDistance", 32, 8, 64);
    public static final ModConfigSpec.BooleanValue WINDOWS_VOICE = BUILDER
            .comment("Without an Inworld key, speak with a Russian voice installed in Windows (nothing is sent anywhere).")
            .define("windowsVoice", true);

    public static final ModConfigSpec.BooleanValue MACOS_VOICE = BUILDER
            .comment("Without an Inworld key, speak using an installed Russian macOS voice. No network or voice downloads.")
            .define("macosVoice", true);

    public static final ModConfigSpec.ConfigValue<String> API_KEY = BUILDER
            .comment("Your provider's API key: OpenRouter or direct OpenAI; usually not needed for a local server. Stored in the local Minecraft config folder.")
            .translation("config.aicompanion.api_key")
            .define("apiKey", "");

    public static final ModConfigSpec.ConfigValue<String> MODEL = BUILDER
            .comment("Model ID: openrouter/auto for OpenRouter; gpt-6-luna or gpt-6.1-terra for direct OpenAI; a loaded model name for a local server.")
            .translation("config.aicompanion.model")
            .define("model", "openrouter/auto");

    public static final ModConfigSpec.ConfigValue<String> API_URL = BUILDER
            .comment("Empty means OpenRouter. https://api.openai.com/v1 uses Responses API directly; compatible/local servers keep Chat Completions, e.g. http://localhost:11434/v1 for Ollama or http://localhost:1234/v1 for LM Studio.")
            .translation("config.aicompanion.api_url")
            .define("apiUrl", "");

    /** Off by default: on a full prompt the model spent 2263 hidden reasoning tokens and 100 s before the plan. */
    public static final ModConfigSpec.BooleanValue THINKING = BUILDER
            .comment("Let the model reason before answering. Off: the same prompt is answered in seconds instead of minutes. On: the server's own default — slower, and sometimes a more careful choice of materials.")
            .translation("config.aicompanion.model_thinking")
            .define("thinking", false);

    /** Empty means «не задавать»: the server's own sampling, unchanged. The mod imposes no range: models differ. */
    public static final ModConfigSpec.ConfigValue<String> TEMPERATURE = BUILDER
            .comment("Sampling temperature, sent as typed (dot or comma, negative too); the acceptable range depends on your model, and a value it rejects comes back as its own error. Empty keeps the model server's own default.")
            .translation("config.aicompanion.temperature")
            .define("temperature", "");

    /** Empty means «не задавать»: the server's own sampling, unchanged. The mod imposes no range: models differ. */
    public static final ModConfigSpec.ConfigValue<String> TOP_P = BUILDER
            .comment("Nucleus sampling top_p, sent as typed (dot or comma, negative too); the acceptable range depends on your model, and a value it rejects comes back as its own error. Empty keeps the model server's own default.")
            .translation("config.aicompanion.top_p")
            .define("topP", "");

    public static final ModConfigSpec.ConfigValue<String> INWORLD_API_KEY = BUILDER
            .comment("Inworld Basic credential copied from the Inworld portal. Leave blank to use an installed local Russian voice on Windows (windowsVoice) or macOS (macosVoice).")
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
    /** Whether he also stores into and takes from barrels, shulker boxes, furnaces, hoppers and dispensers. On unless the player turns it off. */
    public static final ModConfigSpec.BooleanValue OTHER_CONTAINERS = BUILDER
            .comment("Store into and take from barrels, shulker boxes, furnaces, hoppers and dispensers when asked by name, and sort barrels with the chests. Off: chests only.")
            .translation("config.aicompanion.other_containers")
            .define("otherContainers", true);
    /** Whether an urgent order («иди ко мне», «стой», «защищай») puts a dig aside for later instead of ending it. On unless the player turns it off. */
    public static final ModConfigSpec.BooleanValue URGENT_ORDER = BUILDER
            .comment("An urgent order (come, stay, guard) interrupts the dig and puts the rest aside; he digs it once he is free. Off: the order ends the dig.")
            .translation("config.aicompanion.urgent_order")
            .define("urgentOrder", true);
    /** Whether he gets into the player's boat with a seat free and out of it with the player. On unless the player turns it off. */
    public static final ModConfigSpec.BooleanValue BOAT_RIDE = BUILDER
            .comment("Get into the player's boat or raft when a seat is free, and out of it with the player, onto land beside it; never stay in a boat without the player. Off: he swims after the boat.")
            .translation("config.aicompanion.boat_ride")
            .define("boatRide", true);
    /** Whether a hurtful fall the water bucket does not catch is caught in a boat from his bag. On unless the player turns it off. */
    public static final ModConfigSpec.BooleanValue BOAT_CLUTCH = BUILDER
            .comment("Catch a hurtful fall the water bucket cannot (the Nether, no bucket) in a boat from his bag, then break it and take it back. Off: he takes the fall.")
            .translation("config.aicompanion.boat_clutch")
            .define("boatClutch", true);
    /** Whether a tree he felled gets a sapling of its wood back on the stump from his bag. On unless the player turns it off. */
    public static final ModConfigSpec.BooleanValue REPLANT_SAPLINGS = BUILDER
            .comment("Put a sapling of the same wood from his bag on the stump of a tree he felled (a dark oak only from four). Off: the stumps stay bare.")
            .translation("config.aicompanion.replant_saplings")
            .define("replantSaplings", true);

    public static final ModConfigSpec.BooleanValue LONG_TERM_MEMORY = BUILDER
            .comment("Let the companion remember facts, promises and recent talk between sessions, adapt his character and raise topics of his own. Stored in the world save.")
            .translation("config.aicompanion.long_term_memory")
            .define("longTermMemory", true);

    /** Whether «подбери» takes what lies on the ground around him, and how far the scan goes. On unless the player turns it off. */
    public static final ModConfigSpec.BooleanValue PICK_UP_NEARBY = BUILDER
            .comment("«Подбери <X>» takes what of it lies on the ground around the companion instead of starting a hunt or a dig. Off: the usual hunt or dig starts at once.")
            .translation("config.aicompanion.pick_up_nearby")
            .define("pickUpNearby", true);
    public static final ModConfigSpec.IntValue PICK_UP_RADIUS = BUILDER
            .comment("How far around the companion the ground is scanned for «подбери», in blocks.")
            .translation("config.aicompanion.pick_up_radius")
            .defineInRange("pickUpRadius", 16, 4, 32);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private OpenRouterConfig() {
    }
}
