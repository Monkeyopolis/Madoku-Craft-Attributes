package madoku.craft.attributes;

public final class MadokuHungerClientState {
	private static volatile int serverHungerCurrent = -1;
	private static volatile int serverHungerMax = -1;

	private MadokuHungerClientState() {
	}

	public static void setServerHunger(int current, int max) {
		serverHungerCurrent = Math.max(0, current);
		serverHungerMax = Math.max(1, max);
	}

	public static boolean hasServerHunger() {
		return serverHungerCurrent >= 0 && serverHungerMax > 0;
	}

	public static int getServerHungerCurrent() {
		return serverHungerCurrent;
	}

	public static int getServerHungerMax() {
		return serverHungerMax;
	}

	public static void reset() {
		serverHungerCurrent = -1;
		serverHungerMax = -1;
	}
}
