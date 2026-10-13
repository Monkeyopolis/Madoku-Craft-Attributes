package madoku.craft.java.attributes;

/** Public aggregate contract for the Madoku Attributes subsystem. */
public final class AttributesAPIManager {
	private AttributesAPIManager() { }

	public static boolean isEnabled() {
		return MadokuAttributesManager.isEnabled();
	}
}
