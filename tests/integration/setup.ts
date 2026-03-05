/**
 * Integration Test Setup
 * Global hooks for starting/stopping containers, running migrations, seeding data.
 */

import { spawn } from 'child_process';
import * as fs from 'fs';
import * as path from 'path';

const projectRoot = path.resolve(__dirname, '../../');
const composeFile = path.join(__dirname, 'docker-compose.test.yml');

/**
 * Start docker-compose test infrastructure before all tests
 */
export async function startTestInfrastructure(): Promise<void> {
    console.log('🟢 Starting test infrastructure...');

    return new Promise((resolve, reject) => {
        const compose = spawn('docker-compose', ['-f', composeFile, 'up', '-d'], {
            cwd: projectRoot,
            stdio: 'inherit',
        });

        compose.on('close', (code) => {
            if (code !== 0) {
                reject(new Error(`docker-compose up failed with code ${code}`));
            } else {
                console.log('✅ Test infrastructure started');
                // Wait for services to be healthy
                setTimeout(resolve, 10000);
            }
        });
    });
}

/**
 * Stop docker-compose after all tests
 */
export async function stopTestInfrastructure(): Promise<void> {
    console.log('🔴 Stopping test infrastructure...');

    return new Promise((resolve, reject) => {
        const compose = spawn('docker-compose', ['-f', composeFile, 'down', '-v'], {
            cwd: projectRoot,
            stdio: 'inherit',
        });

        compose.on('close', (code) => {
            if (code !== 0) {
                reject(new Error(`docker-compose down failed with code ${code}`));
            } else {
                console.log('✅ Test infrastructure stopped');
                resolve();
            }
        });
    });
}

/**
 * Run database migrations
 */
export async function runMigrations(): Promise<void> {
    console.log('🔵 Running database migrations...');

    const migrationsDir = path.join(projectRoot, 'schemas/migrations');
    const files = fs.readdirSync(migrationsDir).filter(f => f.endsWith('.sql')).sort();

    for (const file of files) {
        const sqlPath = path.join(migrationsDir, file);
        console.log(`  Running ${file}...`);

        return new Promise((resolve, reject) => {
            const psql = spawn('psql', [
                '-h', 'localhost',
                '-U', 'springlens',
                '-d', 'springlens_test',
                '-f', sqlPath,
            ], {
                env: { ...process.env, PGPASSWORD: 'testpass123' },
                stdio: 'inherit',
            });

            psql.on('close', (code) => {
                if (code !== 0) {
                    reject(new Error(`Migration ${file} failed`));
                } else {
                    resolve();
                }
            });
        });
    }

    console.log('✅ Migrations completed');
}

/**
 * Seed test data into database
 */
export async function seedTestData(): Promise<void> {
    console.log('🌱 Seeding test data...');

    // Seed workspaces, projects, users
    const seedQueries = `
        INSERT INTO users (id, email, github_id, created_at) VALUES
            ('user-1', 'test@example.com', 'github-123', NOW())
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO workspaces (id, name, owner_id, plan, created_at) VALUES
            ('workspace-1', 'Test Workspace', 'user-1', 'pro', NOW())
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO projects (id, workspace_id, name, slug, created_at) VALUES
            ('project-1', 'workspace-1', 'Test Project', 'test-project', NOW())
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO environments (id, project_id, name, created_at) VALUES
            ('env-1', 'project-1', 'dev', NOW()),
            ('env-2', 'project-1', 'staging', NOW()),
            ('env-3', 'project-1', 'prod', NOW())
        ON CONFLICT (id) DO NOTHING;
    `;

    // Execute via psql
    return new Promise((resolve, reject) => {
        const psql = spawn('psql', [
            '-h', 'localhost',
            '-U', 'springlens',
            '-d', 'springlens_test',
        ], {
            env: { ...process.env, PGPASSWORD: 'testpass123' },
        });

        psql.stdin?.write(seedQueries);
        psql.stdin?.end();

        psql.on('close', (code) => {
            if (code !== 0) {
                reject(new Error('Seed data insert failed'));
            } else {
                console.log('✅ Test data seeded');
                resolve();
            }
        });
    });
}

/**
 * Truncate all test data (cleanup between tests)
 */
export async function cleanupTestData(): Promise<void> {
    const truncateQueries = `
        TRUNCATE startup_snapshots, startup_timelines, recommendations, deliveries CASCADE;
    `;

    return new Promise((resolve) => {
        const psql = spawn('psql', [
            '-h', 'localhost',
            '-U', 'springlens',
            '-d', 'springlens_test',
        ], {
            env: { ...process.env, PGPASSWORD: 'testpass123' },
        });

        psql.stdin?.write(truncateQueries);
        psql.stdin?.end();
        psql.on('close', () => resolve());
    });
}

/**
 * Jest setup hook
 */
beforeAll(async () => {
    await startTestInfrastructure();
    await runMigrations();
    await seedTestData();
});

afterAll(async () => {
    await cleanupTestData();
    await stopTestInfrastructure();
});

afterEach(async () => {
    await cleanupTestData();
});
