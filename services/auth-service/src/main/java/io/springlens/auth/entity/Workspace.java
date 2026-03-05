package io.springlens.auth.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspaces")
public class Workspace {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "plan", nullable = false)
    private String plan = "free";

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    @Column(name = "plan_project_limit", nullable = false)
    private int planProjectLimit = 1;

    @Column(name = "plan_member_limit", nullable = false)
    private int planMemberLimit = 1;

    @Column(name = "plan_environment_limit", nullable = false)
    private int planEnvironmentLimit = 1;

    @Column(name = "plan_history_days", nullable = false)
    private int planHistoryDays = 90;

    @Column(name = "sso_enabled", nullable = false)
    private boolean ssoEnabled = false;

    @Column(name = "saml_metadata_url")
    private String samlMetadataUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Workspace() {
    }

    public static Workspace create(String name, String slug) {
        var w = new Workspace();
        w.id = UUID.randomUUID();
        w.name = name;
        w.slug = slug;
        w.plan = "free";
        w.planProjectLimit = 1;
        w.planMemberLimit = 1;
        w.planEnvironmentLimit = 1;
        w.planHistoryDays = 90;
        w.createdAt = Instant.now();
        w.updatedAt = Instant.now();
        return w;
    }

    // Getters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getPlan() { return plan; }
    public String getStripeCustomerId() { return stripeCustomerId; }
    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public int getPlanProjectLimit() { return planProjectLimit; }
    public int getPlanMemberLimit() { return planMemberLimit; }
    public int getPlanEnvironmentLimit() { return planEnvironmentLimit; }
    public int getPlanHistoryDays() { return planHistoryDays; }
    public boolean isSsoEnabled() { return ssoEnabled; }
    public String getSamlMetadataUrl() { return samlMetadataUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    // Setters
    public void setName(String name) { this.name = name; }
    public void setPlan(String plan) { this.plan = plan; }
    public void setStripeCustomerId(String stripeCustomerId) { this.stripeCustomerId = stripeCustomerId; }
    public void setStripeSubscriptionId(String stripeSubscriptionId) { this.stripeSubscriptionId = stripeSubscriptionId; }
    public void setPlanProjectLimit(int planProjectLimit) { this.planProjectLimit = planProjectLimit; }
    public void setPlanMemberLimit(int planMemberLimit) { this.planMemberLimit = planMemberLimit; }
    public void setPlanEnvironmentLimit(int planEnvironmentLimit) { this.planEnvironmentLimit = planEnvironmentLimit; }
    public void setPlanHistoryDays(int planHistoryDays) { this.planHistoryDays = planHistoryDays; }
    public void setSsoEnabled(boolean ssoEnabled) { this.ssoEnabled = ssoEnabled; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
