package madoku.craft.java.attributes;

import madoku.craft.java.core.module.MadokuClientRuntime;
import madoku.craft.java.core.module.MadokuModule;
import madoku.craft.java.core.module.MadokuModuleIds;
import net.fabricmc.api.ClientModInitializer;

/** Fabric client entrypoint for the standalone Attributes jar. */
public final class MadokuAttributesClientInitializer implements ClientModInitializer, MadokuModule {
	@Override public net.minecraft.resources.Identifier id() { return MadokuModuleIds.ATTRIBUTES; }
	@Override public void onInitializeClient() { MadokuClientRuntime.initialize(this); }
	@Override public void initialize() { MadokuAttributesClient.initialize(); }
}
