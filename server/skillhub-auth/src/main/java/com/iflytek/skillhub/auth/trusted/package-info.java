/**
 * Trusted header authentication module.
 *
 * <p>This module provides authentication via HTTP headers set by a trusted reverse proxy
 * (e.g., Traefik ForwardAuth). When enabled, user identity is extracted from headers
 * and users are auto-provisioned on first access.
 */
package com.iflytek.skillhub.auth.trusted;
