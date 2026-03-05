package io.springlens.auth.repository;

import io.springlens.auth.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    Optional<Workspace> findBySlug(String slug);
    Optional<Workspace> findByStripeCustomerId(String stripeCustomerId);
    Optional<Workspace> findByStripeSubscriptionId(String stripeSubscriptionId);
}
