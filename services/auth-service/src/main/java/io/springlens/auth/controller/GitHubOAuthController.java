package io.springlens.auth.controller;

import io.springlens.auth.entity.User;
import io.springlens.auth.entity.Workspace;
import io.springlens.auth.entity.WorkspaceMember;
import io.springlens.auth.repository.UserRepository;
import io.springlens.auth.repository.WorkspaceMemberRepository;
import io.springlens.auth.repository.WorkspaceRepository;
import io.springlens.auth.service.JwtService;
import io.springlens.auth.service.AuditLogService;
import io.springlens.shared.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * GitHub OAuth2 callback handler.
 * Exchanges code for token, fetches GitHub user, creates/links account.
 *
 * ✅ HARDENED: OAuth state parameter validation prevents CSRF attacks
 */
@RestController
@RequestMapping("/v1/auth")
public class GitHubOAuthController {

    private static final Logger log = LoggerFactory.getLogger(GitHubOAuthController.class);

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;

    @Value("${springlens.github.client-id}")
    private String clientId;

    @Value("${springlens.github.client-secret}")
    private String clientSecret;

    private final RestClient githubClient;

    public GitHubOAuthController(UserRepository userRepository,
                                   WorkspaceRepository workspaceRepository,
                                   WorkspaceMemberRepository memberRepository,
                                   JwtService jwtService,
                                   AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.jwtService = jwtService;
        this.auditLogService = auditLogService;
        this.githubClient = RestClient.builder()
                .baseUrl("https://github.com")
                .build();
    }

    /**
     * Initiates GitHub OAuth flow by redirecting to GitHub authorization endpoint.
     * ✅ SECURITY: Generates and stores a random state parameter to prevent CSRF attacks.
     * The state is stored in the session and validated in the callback.
     */
    @GetMapping("/github/login")
    public RedirectView initiateGitHubLogin(HttpSession session) {
        log.info("Initiating GitHub OAuth flow");

        // ✅ FIXED: Generate random state and store in session (CSRF protection)
        String state = UUID.randomUUID().toString();
        session.setAttribute("oauth_state", state);
        session.setMaxInactiveInterval(600); // 10 minutes TTL

        try {
            String redirectUri = URLEncoder.encode("https://api.springlens.io/v1/auth/github/callback", StandardCharsets.UTF_8);
            String authorizationUrl = "https://github.com/login/oauth/authorize?" +
                    "client_id=" + clientId +
                    "&redirect_uri=" + redirectUri +
                    "&state=" + state +
                    "&scope=user:email";

            return new RedirectView(authorizationUrl);
        } catch (Exception ex) {
            log.error("Failed to initiate GitHub OAuth", ex);
            return new RedirectView("https://springlens.io/auth-error");
        }
    }

