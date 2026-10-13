package madoku.craft.java.attributes;

/** Public client contract for synchronized Attributes state. */
public final class AttributesClientAPIManager {
	@FunctionalInterface
	public interface HungerListener {
		void onHungerUpdated(int current, int max);
	}

	private AttributesClientAPIManager() { }

	public static void initialize() {
		MadokuAttributesClient.initialize();
	}

	public static void addHungerListener(HungerListener listener) {
		if (listener == null) return;
		MadokuAttributesClient.addHungerListener(listener::onHungerUpdated);
	}
}
