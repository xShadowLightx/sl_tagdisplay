package io.github.shadowlight.tagdisplay.model;

public enum TagType {
    HEADTAG("headtags", "headtag", "Headtag", 2.75),
    GANGTAG("gangtags", "gangtag", "Gangtag", 1.75);

    private final String configKey;
    private final String selectionKey;
    private final String displayName;
    private final double heightAboveFeet;

    TagType(String configKey, String selectionKey, String displayName, double heightAboveFeet) {
        this.configKey = configKey;
        this.selectionKey = selectionKey;
        this.displayName = displayName;
        this.heightAboveFeet = heightAboveFeet;
    }

    public String configKey() {
        return configKey;
    }

    public String selectionKey() {
        return selectionKey;
    }

    public String displayName() {
        return displayName;
    }

    public double heightAboveFeet() {
        return heightAboveFeet;
    }
}
