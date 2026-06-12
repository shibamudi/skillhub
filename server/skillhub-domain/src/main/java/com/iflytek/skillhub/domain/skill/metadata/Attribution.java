package com.iflytek.skillhub.domain.skill.metadata;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;

import java.net.MalformedURLException;
import java.net.URL;

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

    public Attribution withValidatedSourceUrl() {
        if (isBlank(sourceUrl)) {
            return this;
        }
        try {
            new URL(sourceUrl);
            String lower = sourceUrl.toLowerCase();
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                throw new DomainBadRequestException("error.skill.publish.sourceUrl.invalid",
                        "sourceUrl must use http:// or https:// scheme");
            }
        } catch (MalformedURLException e) {
            throw new DomainBadRequestException("error.skill.publish.sourceUrl.invalid", e.getMessage());
        }
        return this;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
