package com.stressml.monitor;

import com.stressml.model.AppCategory;

import java.util.EnumMap;
import java.util.Map;

/** Polls the foreground application every second and accumulates per-category time and switch counts. */
public class ApplicationMonitor {
    private final ActiveWindowProvider provider;
    private final AppCategoryMapper mapper;
    private AppCategory current = AppCategory.OTHER;
    private String lastRawName;
    private int switches;
    private final Map<AppCategory, Double> secondsByCategory = new EnumMap<>(AppCategory.class);

    public ApplicationMonitor(ActiveWindowProvider provider, AppCategoryMapper mapper) { this.provider = provider; this.mapper = mapper; }

    /** Called once per second by the scheduler. */
    public synchronized void tick() {
        String raw = provider.foregroundProcessName();
        if (raw != null && !raw.equals(lastRawName)) {
            if (lastRawName != null) switches++;
            lastRawName = raw;          // kept only in memory to detect switches; never persisted
            current = mapper.categorise(raw);
        }
        secondsByCategory.merge(current, 1.0, Double::sum);
    }

    public synchronized Snapshot drain() {
        AppCategory dominant = current;
        double best = -1, total = 0;
        for (Map.Entry<AppCategory, Double> e : secondsByCategory.entrySet()) {
            total += e.getValue();
            if (e.getValue() > best) { best = e.getValue(); dominant = e.getKey(); }
        }
        Snapshot s = new Snapshot(switches, dominant, new EnumMap<>(secondsByCategory), total);
        switches = 0; secondsByCategory.clear();
        return s;
    }

    public synchronized void reset() {
        switches = 0; secondsByCategory.clear(); lastRawName = null; current = AppCategory.OTHER;
    }

    public record Snapshot(int switches, AppCategory dominant, Map<AppCategory, Double> seconds, double totalSeconds) {}
}
