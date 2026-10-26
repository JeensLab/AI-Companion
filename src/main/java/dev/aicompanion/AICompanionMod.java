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
    public static final DeferredHolder<MenuType<?>, MenuType<CompanionMenu>> COMPANION_MENU = MENUS.register(
            "companion_inventory", () -> IMenuTypeExtension.create(CompanionMenu::fromNetwork));
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> COMPANION = ENTITY_TYPES.register(
            "companion", () -> EntityType.Builder.of(CompanionEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F).clientTrackingRange(10).build("aicompanion:companion"));

    public AICompanionMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, OpenRouterConfig.SPEC);
        ENTITY_TYPES.register(modEventBus);
        MENUS.register(modEventBus);
        modEventBus.addListener(CompanionEntity::createAttributes);
        NeoForge.EVENT_BUS.addListener(CompanionEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(CompanionEvents::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(CompanionEvents::onLivingDrops);
        NeoForge.EVENT_BUS.addListener(dev.aicompanion.entity.CompanionGoals::pickedUp);
        NeoForge.EVENT_BUS.addListener(CompanionEvents::registerCommands);
        NeoForge.EVENT_BUS.addListener(dev.aicompanion.entity.CompanionChunks::tick);
        NeoForge.EVENT_BUS.addListener(dev.aicompanion.client.CompanionServerPlanPort::tick);
        NeoForge.EVENT_BUS.addListener(CompanionEvents::tick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent event) -> {
            dev.aicompanion.entity.CompanionChunks.stop();
            dev.aicompanion.client.CompanionServerPlanPort.stop(event.getServer());
            CompanionDiagnostics.event("", "server_stopping");
            CompanionDiagnostics.flush();
        });
        LOGGER.info("AI Companion loaded for Minecraft 1.21.1 / NeoForge.");
    }
}
