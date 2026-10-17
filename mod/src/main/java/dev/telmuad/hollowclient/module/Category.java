package dev.telmuad.hollowclient.module;

public enum Category {
	HUD("HUD"),
	INTERFACE("Interface"),
	VISUAL("Visual"),
	EFFECTS("Effects"),
	QOL("Quality of Life"),
	STYLES("Styles"),
	SKYBLOCK("SkyBlock"),
	CLIENT("Client");

	private final String label;

	Category(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
