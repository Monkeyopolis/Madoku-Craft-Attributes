package madoku.craft.java.attributes;

import madoku.craft.java.core.module.MadokuStandaloneModule;
import madoku.craft.java.core.module.MadokuStandaloneRuntime;
import net.fabricmc.api.ModInitializer;
import net.minecraft.server.MinecraftServer;

/** Fabric entrypoint for the standalone Attributes jar. */
public final class MadokuAttributesInitializer implements ModInitializer, MadokuStandaloneModule {
	@Override public void onInitialize() { MadokuStandaloneRuntime.initialize(this); }
	@Override public void initialize() { MadokuAttributesManager.initialize(); }
	@Override public void reset() { MadokuAttributesManager.reset(); }
	@Override public void loadPersistedData(MinecraftServer server) { MadokuAttributesManager.loadPersistedData(server); }
	@Override public void onServerStarted(MinecraftServer server) { MadokuAttributesManager.onServerStarted(server); }
	@Override public void onServerTick(MinecraftServer server) { MadokuAttributesManager.onServerTick(server); }
}
