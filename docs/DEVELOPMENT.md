# Caliper — Developer Setup & Contributor Guide

This guide provides instructions for setting up, building, running, and testing Caliper in a local development environment.

---

## 1. Prerequisites

Ensure the following tools are installed on your host machine:

| Component | Minimum Version | Required For |
| :--- | :---: | :--- |
| **Java JDK** | 21+ *(Java 21 or 25 supported)* | Backend API, Judge worker, Plagiarism engine |
| **Apache Maven** | 3.9+ | Multi-module Java build and test execution |
| **Node.js** | 20+ LTS *(v20 or v22)* | React frontend toolchain |
| **npm** | 10+ | Frontend package management |
| **PostgreSQL** | 16+ | Application and testing relational database |
| **Docker Engine** | 24+ / Docker Desktop | Sandboxed container execution |

---

## 2. Environment Configuration

Copy the example environment configuration to `.env`:

```bash
cp .env.example .env
```

### Key Environment Variables
```ini
# Application Secrets
JWT_SECRET=development-insecure-secret-key-at-least-32-chars-long
SERVER_PORT=8080

# Database Configuration
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/codesentinel
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres

# Storage Provider (Local File System)
STORAGE_PROVIDER=local
STORAGE_ROOT=./storage

# Code Execution Sandbox
CALIPER_DOCKER_BIN=docker
CALIPER_SANDBOX_RUNTIME=docker
CALIPER_JUDGE_WORK_ROOT=./storage/judge

# Demo Campus Seeding
APP_SEED_DEMO=true
```

---

## 3. Database Initialization

Ensure PostgreSQL is running locally and create the development and test databases:

```sql
CREATE DATABASE codesentinel;
CREATE DATABASE codesentinel_test;
```

Migrations are automatically applied on application startup by Flyway from `backend/src/main/resources/db/migration/`.

---

## 4. Sandbox Images Preparation

Caliper executes code inside dedicated runner containers. Build the local runner images before running integration tests:

### On Linux / macOS:
```bash
./scripts/build-sandbox-images.sh
```

### On Windows (PowerShell):
```powershell
.\scripts\windows\build-sandbox-images.ps1
```

This builds the standardized runner images:
- `codesentinel/runner-native:1` (C & C++)
- `codesentinel/runner-java:1` (Java 21)
- `codesentinel/runner-python:1` (Python 3.11+)
- `codesentinel/runner-node:1` (Node.js 20+)

---

## 5. Running the Application Locally

### 5.1 Run the Backend
```bash
# Using local Maven
mvn spring-boot:run -pl backend
```
The backend API starts on `http://localhost:8080`.

### 5.2 Run the Frontend
```bash
cd frontend
npm install
npm run dev
```
The frontend starts on `http://localhost:5173` and proxies `/api` calls to `http://localhost:8080`.

---

## 6. Demo Accounts (Local Development)

When `APP_SEED_DEMO=true` is enabled, the database is pre-seeded with sample courses, assignments, quizzes, and accounts:

| Role | Email | Password | Access Scope |
| :--- | :--- | :--- | :--- |
| **Instructor** | `demo-instructor@demo.invalid` | `demo-password-1` | Lead instructor across all seeded courses |
| **Student** | `student1@demo.invalid` | `demo-password-1` | Enrolled student in CS201, CS301 |
| **Student** | `student2@demo.invalid` | `demo-password-1` | Enrolled student in CS201, CS301 |
| **Student** | `student3@demo.invalid` | `demo-password-1` | Enrolled student in CS201, CS301 |

> [!WARNING]
> These credentials are for local development and demonstration only. Never deploy them to production environments.

---

## 7. Running the Automated Test Suite

### 7.1 Backend Tests (All Modules)
```bash
# Run all unit, integration, and scenario tests (parent, engine, judge, backend)
mvn test
```

### 7.2 Run Specific Module Tests
```bash
# Plagiarism engine benchmarks and quality evaluation
mvn test -pl engine

# Sandbox container tests
mvn test -pl judge

# API, RBAC, LTI, and acceptance tests
mvn test -pl backend
```

### 7.3 Frontend Tests & Build
```bash
cd frontend

# Run Vitest test suite
npm test -- --run

# Run TypeScript type check and production bundle build
npm run build
```

---

## 8. Code Formatting & Quality Standards

- Maintain clean separation between controllers, services, repositories, and domain DTO records.
- Preserve 100% backward compatibility for existing REST API paths and database migrations.
- Verify both Linux and Windows line endings using `.gitattributes`.
