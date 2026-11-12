package com.fergusb.plan;

import java.util.List;

/** The area between four arterial roads */
public record Superblock(int i, int j, Rect bounds, List<Road> streets, List<Lot> lots) {
}
