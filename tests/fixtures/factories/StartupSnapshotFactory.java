package io.springlens.fixtures.factories;

import java.util.*;

/**
 * Factory for creating test StartupSnapshot DTOs.
 * Used across unit, integration, and E2E tests.
 */
public class StartupSnapshotFactory {

    private String projectId = UUID.randomUUID().toString();
    private String environment = "dev";
    private String agentVersion = "1.0.0";
    private String springBootVersion = "3.3.2";
    private String javaVersion = "21.0.3";
    private int totalStartupMs = 5000;
    private String gitCommitSha = "a".repeat(40);
    private List<Map<String, Object>> beans = new ArrayList<>();
    private List<Map<String, Object>> phases = new ArrayList<>();
    private List<Map<String, Object>> autoconfigurations = new ArrayList<>();

    public StartupSnapshotFactory() {
        // Default: 5 beans with 1 bottleneck
        this.beans = List.of(
                beanEvent("dataSource", "com.zaxxer.HikariDataSource", 300, 0, null),
                beanEvent("bean1", "com.example.Bean1", 150, 300, List.of("dataSource")),
                beanEvent("bean2", "com.example.Bean2", 100, 450, List.of("dataSource")),
                beanEvent("bean3", "com.example.Bean3", 50, 550, List.of()),
                beanEvent("bean4", "com.example.Bean4", 150, 600, List.of("bean1"))
        );

        // Default: 4 phases
        this.phases = List.of(
                phaseEvent("context_refresh", 2000, 0),
                phaseEvent("bean_post_processors", 1000, 2000),
                phaseEvent("application_listeners", 1000, 3000),
                phaseEvent("context_loaded", 500, 4000),
                phaseEvent("started", 500, 4500)
        );

        // Default: 2 autoconfigurations
        this.autoconfigurations = List.of(
                autoconfigEvent("org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration", true, 100, 50)
        );
    }

    public StartupSnapshotFactory withProjectId(String projectId) {
        this.projectId = projectId;
        return this;
    }

    public StartupSnapshotFactory withEnvironment(String environment) {
        this.environment = environment;
        return this;
    }

    public StartupSnapshotFactory withTotalStartupMs(int ms) {
        this.totalStartupMs = ms;
        return this;
    }

    public StartupSnapshotFactory withGitCommitSha(String sha) {
        this.gitCommitSha = sha;
        return this;
    }

    public StartupSnapshotFactory withBeans(List<Map<String, Object>> beans) {
        this.beans = beans;
        return this;
    }

    public StartupSnapshotFactory withBottleneckBeans(int count) {
        List<Map<String, Object>> bottlenecks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            bottlenecks.add(beanEvent(
                    "bottleneck" + i,
                    "com.example.Bottleneck" + i,
                    250 + (i * 10),
                    i * 300,
                    null
            ));
        }
        this.beans = bottlenecks;
        return this;
    }

    public StartupSnapshotFactory withSlowStartup() {
        this.totalStartupMs = 10000;
        return this;
    }

    public StartupSnapshotFactory withSlowStartupNoBottlenecks() {
        this.totalStartupMs = 10000;
        this.beans = List.of(
                beanEvent("bean1", "com.example.Bean1", 150, 0, null),
                beanEvent("bean2", "com.example.Bean2", 150, 150, null),
                beanEvent("bean3", "com.example.Bean3", 150, 300, null)
        );
        return this;
    }

    public StartupSnapshotFactory withProxyBeans() {
        List<Map<String, Object>> beanList = new ArrayList<>();
        beanList.add(beanEvent("cglib_proxy", "net.sf.cglib.Proxy", 300, 0, null));
        beanList.add(beanEvent("bean1", "com.example.Bean1", 200, 300, null));
        this.beans = beanList;
        return this;
    }

    public StartupSnapshotFactory withDataSourceBean() {
        List<Map<String, Object>> beanList = new ArrayList<>(beans);
        beanList.add(beanEvent("dataSource", "com.zaxxer.HikariDataSource", 300, 500, null));
        this.beans = beanList;
        return this;
    }

    public Map<String, Object> build() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("project_id", projectId);
        snapshot.put("environment", environment);
        snapshot.put("agent_version", agentVersion);
        snapshot.put("spring_boot_version", springBootVersion);
        snapshot.put("java_version", javaVersion);
        snapshot.put("total_startup_ms", totalStartupMs);
        snapshot.put("git_commit_sha", gitCommitSha);
        snapshot.put("beans", beans);
        snapshot.put("phases", phases);
        snapshot.put("autoconfigurations", autoconfigurations);
        return snapshot;
    }

    public String buildJson() {
        // Simple JSON serialization (use Jackson/Gson in real tests)
        Map<String, Object> map = build();
        return map.toString(); // Simplified; use proper JSON library
    }

    private static Map<String, Object> beanEvent(String beanName, String className, int durationMs, int startMs, List<String> dependencies) {
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("bean_name", beanName);
        bean.put("class_name", className);
        bean.put("duration_ms", durationMs);
        bean.put("start_ms", startMs);
        if (dependencies != null) {
            bean.put("dependencies", dependencies);
        }
        return bean;
    }

    private static Map<String, Object> phaseEvent(String phaseName, int durationMs, int startMs) {
        Map<String, Object> phase = new LinkedHashMap<>();
        phase.put("phase_name", phaseName);
        phase.put("duration_ms", durationMs);
        phase.put("start_ms", startMs);
        return phase;
    }

    private static Map<String, Object> autoconfigEvent(String className, boolean matched, int durationMs, int conditionEvaluationMs) {
        Map<String, Object> autoconfig = new LinkedHashMap<>();
        autoconfig.put("class_name", className);
        autoconfig.put("matched", matched);
        autoconfig.put("duration_ms", durationMs);
        autoconfig.put("condition_evaluation_ms", conditionEvaluationMs);
        return autoconfig;
    }
}
