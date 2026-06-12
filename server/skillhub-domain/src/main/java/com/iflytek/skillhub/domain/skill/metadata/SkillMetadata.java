package com.iflytek.skillhub.domain.skill.metadata;

import java.util.Map;

public record SkillMetadata(
    String name,
    String description,
    String version,
    String body,
    Map<String, Object> frontmatter,
    Attribution attribution
) {

    public SkillMetadata withAttribution(Attribution override) {
        if (override == null || override.isEmpty()) {
            return this;
        }
        Attribution merged = new Attribution(
            nonBlankOrDefault(override.author(), attribution.author()),
            nonBlankOrDefault(override.sourcePlatform(), attribution.sourcePlatform()),
            nonBlankOrDefault(override.sourceUrl(), attribution.sourceUrl())
        );
        return new SkillMetadata(name, description, version, body, frontmatter, merged);
    }

    private static String nonBlankOrDefault(String override, String fallback) {
        return (override != null && !override.isBlank()) ? override : fallback;
    }
}
