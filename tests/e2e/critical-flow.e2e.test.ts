/**
 * End-to-End Tests for Critical Path
 * Agent ingestion → Analysis → Recommendations (complete flow)
 */

import axios from 'axios';
import { v4 as uuidv4 } from 'uuid';

const INGEST_API = 'http://localhost:8081/v1';
const ANALYSIS_API = 'http://localhost:8082/v1';
const RECOMMENDATION_API = 'http://localhost:8083/v1';

describe('E2E: Critical Path (Agent → Analysis → Recommendations)', () => {
    let projectId: string;
    let workspaceId: string;

    beforeAll(() => {
        projectId = uuidv4();
        workspaceId = uuidv4();
    });

    describe('E2E-001: Complete flow with 5 beans, 2 bottlenecks', () => {
        it('should process snapshot through all 3 services within 5 seconds', async () => {
            // Arrange: Create snapshot with bottlenecks
            const snapshotPayload = {
                project_id: projectId,
                environment: 'dev',
                agent_version: '1.0.0',
                spring_boot_version: '3.3.2',
                java_version: '21.0.3',
                total_startup_ms: 5000,
                git_commit_sha: 'a'.repeat(40),
                beans: [
                    {
                        bean_name: 'dataSource',
                        class_name: 'com.zaxxer.HikariDataSource',
                        duration_ms: 300,
                        start_ms: 0,
                        dependencies: [],
                    },
                    {
                        bean_name: 'bean1',
                        class_name: 'com.example.Bean1',
                        duration_ms: 250, // Bottleneck
                        start_ms: 300,
                        dependencies: ['dataSource'],
                    },
                    {
                        bean_name: 'bean2',
                        class_name: 'com.example.Bean2',
                        duration_ms: 150,
                        start_ms: 550,
                        dependencies: ['dataSource'],
                    },
                    {
                        bean_name: 'bean3',
                        class_name: 'com.example.Bean3',
                        duration_ms: 220, // Bottleneck
                        start_ms: 700,
                        dependencies: ['bean1', 'bean2'],
                    },
                    {
                        bean_name: 'bean4',
                        class_name: 'com.example.Bean4',
                        duration_ms: 100,
                        start_ms: 920,
                        dependencies: ['bean3'],
                    },
                ],
                phases: [
                    { phase_name: 'context_refresh', duration_ms: 2500, start_ms: 0 },
                    { phase_name: 'bean_post_processors', duration_ms: 1500, start_ms: 2500 },
                    { phase_name: 'application_listeners', duration_ms: 600, start_ms: 4000 },
                    { phase_name: 'context_loaded', duration_ms: 400, start_ms: 4600 },
                ],
                autoconfigurations: [
                    {
                        class_name: 'org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration',
                        matched: true,
                        duration_ms: 150,
                        condition_evaluation_ms: 75,
                    },
                ],
            };

            // Act 1: Ingest snapshot
            const startTime = Date.now();
            const ingestResponse = await axios.post(`${INGEST_API}/ingest`, snapshotPayload, {
                headers: { Authorization: 'Bearer sl_proj_test_key' },
            });

            const snapshotId = ingestResponse.data.snapshot_id;
            expect(ingestResponse.status).toBe(202);

            // Act 2: Wait for analysis to complete (poll status)
            let analysisComplete = false;
            let recommendationsCount = 0;
            const maxWaitMs = 5000;

            while (!analysisComplete && Date.now() - startTime < maxWaitMs) {
                try {
                    const statusResponse = await axios.get(
                        `${INGEST_API}/snapshots/${snapshotId}/status`,
                        {
                            headers: { Authorization: 'Bearer sl_proj_test_key' },
                        }
                    );

                    if (statusResponse.data.status === 'complete') {
                        analysisComplete = true;
                        recommendationsCount = statusResponse.data.recommendation_count || 0;
                    }
                } catch {
                    // Still processing
                }

                await new Promise(r => setTimeout(r, 100));
            }

            // Assert: Flow completed within 5 seconds
            const elapsedMs = Date.now() - startTime;
            expect(elapsedMs).toBeLessThan(maxWaitMs);
            expect(analysisComplete).toBe(true);

            // Act 3: Verify timeline analysis
            const timelineResponse = await axios.get(
                `${ANALYSIS_API}/projects/${projectId}/snapshots/${snapshotId}/timeline`,
                {
                    headers: { Authorization: 'Bearer test_jwt_token' },
                }
            );

            // Assert: Timeline has correct structure
            expect(timelineResponse.data.beans).toHaveLength(5);
            expect(timelineResponse.data.bottleneck_count).toBe(2);
            expect(timelineResponse.data.phases).toHaveLength(4);

            // Act 4: Verify recommendations generated
            const recommendationsResponse = await axios.get(
                `${RECOMMENDATION_API}/projects/${projectId}/recommendations`,
                {
                    headers: { Authorization: 'Bearer test_jwt_token' },
                }
            );

            // Assert: At least 1 recommendation per bottleneck
            expect(recommendationsResponse.data.recommendations.length).toBeGreaterThanOrEqual(2);

            // Verify recommendation categories
            const categories = recommendationsResponse.data.recommendations.map(
                (r: any) => r.category
            );
            expect(categories).toContain('lazy_loading'); // >= 5 bottlenecks for lazy
            expect(categories).toContain('classpath_optimization'); // CDS always present
        });
    });

    describe('E2E-002: Budget gate integration', () => {
        it('should enforce startup budget in CI/CD flow', async () => {
            const snapshotPayload = {
                project_id: projectId,
                environment: 'ci',
                agent_version: '1.0.0',
                spring_boot_version: '3.3.2',
                java_version: '21.0.3',
                total_startup_ms: 8000, // Over typical budget
                git_commit_sha: 'b'.repeat(40),
                beans: [
                    {
                        bean_name: 'slow_bean',
                        class_name: 'com.example.SlowBean',
                        duration_ms: 8000,
                        start_ms: 0,
                    },
                ],
                phases: [{ phase_name: 'context_refresh', duration_ms: 8000, start_ms: 0 }],
                autoconfigurations: [],
            };

            // Ingest
            const ingestResponse = await axios.post(`${INGEST_API}/ingest`, snapshotPayload, {
                headers: { Authorization: 'Bearer sl_proj_test_key' },
            });

            const snapshotId = ingestResponse.data.snapshot_id;

            // Check budget: 3000ms limit, actual 8000ms
            try {
                await axios.get(
                    `${INGEST_API}/snapshots/${snapshotId}/budget-check?budget_ms=3000`,
                    {
                        headers: { Authorization: 'Bearer sl_proj_test_key' },
                    }
                );
                fail('Budget check should fail');
            } catch (error: any) {
                // Assert: 422 Unprocessable Entity
                expect(error.response.status).toBe(422);
                expect(error.response.data.actual_ms).toBe(8000);
                expect(error.response.data.excess_ms).toBe(5000);
            }
        });
    });

    describe('E2E-003: Deduplication prevents double-processing', () => {
        it('should deduplicate and not re-process identical snapshots', async () => {
            const projectId3 = uuidv4();
            const commitSha = 'c'.repeat(40);

            const snapshotPayload = {
                project_id: projectId3,
                environment: 'dev',
                agent_version: '1.0.0',
                spring_boot_version: '3.3.2',
                java_version: '21.0.3',
                total_startup_ms: 5000,
                git_commit_sha: commitSha,
                beans: [
                    {
                        bean_name: 'bean1',
                        class_name: 'com.example.Bean1',
                        duration_ms: 250,
                        start_ms: 0,
                    },
                    {
                        bean_name: 'bean2',
                        class_name: 'com.example.Bean2',
                        duration_ms: 250,
                        start_ms: 250,
                    },
                    {
                        bean_name: 'bean3',
                        class_name: 'com.example.Bean3',
                        duration_ms: 250,
                        start_ms: 500,
                    },
                    {
                        bean_name: 'bean4',
                        class_name: 'com.example.Bean4',
                        duration_ms: 250,
                        start_ms: 750,
                    },
                    {
                        bean_name: 'bean5',
                        class_name: 'com.example.Bean5',
                        duration_ms: 250,
                        start_ms: 1000,
                    },
                ],
                phases: [{ phase_name: 'context_refresh', duration_ms: 5000, start_ms: 0 }],
                autoconfigurations: [],
            };

            // First request
            const response1 = await axios.post(`${INGEST_API}/ingest`, snapshotPayload, {
                headers: { Authorization: 'Bearer sl_proj_test_key' },
            });

            const snapshotId1 = response1.data.snapshot_id;

            // Second identical request
            const response2 = await axios.post(`${INGEST_API}/ingest`, snapshotPayload, {
                headers: { Authorization: 'Bearer sl_proj_test_key' },
            });

            // Assert
            expect(response1.data.status).toBe('queued');
            expect(response2.data.status).toBe('deduplicated');
            expect(response2.data.snapshot_id).toBe(snapshotId1);
        });
    });

    describe('E2E-004: Status polling transitions', () => {
        it('should transition: queued → processing → complete', async () => {
            const snapshotPayload = {
                project_id: projectId,
                environment: 'staging',
                agent_version: '1.0.0',
                spring_boot_version: '3.3.2',
                java_version: '21.0.3',
                total_startup_ms: 5000,
                git_commit_sha: 'd'.repeat(40),
                beans: [
                    {
                        bean_name: 'bean1',
                        class_name: 'com.example.Bean1',
                        duration_ms: 250,
                        start_ms: 0,
                    },
                ],
                phases: [{ phase_name: 'context_refresh', duration_ms: 5000, start_ms: 0 }],
                autoconfigurations: [],
            };

            const ingestResponse = await axios.post(`${INGEST_API}/ingest`, snapshotPayload, {
                headers: { Authorization: 'Bearer sl_proj_test_key' },
            });

            const snapshotId = ingestResponse.data.snapshot_id;

            // Poll status every 100ms
            const statuses: string[] = [];
            const maxWaitMs = 3000;
            const startTime = Date.now();

            while (Date.now() - startTime < maxWaitMs) {
                try {
                    const statusResponse = await axios.get(
                        `${INGEST_API}/snapshots/${snapshotId}/status`,
                        {
                            headers: { Authorization: 'Bearer sl_proj_test_key' },
                        }
                    );

                    const status = statusResponse.data.status;
                    if (!statuses.includes(status)) {
                        statuses.push(status);
                    }

                    if (status === 'complete') {
                        break;
                    }
                } catch {
                    // Still processing
                }

                await new Promise(r => setTimeout(r, 100));
            }

            // Assert: Saw at least queued and complete
            expect(statuses).toContain('queued');
            expect(statuses).toContain('complete');

            // Optional: processing may or may not be observed depending on timing
        });
    });
});
