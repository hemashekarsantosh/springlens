package io.springlens.auth.controller;

import io.springlens.auth.entity.Subscription;
import io.springlens.auth.repository.SubscriptionRepository;
import io.springlens.auth.repository.WorkspaceRepository;
import io.springlens.auth.service.SecretsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Stripe webhook handler.
 * invoice.paid → activate Pro
 * customer.subscription.deleted → downgrade to Free
 *
 * ✅ HARDENED: Webhook secret and signature verification prevent billing attacks
 */
@RestController
@RequestMapping("/v1/billing")
public class BillingController {

    private static final Logger log = LoggerFactory.getLogger(BillingController.class);
    private static final String STRIPE_SECRET_NAME = "springlens/stripe-webhook-secret";

    private final WorkspaceRepository workspaceRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final String webhookSecret;

    public BillingController(WorkspaceRepository workspaceRepository,
                               SubscriptionRepository subscriptionRepository,
                               SecretsService secretsService) {
        this.workspaceRepository = workspaceRepository;
        this.subscriptionRepository = subscriptionRepository;

        // ✅ FIXED: Load Stripe webhook secret from AWS Secrets Manager
        try {
            this.webhookSecret = secretsService.getSecret(STRIPE_SECRET_NAME);
            log.info("Stripe webhook secret loaded from Secrets Manager");
        } catch (Exception ex) {
            log.error("Failed to load Stripe webhook secret from Secrets Manager", ex);
            throw new IllegalStateException("Cannot initialize BillingController without Stripe webhook secret", ex);
        }
    }

    @PostMapping("/webhooks/stripe")
    @Transactional
    public ResponseEntity<Map<String, String>> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature") String signature) {

        log.info("Received Stripe webhook");

        try {
            // ✅ FIXED: Verify signature first using Stripe's SDK
            com.stripe.model.Event event = com.stripe.net.Webhook.constructEvent(
                    payload, signature, webhookSecret);

            log.info("Stripe event type={}", event.getType());

            switch (event.getType()) {
                case "invoice.paid" -> {
                    var invoice = (com.stripe.model.Invoice) event.getDataObjectDeserializer()
                            .getObject().orElse(null);
                    if (invoice != null) handleInvoicePaid(invoice);
                }
                case "customer.subscription.deleted" -> {
                    var subscription = (com.stripe.model.Subscription) event.getDataObjectDeserializer()
                            .getObject().orElse(null);
                    if (subscription != null) handleSubscriptionCanceled(subscription);
                }
                case "customer.subscription.updated" -> {
                    var subscription = (com.stripe.model.Subscription) event.getDataObjectDeserializer()
                            .getObject().orElse(null);
                    if (subscription != null) handleSubscriptionUpdated(subscription);
                }
                default -> log.debug("Unhandled Stripe event type={}", event.getType());
            }

            return ResponseEntity.ok(Map.of("status", "received"));

        } catch (com.stripe.exception.SignatureVerificationException ex) {
            log.warn("Stripe webhook signature verification failed");
            return ResponseEntity.status(401)
                    .body(Map.of("status", "error", "message", "Invalid signature"));
        } catch (Exception ex) {
            log.error("Stripe webhook processing failed", ex);
            return ResponseEntity.status(400)
                    .body(Map.of("status", "error", "message", "Processing failed"));
        }
    }

    private void handleInvoicePaid(com.stripe.model.Invoice data) {
        String stripeSubscriptionId = data.getSubscription();
        if (stripeSubscriptionId == null) return;

        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId)
                .ifPresent(sub -> {
                    var workspace = workspaceRepository.findById(sub.getWorkspaceId()).orElse(null);
                    if (workspace == null) return;

                    workspace.setPlan("pro");
                    workspace.setPlanProjectLimit(10);
                    workspace.setPlanMemberLimit(10);
                    workspace.setPlanEnvironmentLimit(3);
                    workspace.setPlanHistoryDays(365);
                    workspace.setUpdatedAt(Instant.now());
                    workspaceRepository.save(workspace);

                    sub.setStatus("active");
                    sub.setUpdatedAt(Instant.now());
                    subscriptionRepository.save(sub);

                    log.info("Activated Pro plan workspace={}", workspace.getId());
                });
    }

    private void handleSubscriptionCanceled(com.stripe.model.Subscription data) {
        String stripeSubscriptionId = data.getId();

        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId)
                .ifPresent(sub -> {
                    var workspace = workspaceRepository.findById(sub.getWorkspaceId()).orElse(null);
                    if (workspace == null) return;

                    workspace.setPlan("free");
                    workspace.setPlanProjectLimit(1);
                    workspace.setPlanMemberLimit(1);
                    workspace.setPlanEnvironmentLimit(1);
                    workspace.setPlanHistoryDays(90);
                    workspace.setUpdatedAt(Instant.now());
                    workspaceRepository.save(workspace);

                    sub.setStatus("canceled");
                    sub.setCanceledAt(Instant.now());
                    sub.setUpdatedAt(Instant.now());
                    subscriptionRepository.save(sub);

                    log.info("Downgraded workspace to Free plan workspace={}", workspace.getId());
                });
    }

    private void handleSubscriptionUpdated(com.stripe.model.Subscription data) {
        String stripeSubscriptionId = data.getId();
        String status = data.getStatus();

        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId)
                .ifPresent(sub -> {
                    if (status != null) sub.setStatus(status);
                    sub.setUpdatedAt(Instant.now());
                    subscriptionRepository.save(sub);
                    log.info("Updated subscription status={} id={}", status, stripeSubscriptionId);
                });
    }
}
