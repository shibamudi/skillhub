package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.metadata.Attribution;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PublishResponse;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
import com.iflytek.skillhub.ratelimit.RateLimit;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * Upload endpoints for skill packages.
 *
 * <p>The controller is responsible for archive extraction and request shaping,
 * while the domain service owns all publication validation and state changes.
 */
@RestController
@RequestMapping({"/api/v1/skills", "/api/web/skills"})
@Validated
public class SkillPublishController extends BaseApiController {

    private final SkillPublishService skillPublishService;
    private final SkillPackageArchiveExtractor skillPackageArchiveExtractor;
    private final SkillHubMetrics skillHubMetrics;

    public SkillPublishController(SkillPublishService skillPublishService,
                                  SkillPackageArchiveExtractor skillPackageArchiveExtractor,
                                  ApiResponseFactory responseFactory,
                                  SkillHubMetrics skillHubMetrics) {
        super(responseFactory);
        this.skillPublishService = skillPublishService;
        this.skillPackageArchiveExtractor = skillPackageArchiveExtractor;
        this.skillHubMetrics = skillHubMetrics;
    }

    /**
     * Publishes an uploaded package into the target namespace after archive
     * extraction and visibility parsing.
     */
    @PostMapping("/{namespace}/publish")
    @RateLimit(category = "publish", authenticated = 10, anonymous = 0)
    public ApiResponse<PublishResponse> publish(
            @PathVariable String namespace,
            @RequestParam("file") MultipartFile file,
            @RequestParam("visibility") String visibility,
            @RequestParam(value = "confirmWarnings", defaultValue = "false") boolean confirmWarnings,
            @RequestParam(value = "authorName", required = false) @Size(max = 256) String authorName,
            @RequestParam(value = "sourcePlatform", required = false) @Size(max = 128) String sourcePlatform,
            @RequestParam(value = "sourceUrl", required = false) @Size(max = 512) String sourceUrl,
            @AuthenticationPrincipal PlatformPrincipal principal) throws IOException {

        SkillVisibility skillVisibility = SkillVisibility.valueOf(visibility.toUpperCase());

        // Validate sourceUrl is actually a URL to prevent XSS via javascript: or data: schemes
        if (sourceUrl != null && !sourceUrl.isBlank()) {
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
        }

        Attribution attribution = new Attribution(authorName, sourcePlatform, sourceUrl);

        List<PackageEntry> entries;
        List<String> extractionWarnings;
        try {
            SkillPackageArchiveExtractor.ExtractionResult extractionResult =
                    skillPackageArchiveExtractor.extractWithWarnings(file);
            entries = extractionResult.entries();
            extractionWarnings = extractionResult.warnings();
        } catch (IllegalArgumentException e) {
            throw new DomainBadRequestException("error.skill.publish.package.invalid", e.getMessage());
        }

        if (!confirmWarnings && !extractionWarnings.isEmpty()) {
            throw new DomainBadRequestException(
                    "error.skill.publish.precheck.confirmRequired",
                    String.join("\n", extractionWarnings));
        }

        SkillPublishService.PublishResult publishResult = skillPublishService.publishFromEntries(
                namespace,
                entries,
                principal.userId(),
                skillVisibility,
                principal.platformRoles(),
                confirmWarnings,
                attribution
        );

        PublishResponse response = new PublishResponse(
                publishResult.skillId(),
                namespace,
                publishResult.slug(),
                publishResult.version().getVersion(),
                publishResult.version().getStatus().name(),
                publishResult.version().getFileCount(),
                publishResult.version().getTotalSize()
        );
        skillHubMetrics.incrementSkillPublish(namespace, publishResult.version().getStatus().name());

        return ok("response.success.published", response);
    }
}
