package io.stewardmesh.agent;

/** Supplies a short-lived bearer token without exposing credentials to the workflow. */
@FunctionalInterface
public interface AccessTokenProvider {

    String accessToken();
}
