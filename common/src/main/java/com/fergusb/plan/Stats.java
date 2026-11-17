package com.fergusb.plan;

import java.util.concurrent.atomic.AtomicLong;

public final class Stats {
	private Stats() {}


	public static final AtomicLong chunks = new AtomicLong();
	public static final AtomicLong firstChunkNanos = new AtomicLong();
	public static final AtomicLong lastChunkNanos = new AtomicLong();

	public static final AtomicLong placed = new AtomicLong();
	public static final AtomicLong placeNanos = new AtomicLong();
	public static final AtomicLong placeMaxNanos = new AtomicLong();

	public static final AtomicLong plans = new AtomicLong();
	public static final AtomicLong planNanos = new AtomicLong();
	public static final AtomicLong summaries = new AtomicLong();
	public static final AtomicLong summaryNanos = new AtomicLong();

	public static final AtomicLong noise = new AtomicLong();
	public static final AtomicLong noiseNanos = new AtomicLong();

	public static void chunk() {
		long now = System.nanoTime();
		firstChunkNanos.compareAndSet(0, now);
		lastChunkNanos.set(now);
		chunks.incrementAndGet();
	}

	public static void placed(long nanos) {
		placed.incrementAndGet();
		placeNanos.addAndGet(nanos);
		placeMaxNanos.accumulateAndGet(nanos, Math::max);
	}

	public static void reset() {
		for (AtomicLong a : new AtomicLong[] {chunks, firstChunkNanos, lastChunkNanos, placed, placeNanos, placeMaxNanos,
				plans, planNanos, summaries, summaryNanos, noise, noiseNanos}) {
			a.set(0);
		}
	}
}
