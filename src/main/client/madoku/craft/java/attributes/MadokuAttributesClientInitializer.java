package madoku.craft.java.attributes;

import net.fabricmc.api.ClientModInitializer;

/** Fabric client entrypoint for the standalone Attributes jar. */
public final class MadokuAttributesClientInitializer implements ClientModInitializer {
	@Override public void onInitializeClient() { MadokuAttributesClient.initialize(); }
}
