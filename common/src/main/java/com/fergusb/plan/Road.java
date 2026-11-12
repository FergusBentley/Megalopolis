package com.fergusb.plan;

public record Road(Rect rect, Kind kind) {
	public enum Kind { ARTERIAL, STREET }

	public boolean runsAlongZ() {
		return rect.width() <= rect.depth();
	}
}
