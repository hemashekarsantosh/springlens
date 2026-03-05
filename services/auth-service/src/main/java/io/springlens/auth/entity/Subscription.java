package io.springlens.auth.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "stripe_subscription_id", nullable = false, unique = true)
    private String stripeSubscriptionId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "plan", nullable = false)
    private String plan;

    @Column(name = "amount_cents", nullable = false)
    private int amountCents;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Subscription() {
    }

    public static Subscription create(UUID workspaceId, String stripeSubscriptionId,
                                       String status, String plan, int amountCents,
                                       Instant periodStart, Instant periodEnd) {
        var s = new Subscription();
        s.id = UUID.randomUUID();
        s.workspaceId = workspaceId;
        s.stripeSubscriptionId = stripeSubscriptionId;
        s.status = status;
        s.plan = plan;
        s.amountCents = amountCents;
        s.currentPeriodStart = periodStart;
        s.currentPeriodEnd = periodEnd;
        s.createdAt = Instant.now();
        s.updatedAt = Instant.now();
        return s;
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public String getStatus() { return status; }
    public String getPlan() { return plan; }
    public int getAmountCents() { return amountCents; }
    public Instant getCurrentPeriodStart() { return currentPeriodStart; }
    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public Instant getCanceledAt() { return canceledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(String status) { this.status = status; }
    public void setPlan(String plan) { this.plan = plan; }
    public void setCurrentPeriodStart(Instant currentPeriodStart) { this.currentPeriodStart = currentPeriodStart; }
    public void setCurrentPeriodEnd(Instant currentPeriodEnd) { this.currentPeriodEnd = currentPeriodEnd; }
    public void setCanceledAt(Instant canceledAt) { this.canceledAt = canceledAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
