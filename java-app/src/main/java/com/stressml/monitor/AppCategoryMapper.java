package com.stressml.monitor;

import com.stressml.model.AppCategory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

/** Maps a process/window name to a coarse category. The raw name is discarded after mapping. */
public class AppCategoryMapper {
    private final Properties map = new Properties();

    public AppCategoryMapper() {
        try (InputStream in = getClass().getResourceAsStream("/app_categories.properties")) {
            if (in != null) map.load(in);
        } catch (IOException ignored) { }
    }

    public AppCategory categorise(String rawName) {
        if (rawName == null) return AppCategory.OTHER;
        String n = rawName.toLowerCase(Locale.ROOT);
        for (String key : map.stringPropertyNames()) {
            if (n.contains(key)) {
                try { return AppCategory.valueOf(map.getProperty(key).trim().toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException e) { return AppCategory.OTHER; }
            }
        }
        return AppCategory.OTHER;
    }
}
