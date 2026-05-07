# Intelligent Data Labeling & Analysis Platform

An LLM-assisted data labeling platform based on DeepSeek (OpenAI-compatible API). It supports Excel/CSV row-by-row labeling with versioned custom labels, async processing with real-time progress, manual corrections, export, archiving, external DB sync, and basic statistics.

## Tech Stack
- Frontend: Vue 3 + Vite + Element Plus
- Backend: Spring Boot 2.7 + Java 8 + Spring Security (JWT) + JPA
- Storage: MySQL 8 (Redis optional)

## Docs
- Requirements: `项目需求.txt` (Chinese)
- Implementation plan: `实现方案.md` (Chinese)
- Backend guide: `backend/README.md` (Chinese)

## Run Locally (Backend)
1. Init MySQL: run `backend/sql/schema.sql`
2. Configure: edit `backend/src/main/resources/application.yml`
3. Start: run `mvn spring-boot:run` under `backend/` (or build jar via `mvn clean package -DskipTests`)

Default admin account: `admin / admin123` (change it in production).
