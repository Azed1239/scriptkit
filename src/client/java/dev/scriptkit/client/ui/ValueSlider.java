package dev.scriptkit.client.ui;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** A slider over a real range with a label, e.g. "Damage: x1.60". */
public class ValueSlider extends AbstractSliderButton {
	private final String label;
	private final double min;
	private final double max;
	private final double step;
	private final DoubleFunction<String> format;
	private final DoubleConsumer onChange;

	public ValueSlider(int x, int y, int width, int height, String label, double min, double max, double step,
		double current, DoubleFunction<String> format, DoubleConsumer onChange) {
		super(x, y, width, height, Component.empty(), (clamp(current, min, max) - min) / (max - min));
		this.label = label;
		this.min = min;
		this.max = max;
		this.step = step;
		this.format = format;
		this.onChange = onChange;
		updateMessage();
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	/** The real value, snapped to the step. */
	public double actual() {
		double raw = min + value * (max - min);
		return step <= 0 ? raw : Math.round(raw / step) * step;
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.literal(label + ": " + format.apply(actual())));
	}

	@Override
	protected void applyValue() {
		onChange.accept(actual());
	}
}
