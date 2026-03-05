package io.springlens.auth.service;

import io.springlens.auth.entity.Workspace;
import io.springlens.auth.repository.ProjectRepository;
import io.springlens.auth.repository.WorkspaceMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Validates plan limits (BR-003) before creating projects/environments/members.
 * Enforcement is hard — excess resources are rejected at API level.
 */
@Service
public class PlanQuotaEnforcer {

    private static final Logger log = LoggerFactory.getLogger(PlanQuotaEnforcer.class);

    private final ProjectRepository projectRepository;
    private final WorkspaceMemberRepository memberRepository;

    public PlanQuotaEnforcer(ProjectRepository projectRepository,
                               WorkspaceMemberRepository memberRepository) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
    }

    public void checkProjectQuota(Workspace workspace) {
        long current = projectRepository.countByWorkspaceIdAndDeletedAtIsNull(workspace.getId());
        if (current >= workspace.getPlanProjectLimit()) {
            log.info("Project quota exceeded workspace={} plan={} limit={} current={}",
                    workspace.getId(), workspace.getPlan(), workspace.getPlanProjectLimit(), current);
            throw new QuotaExceededException(
                    String.format("Project limit reached for %s plan (%d/%d). Upgrade to create more projects.",
                            workspace.getPlan(), current, workspace.getPlanProjectLimit()));
        }
    }

    public void checkMemberQuota(Workspace workspace) {
        long current = memberRepository.countByWorkspaceIdAndDeletedAtIsNull(workspace.getId());
        if (current >= workspace.getPlanMemberLimit()) {
            log.info("Member quota exceeded workspace={} plan={} limit={} current={}",
                    workspace.getId(), workspace.getPlan(), workspace.getPlanMemberLimit(), current);
            throw new QuotaExceededException(
                    String.format("Member limit reached for %s plan (%d/%d). Upgrade to invite more members.",
                            workspace.getPlan(), current, workspace.getPlanMemberLimit()));
        }
    }

    public static class QuotaExceededException extends RuntimeException {
        public QuotaExceededException(String message) {
            super(message);
        }
    }
}
