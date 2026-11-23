package ru.example.privateblocks;

import java.util.List;

public record Dynamite(String id, double powerMult, boolean underwater, boolean obsidian,
                       Mode mode, int areaRadius, String name, List<String> lore) {
    public enum Mode { NORMAL, SPAWNER, BEDROCK }
}
