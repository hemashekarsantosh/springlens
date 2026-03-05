/**
 * Integration Tests for Ingestion Service
 * Tests real Kafka publishing, Redis deduplication, PostgreSQL persistence.
 */

import axios from 'axios';
import { v4 as uuidv4 } from 'uuid';
import Redis from 'redis';

const INGESTION_API = 'http://localhost:8081/v1';
const redisClient = Redis.createClient({ url: 'redis://localhost:6379' });

describe('Ingestion Service Integration Tests', () => {
    beforeAll(async () => {
        await redisClient.connect();
    });

    afterAll(async () => {
        await redisClient.disconnect();
    });

    afterEach(async () => {
        // Clear Redis cache between tests
        await redisClient.flushDb();
    });

    describe('INT-INGEST-001: Happy path ingest → Kafka publish', () => {
        it('should accept snapshot and publish to Kafka', async () => {
            // Arrange
            const snapshotPayload = {
                project_id: uuidv4(),
                environment: 'dev',
                agent_version: '1.0.0',
                spring_boot_version: '3.3.2',
                java_version: '21.0.3',
                total_startup_ms: 5000,
                git_commit_sha: 'a'.repeat(40),
                beans: [
                    { bean_name: 'dataSource', class_name: 'com.zaxxer.HikariDataSource', duration_ms: 300, start_ms: 0 },
                    { bean_name: 'bean1', class_name: 'com.example.Bean1', duration_ms: 200, start_ms: 300, dependencies: ['dataSource'] },
                ],
                phases: [
                    { phase_name: 'context_refresh', duration_ms: 3000, start_ms: 0 },
                    { phase_name: 'bean_post_processors', duration_ms: 1500, start_ms: 3000 },
                    { phase_name: 'context_loaded', duration_ms: 500, start_ms: 4500 },
                ],
                autoconfigurations: [
                    { class_name: 'org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration', matched: true, duration_ms: 100, condition_evaluation_ms: 50 },
                ],
            };

            // Act
            const response = await axios.post(`${INGESTION_API}/ingest`, snapshotPayload, {
                headers: { Authorization: 'Bearer sl_proj_test_key_valid' },
            });

            // Assert
            expect(response.status).toBe(202);
            expect(response.data.snapshot_id).toBeDefined();
            expect(response.data.status).toBe('queued');

            // Verify snapshot was persisted (poll status)
            await new Promise(resolve => setTimeout(resolve, 1000)); // Wait for async processing
            const statusResponse = await axios.get(`${INGESTION_API}/snapshots/${response.data.snapshot_id}/status`, {
                headers: { Authorization: 'Bearer sl_proj_test_key_valid' },
            });

            expect(statusResponse.data.total_startup_ms).toBe(5000);
        });
    });

    describe('INT-INGEST-002: Deduplication within 60s window', () => {
        it('should return deduplicated response for identical request', async () => {
            // Arrange
            const projectId = uuidv4();
            const commitSha = 'b'.repeat(40);
            const snapshotPayload = {
                project_id: projectId,
                environment: 'dev',
                agent_version: '1.0.0',
                spring_boot_version: '3.3.2',
                java_version: '21.0.3',
                total_startup_ms: 5000,
                git_commit_sha: commitSha,
                beans: [
                    { bean_name: 'bean1', class_name: 'com.example.Bean1', duration_ms: 200, start_ms: 0 },
                ],
                phases: [
                    { phase_name: 'context_refresh', duration_ms: 5000, start_ms: 0 },
                ],
                autoconfigurations: [],
            };

            // Act - First request
            const response1 = await axios.post(`${INGESTION_API}/ingest`, snapshotPayload, {
                headers: { Authorization: 'Bearer sl_proj_test_key_valid' },
            });
            const snapshotId1 = response1.data.snapshot_id;

            // Act - Second request (identical, within 60s)
            const response2 = await axios.post(`${INGESTION_API}/ingest`, snapshotPayload, {
                headers: { Authorization: 'Bearer sl_proj_test_key_valid' },
            });
            const snapshotId2 = response2.data.snapshot_id;

            // Assert
            expect(response1.status).toBe(202);
            expect(response1.data.status).toBe('queued');

            expect(response2.status).toBe(200);
            expect(response2.data.status).toBe('deduplicated');
            expect(snapshotId2).toBe(snapshotId1); // Same snapshot ID
        });
    });

    describe('INT-INGEST-006: Budget check endpoint', () => {
        it('should return 200 when startup within budget', async () => {
            // Arrange
            const snapshotPayload = {
                project_id: uuidv4(),
                environment: 'ci',
                agent_version: '1.0.0',
                spring_boot_version: '3.3.2',
                java_version: '21.0.3',
                total_startup_ms: 3000,
                git_commit_sha: 'c'.repeat(40),
                beans: [],
                phases: [{ phase_name: 'context_refresh', duration_ms: 3000, start_ms: 0 }],
                autoconfigurations: [],
            };

            // Act - Ingest
            const ingestResponse = await axios.post(`${INGESTION_API}/ingest`, snapshotPayload, {
                headers: { Authorization: 'Bearer sl_proj_test_key_valid' },
            });

            // Act - Check budget (5000ms limit, actual 3000ms)
            const budgetResponse = await axios.get(
                `${INGESTION_API}/snapshots/${ingestResponse.data.snapshot_id}/budget-check?budget_ms=5000`,
                {
                    headers: { Authorization: 'Bearer sl_proj_test_key_valid' },
                }
            );

            // Assert
            expect(budgetResponse.status).toBe(200);
            expect(budgetResponse.data.within_budget).toBe(true);
            expect(budgetResponse.data.actual_ms).toBe(3000);
            expect(budgetResponse.data.budget_ms).toBe(5000);
        });

        it('should return 422 when startup exceeds budget', async () => {
            // Arrange
            const snapshotPayload = {
                project_id: uuidv4(),
                environment: 'ci',
                agent_version: '1.0.0',
                spring_boot_version: '3.3.2',
                java_version: '21.0.3',
                total_startup_ms: 6000,
                git_commit_sha: 'd'.repeat(40),
                beans: [
                    { bean_name: 'slow1', class_name: 'com.example.Slow1', duration_ms: 3000, start_ms: 0 },
                    { bean_name: 'slow2', class_name: 'com.example.Slow2', duration_ms: 2000, start_ms: 3000 },
                    { bean_name: 'slow3', class_name: 'com.example.Slow3', duration_ms: 1000, start_ms: 5000 },
                ],
                phases: [{ phase_name: 'context_refresh', duration_ms: 6000, start_ms: 0 }],
                autoconfigurations: [],
            };

            // Act - Ingest
            const ingestResponse = await axios.post(`${INGESTION_API}/ingest`, snapshotPayload, {
                headers: { Authorization: 'Bearer sl_proj_test_key_valid' },
            });

            // Act - Check budget (2000ms limit, actual 6000ms)
            try {
                await axios.get(
                    `${INGESTION_API}/snapshots/${ingestResponse.data.snapshot_id}/budget-check?budget_ms=2000`,
                    {
                        headers: { Authorization: 'Bearer sl_proj_test_key_valid' },
                    }
                );
                fail('Should have thrown 422 error');
            } catch (error: any) {
                // Assert
                expect(error.response.status).toBe(422);
                expect(error.response.data.actual_ms).toBe(6000);
                expect(error.response.data.excess_ms).toBe(4000);
                expect(error.response.data.top_bottlenecks).toBeDefined();
                expect(error.response.data.top_bottlenecks.length).toBeGreaterThan(0);
            }
        });
    });

    describe('INT-INGEST-005: API key validation', () => {
        it('should return 401 for missing API key', async () => {
            const snapshotPayload = {
                project_id: uuidv4(),
                environment: 'dev',
                agent_version: '1.0.0',
                spring_boot_version: '3.3.2',
                java_version: '21.0.3',
                total_startup_ms: 5000,
                git_commit_sha: 'e'.repeat(40),
                beans: [],
                phases: [{ phase_name: 'context_refresh', duration_ms: 5000, start_ms: 0 }],
                autoconfigurations: [],
            };

            try {
                await axios.post(`${INGESTION_API}/ingest`, snapshotPayload);
                fail('Should have thrown 401 error');
            } catch (error: any) {
                expect(error.response.status).toBe(401);
            }
        });
    });

    describe('INT-INGEST-004: Payload size limit', () => {
        it('should return 413 for payload > 10MB', async () => {
            // Create a payload that exceeds 10MB
            const largePayload = {
                project_id: uuidv4(),
                environment: 'dev',
                agent_version: '1.0.0',
                spring_boot_version: '3.3.2',
                java_version: '21.0.3',
                total_startup_ms: 5000,
                git_commit_sha: 'f'.repeat(40),
                beans: Array(100000).fill(0).map((_, i) => ({
                    bean_name: `bean${i}`,
                    class_name: `com.example.Bean${i}`,
                    duration_ms: 100,
                    start_ms: i * 100,
                })),
                phases: [{ phase_name: 'context_refresh', duration_ms: 5000, start_ms: 0 }],
                autoconfigurations: [],
            };

            try {
                await axios.post(`${INGESTION_API}/ingest`, largePayload, {
                    headers: { Authorization: 'Bearer sl_proj_test_key_valid' },
                    maxBodyLength: 11 * 1024 * 1024, // Allow > 10MB for this test
                });
                fail('Should have thrown 413 error');
            } catch (error: any) {
                expect(error.response.status).toBe(413);
            }
        });
    });
});
