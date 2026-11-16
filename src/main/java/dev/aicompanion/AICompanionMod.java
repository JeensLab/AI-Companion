package dev.aicompanion;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.inventory.CompanionMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/** Main entry point for the AI Companion mod. */
@Mod(AICompanionMod.MOD_ID)
public final class AICompanionMod {
    public static final String MOD_ID = "aicompanion";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    /**
     * Oleg's own guitar for the evenings by the fire (entity/CompanionCampfire). Only the renderer draws it in his hands: it is in no
     * creative tab, no recipe and no loot, and is never put into his bag or handed out.
     */
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.Item> GUITAR = ITEMS.registerSimpleItem(
            "guitar", new net.minecraft.world.item.Item.Properties().stacksTo(1));
    public static final DeferredHolder<MenuType<?>, MenuType<CompanionMenu>> COMPANION_MENU = MENUS.register(
            "companion_inventory", () -> IMenuTypeExtension.create(CompanionMenu::fromNetwork));
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> COMPANION = ENTITY_TYPES.register(
            "companion", () -> EntityType.Builder.of(CompanionEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F).clientTrackingRange(10)
                    // Sits down into a boat or minecart as a player does, his legs inside (not perched on top of it).
                    .vehicleAttachment(net.minecraft.world.entity.player.Player.DEFAULT_VEHICLE_ATTACHMENT)
                    // His place goes to the client every tick, not every third: a fast fall is drawn where it is (CompanionEntity.lerpTo).
                    .updateInterval(1).build("aicompanion:companion"));
    /** The invisible point a player sits on after /sit (entity/CompanionSeat): low enough that he sits on the block itself. */
    public static final DeferredHolder<EntityType<?>, EntityType<dev.aicompanion.entity.CompanionSeat>> SEAT = ENTITY_TYPES.register(
            "seat", () -> EntityType.Builder.<dev.aicompanion.entity.CompanionSeat>of(dev.aicompanion.entity.CompanionSeat::new, MobCategory.MISC)
                    .sized(0.2F, 0.1F).noSummon().fireImmune().clientTrackingRange(10).build("aicompanion:seat"));

    public AICompanionMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, OpenRouterConfig.SPEC);
        ENTITY_TYPES.register(modEventBus);
        MENUS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(CompanionEntity::createAttributes);
        NeoForge.EVENT_BUS.addListener(CompanionEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(CompanionEvents::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(CompanionEvents::onLivingDrops);
        NeoForge.EVENT_BUS.addListener(CompanionEvents::onFarmlandTrample);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, CompanionEvents::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(dev.aicompanion.entity.CompanionGoals::pickedUp);
        // A player took a stack of a fall: the place's manifest loses it before any walk sets out for it.
        NeoForge.EVENT_BUS.addListener(dev.aicompanion.entity.CompanionDeathSpots::onPlayerPickup);
        NeoForge.EVENT_BUS.addListener(CompanionEvents::registerCommands);
        NeoForge.EVENT_BUS.addListener(dev.aicompanion.entity.CompanionSeat::registerCommand);
        NeoForge.EVENT_BUS.addListener(dev.aicompanion.entity.CompanionChunks::tick);
        NeoForge.EVENT_BUS.addListener(dev.aicompanion.client.CompanionServerPlanPort::tick);
        NeoForge.EVENT_BUS.addListener(dev.aicompanion.entity.CompanionAgenda::tick);
        NeoForge.EVENT_BUS.addListener(CompanionEvents::tick);
        NeoForge.EVENT_BUS.addListener(dev.aicompanion.entity.CompanionHappeningEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(dev.aicompanion.entity.CompanionHappeningEvents::onRespawn);
        NeoForge.EVENT_BUS.addListener(dev.aicompanion.entity.CompanionHappeningEvents::onAdvancement);
        NeoForge.EVENT_BUS.addListener(dev.aicompanion.entity.CompanionHappeningEvents::onDimensionChange);
        NeoForge.EVENT_BUS.addListener(dev.aicompanion.entity.CompanionHappeningEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(dev.aicompanion.entity.CompanionHappeningEvents::onLogout);
        NeoForge.EVENT_BUS.addListener(dev.aicompanion.entity.CompanionHappeningEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(dev.aicompanion.entity.CompanionHappeningEvents::onServerStopped);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent event) -> {
            dev.aicompanion.entity.CompanionChunks.stop();
            dev.aicompanion.client.CompanionServerPlanPort.stop(event.getServer());
            dev.aicompanion.entity.CompanionAgenda.stop();
            CompanionDiagnostics.event("", "server_stopping");
            CompanionDiagnostics.flush();
        });
        LOGGER.info("AI Companion loaded for Minecraft 1.21.1 / NeoForge.");
    }
}
