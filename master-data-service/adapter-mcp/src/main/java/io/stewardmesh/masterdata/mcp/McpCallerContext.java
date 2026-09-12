package io.stewardmesh.masterdata.mcp;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Reads caller identity and grants exclusively from the authenticated server-side context. */
final class McpCallerContext {

    Caller requireScope(String scope) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException(
                    "an authenticated MCP caller is required");
        }
        String authority = "SCOPE_" + scope;
        boolean granted = authentication.getAuthorities().stream()
                .anyMatch(candidate -> candidate.getAuthority().equals(authority));
        if (!granted) {
            throw new AccessDeniedException("MCP caller lacks required scope " + scope);
        }
        return new Caller(authentication.getName());
    }

    record Caller(String subject) {}
}
