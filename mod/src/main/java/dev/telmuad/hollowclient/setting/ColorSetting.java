package dev.telmuad.hollowclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.telmuad.hollowclient.util.ColorUtil;

/** An ARGB color, stored in the config as "#AARRGGBB". */
public final class ColorSetting extends Setting<Integer> {
	private final boolean alpha;

	public ColorSetting(String id, String name, int defaultArgb) {
		this(id, name, defaultArgb, true);
	}

	public ColorSetting(String id, String name, int defaultArgb, boolean alpha) {
		super(id, name, alpha ? defaultArgb : defaultArgb | 0xFF000000);
		this.alpha = alpha;
	}

	/** Whether the color picker shows the alpha bar. Without it, alpha is always 255. */
	public boolean hasAlpha() {
		return alpha;
	}

	public int argb() {
		return get();
	}

	@Override
	protected Integer sanitize(Integer raw) {
		return alpha ? raw : (raw | 0xFF000000);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(ColorUtil.toHex(get()));
	}

	@Override
	public void fromJson(JsonElement json) {
		Integer parsed = null;
		if (json != null && json.isJsonPrimitive()) {
			JsonPrimitive p = json.getAsJsonPrimitive();
			if (p.isString()) parsed = ColorUtil.parseHex(p.getAsString());
			else if (p.isNumber()) parsed = (int) p.getAsLong();
		}
		if (parsed != null) set(parsed);
		else reset();
	}

	@Override
	public String display() {
		return ColorUtil.toHex(get());
	}
}
