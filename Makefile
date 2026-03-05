# SpringLens — Root Makefile
# Usage: make <target>

.PHONY: help dev build test clean lint

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

dev: ## Start all services locally via docker-compose
	docker compose up -d
	@echo "Services starting..."
	@echo "  Frontend:      http://localhost:3000"
	@echo "  Ingestion API: http://localhost:8081"
	@echo "  Analysis API:  http://localhost:8082"
	@echo "  Recs API:      http://localhost:8083"
	@echo "  Auth API:      http://localhost:8084"
	@echo "  Kafka UI:      http://localhost:8090"
	@echo "  Grafana:       http://localhost:3001 (admin/admin)"
	@echo "  Prometheus:    http://localhost:9090"

build: ## Build all services
	./gradlew build -x test
	cd frontend && npm ci && npm run build

test: ## Run all tests
	./gradlew test
	cd frontend && npm run test

test-integration: ## Run integration tests (requires docker)
	./gradlew integrationTest

lint: ## Run linters
	./gradlew spotbugsMain
	cd frontend && npm run lint

clean: ## Clean all build artifacts
	./gradlew clean
	cd frontend && rm -rf .next node_modules

stop: ## Stop docker-compose services
	docker compose down

logs: ## Tail all service logs
	docker compose logs -f

db-migrate: ## Run Flyway migrations for all services
	./gradlew flywayMigrate

format: ## Format all code
	./gradlew spotlessApply
	cd frontend && npm run format
