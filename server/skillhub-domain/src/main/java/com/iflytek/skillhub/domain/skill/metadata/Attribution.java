package com.iflytek.skillhub.domain.skill.metadata;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record Attribution(
    String author,
    String sourcePlatform,
    String sourceUrl
) {

    public static final Attribution EMPTY = new Attribution(null, null, null);

    @JsonIgnore
    public boolean isEmpty() {
        return isBlank(author) && isBlank(sourcePlatform) && isBlank(sourceUrl);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
