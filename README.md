# DemoSpring

A Spring Boot application that integrates weather data from Netatmo devices with Salesforce Data Cloud for analytics and visualization.

## 📋 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [Installation & Setup](#installation--setup)
- [Security & Vulnerability Scanning](#security--vulnerability-scanning)
- [API Endpoints](#api-endpoints)
- [Development](#development)
- [Deployment](#deployment)
- [Documentation](#documentation)

## 🔍 Overview

This application provides:
- **Netatmo Integration**: Collects weather station data via OAuth2
- **Salesforce Data Cloud**: Ingests sensor data for analytics
- **React Frontend**: Visualizes weather metrics with charts
- **JWT Authentication**: Secure API access
- **Caching**: Redis-based caching for improved performance
- **Monitoring**: Spring Actuator endpoints for health checks

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   React Web     │    │   Spring Boot   │    │   Salesforce    │
│     Client      │◄──►│   Application   │◄──►│   Data Cloud    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                │
                        ┌───────┼───────┐
                        │       │       │
                ┌───────▼──┐ ┌──▼───┐ ┌─▼──────────┐
                │  Redis   │ │  H2  │ │  Netatmo   │
                │  Cache   │ │  DB  │ │    API     │
                └──────────┘ └──────┘ └────────────┘
```

**Tech Stack:**
- **Backend**: Spring Boot 4.1.1, Java 25
- **Frontend**: React 19.3, Vite, React Compiler, Chart.js
- **Database**: H2 (dev), PostgreSQL (prod)
- **Cache**: Redis
- **Security**: Spring Security, JWT
- **Build**: Maven, NPM

## 📋 Prerequisites

- **Java 25**
- **Node.js 24 recommended** (Maven installs the pinned Node and npm versions automatically)
- **Redis** server
- **Netatmo developer account**
- **Salesforce org** with Data Cloud access

## ⚙️ Configuration

### Environment Variables

Create a `.env` file in the project root:

```bash
# Database
REDIS_URL=redis://localhost:6379

# Netatmo API
NETATMO_CLIENT_ID=your_netatmo_client_id
NETATMO_CLIENT_SECRET=your_netatmo_client_secret

# Application
REDIRECT_URI=http://localhost:8080
JWT_SECRET=your_jwt_secret_key_256_bits_minimum

# Salesforce
SF_LOGIN_URL=https://login.salesforce.com
SF_CLIENT_ID=your_salesforce_consumer_key
SF_USERNAME=your_salesforce_username
SF_SESSION_TIMEOUT=3600
SF_CONNECTOR_NAME=your_connector_name
```

### Salesforce Data Cloud Setup

#### 1. Create SSL Certificate
```bash
openssl genrsa 2048 > .private.key
openssl req -new -x509 -nodes -sha256 -days 365 -key .private.key -out server.crt
```

#### 2. Set Private Key Environment Variable
```bash
export SF_PRIVATE_KEY=$(awk 'BEGIN{RS=EOF} {gsub(/\n/,"",$0); print $0}' .private.key)
```

#### 3. Configure Salesforce Connected App
1. **Setup → App Manager → New Connected App**
2. **OAuth Settings:**
   - Scopes: `cdp_ingest_api`, `cdp_api`, `api`, `refresh_token`, `offline_access`
   - Callback URL: (not used, put any valid URL)
   - ✅ Enable JWT Bearer Flow
   - Upload `server.crt` certificate

#### 4. Data Cloud Configuration
1. **Data Management → Ingestion API**
   - Create new connector using provided YAML schema
2. **Data Streams → New Data Stream**
   - Type: Ingestion API
   - Category: Engagement
   - Primary Key: Device ID
   - Event Time Field: timestamp

## 🚀 Installation & Setup

### 1. Clone Repository
```bash
git clone <repository-url>
cd demospring
```

### 2. Install Dependencies
```bash
# Backend dependencies (Maven will handle this)
mvn clean install

# Frontend dependencies
cd webapp
npm install
cd ..
```

### 3. Start Redis
```bash
# Using Docker
docker run -d --name redis -p 6379:6379 redis:alpine

# Or using local installation
redis-server
```

### 4. Run Application
```bash
source .env && mvn spring-boot:run
```

The application will be available at:
- **Backend API**: http://localhost:8080
- **Frontend**: http://localhost:8080 (served by Spring Boot)
- **Actuator**: http://localhost:8080/actuator

## 🔒 Security & Vulnerability Scanning

This project includes comprehensive security scanning and dependency management:

### Automated Dependency Updates
- **GitHub Dependabot** automatically creates PRs for dependency updates
- Configured for Maven, NPM, and GitHub Actions
- Weekly schedule with grouped updates to reduce noise

### Vulnerability Scanning

#### NPM Audit (Frontend)
```bash
cd webapp
npm audit

# Fix vulnerabilities automatically
npm audit fix
```

#### GitHub Actions
- **Automated scans** on every push/PR
- **Weekly scheduled scans** for continuous monitoring
- **Reports uploaded** as artifacts for review

### Configuration
- **NPM Audit Level**: Moderate and above vulnerabilities reported
- **Automated Fixes**: Use `npm audit fix` to resolve issues automatically
- **Scan Results**: Available in GitHub Actions artifacts

### Security Best Practices
- JWT tokens with configurable expiration
- CORS and CSRF protection
- Secure OAuth2 flows for external APIs
- Environment variable based configuration
- Private keys excluded from version control

## 🛠️ API Endpoints

### Authentication
- `POST /api/auth/login` - User login
- `POST /api/auth/signup` - User registration
- `GET /api/auth/authorizeAtmo` - Netatmo OAuth redirect

### Weather Data
- `GET /api/homesdata` - Netatmo homes and devices
- `GET /api/homestatus` - Current device status
- `GET /api/getmeasure` - Historical measurements

### Salesforce Integration
- `GET /api/salesforce/accounts` - Salesforce account data
- `GET /api/datacloud/data` - Query Data Cloud data
- `GET /api/salesforce/whoami` - Current SF user info

### Monitoring
- `GET /actuator/health` - Application health status
- `GET /actuator/metrics` - Application metrics

## 💻 Development

### Build Frontend
```bash
cd webapp
npm run build
```

### Run Tests
```bash
# Backend tests
mvn test

# Frontend tests
cd webapp
npm test
```

### Development Mode
```bash
# Backend only
mvn spring-boot:run

# Frontend development server (separate terminal)
cd webapp
npm start
```

### Code Quality
- **Lombok** for reducing boilerplate
- **SLF4J** for structured logging
- **Spring Boot DevTools** for hot reload
- **Caching** with Redis and Spring Cache abstraction

## 🚢 Deployment

### Production Build
```bash
mvn clean package -Pproduction
```

### Docker Deployment
```bash
# Build image
docker build -t demospring .

# Run with environment file
docker run --env-file .env -p 8080:8080 demospring
```

### Environment-Specific Configuration
- **Development**: H2 database, local Redis
- **Production**: PostgreSQL, managed Redis instance
- **Configuration profiles**: Use Spring profiles for environment-specific settings

---


- Ensure `.env` file is never committed to version control
- Review Dependabot PRs before merging
- Monitor security scan results in GitHub Actions
- Keep Salesforce certificates secure and rotate regularly

---

## Documentation

Additional guides are available in the `docs` directory. The project targets Java 25 (LTS).
