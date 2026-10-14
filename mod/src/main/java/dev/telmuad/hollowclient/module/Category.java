package dev.telmuad.hollowclient.module;

public enum Category {
	HUD("HUD"),
	VISUAL("Visual"),
	QOL("Quality of Life"),
	STYLES("Styles");

	private final String label;

	Category(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