    @GetMapping("/github/callback")
    @Transactional
    public ResponseEntity<Object> githubCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            HttpSession session) {

        log.info("GitHub OAuth callback received code=***");

        try {
            // ✅ FIXED: Validate OAuth state parameter to prevent CSRF attacks
            String sessionState = (String) session.getAttribute("oauth_state");
            if (sessionState == null || !sessionState.equals(state)) {
                log.warn("OAuth state parameter mismatch: potential CSRF attack. Session state={}, received state={}",
                        sessionState != null ? "present" : "null", state != null ? "present" : "null");

                // ✅ SECURITY: Audit log CSRF attack attempt
                auditLogService.logFailure(null, null, "LOGIN", "USER", null,
                        "CSRF attack attempt: state parameter mismatch");

                return ResponseEntity.badRequest()
                        .body(ErrorResponse.of("CSRF_FAILED", "Invalid OAuth state parameter", null));
            }

            // ✅ Security: Clear state from session (one-time use only)
            session.removeAttribute("oauth_state");
            // Exchange code for access token
            var tokenResponse = RestClient.builder()
                    .baseUrl("https://github.com")
                    .defaultHeader("Accept", "application/json")
                    .build()
                    .post()
                    .uri("/login/oauth/access_token")
                    .body(Map.of(
                            "client_id", clientId,
                            "client_secret", clientSecret,
                            "code", code))
                    .retrieve()
                    .body(Map.class);

            if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
                // ✅ SECURITY: Audit log OAuth token exchange failure
                auditLogService.logFailure(null, null, "LOGIN", "USER", null,
                        "GitHub token exchange failed");

                return ResponseEntity.badRequest()
                        .body(ErrorResponse.of("OAUTH_FAILED", "Failed to obtain GitHub access token", null));
            }

            String githubToken = (String) tokenResponse.get("access_token");

            // Fetch GitHub user profile
            var githubUser = RestClient.builder()
                    .baseUrl("https://api.github.com")
                    .defaultHeader("Authorization", "Bearer " + githubToken)
                    .defaultHeader("Accept", "application/vnd.github+json")
                    .build()
                    .get()
                    .uri("/user")
                    .retrieve()
                    .body(Map.class);

            if (githubUser == null) {
                // ✅ SECURITY: Audit log failed user fetch
                auditLogService.logFailure(null, null, "LOGIN", "USER", null,
                        "Failed to fetch GitHub user profile");

                return ResponseEntity.badRequest()
                        .body(ErrorResponse.of("OAUTH_FAILED", "Failed to fetch GitHub user", null));
            }

            String githubId = String.valueOf(githubUser.get("id"));
            String email = (String) githubUser.get("email");
            String displayName = githubUser.get("name") != null
                    ? (String) githubUser.get("name")
                    : (String) githubUser.get("login");
            String avatarUrl = (String) githubUser.get("avatar_url");

            // Find or create user
            User user = userRepository.findByGithubId(githubId)
                    .or(() -> email != null ? userRepository.findByEmail(email) : java.util.Optional.empty())
                    .orElseGet(() -> {
                        var newUser = User.create(
                                email != null ? email : githubId + "@github.noreply.com",
                                displayName, githubId, avatarUrl);
                        return userRepository.save(newUser);
                    });

            // Link GitHub if not already linked
            if (user.getGithubId() == null) {
                user.setGithubId(githubId);
                user.setAvatarUrl(avatarUrl);
                user.setUpdatedAt(java.time.Instant.now());
                userRepository.save(user);
            }

            // Find or create workspace
            var memberOpt = memberRepository.findAll().stream()
                    .filter(m -> m.getUserId().equals(user.getId()) && m.getDeletedAt() == null)
                    .findFirst();

            UUID workspaceId;
            String role;

            if (memberOpt.isPresent()) {
                workspaceId = memberOpt.get().getWorkspaceId();
                role = memberOpt.get().getRole();
            } else {
                // Create default workspace on first login
                String slug = displayName.toLowerCase().replaceAll("[^a-z0-9]", "-")
                        + "-" + UUID.randomUUID().toString().substring(0, 6);
                var workspace = Workspace.create(displayName + "'s Workspace", slug);
                workspaceRepository.save(workspace);
                var member = WorkspaceMember.create(workspace.getId(), user.getId(), "admin");
                memberRepository.save(member);
                workspaceId = workspace.getId();
                role = "admin";
            }

            String accessToken = jwtService.issueAccessToken(user.getId(), workspaceId, user.getEmail(), role);
            String refreshToken = jwtService.issueRefreshToken(user.getId(), workspaceId);

            // ✅ SECURITY: Audit log successful authentication
            auditLogService.logSuccess(workspaceId, user.getId(), "LOGIN", "USER", user.getId(),
                    String.format("GitHub OAuth successful: email=%s", user.getEmail()));

            log.info("GitHub OAuth success userId={} workspaceId={}", user.getId(), workspaceId);

            return ResponseEntity.ok(Map.of(
                    "access_token", accessToken,
                    "refresh_token", refreshToken,
                    "token_type", "Bearer",
                    "expires_in", 900,
                    "user", Map.of(
                            "id", user.getId().toString(),
                            "email", user.getEmail(),
                            "display_name", user.getDisplayName(),
                            "avatar_url", user.getAvatarUrl() != null ? user.getAvatarUrl() : "")));

        } catch (Exception ex) {
            log.error("GitHub OAuth callback failed", ex);
            return ResponseEntity.status(500)
                    .body(ErrorResponse.of("OAUTH_ERROR", "Authentication failed", null));
        }
    }
}
