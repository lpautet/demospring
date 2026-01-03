# PostgreSQL Setup Guide

## Why PostgreSQL for Local Development?

✅ **Data Persistence** - No more data loss on restart  
✅ **Production Parity** - Same database in dev and prod  
✅ **JSONB Support** - Native JSON querying and indexing  
✅ **Better Testing** - Real data accumulates for analysis  
✅ **Performance** - Test with realistic dataset sizes  

---

## Quick Start (Docker - Recommended)

### 1. Start PostgreSQL

```bash
# Start PostgreSQL in Docker
docker-compose up -d

# Check it's running
docker-compose ps

# View logs
docker-compose logs -f postgres
```

**Database Details:**
- Host: `localhost:5432`
- Database: `demospring`
- Username: `demospring`
- Password: `demospring123`

### 2. Run Application

```bash
# Option A: Using startup script (recommended)
./start-with-postgres.sh

# Option B: Using Maven directly
mvn spring-boot:run

# Option C: Using Java
java -jar target/demospring.jar
```

**Note:** PostgreSQL is now the default database - no profile needed!

### 3. Verify Connection

Check application logs for:
```
Hibernate: create table recommendation_history (...)
Using dialect: org.hibernate.dialect.PostgreSQLDialect
```

### 4. Connect to Database (Optional)

```bash
# Using Docker
docker exec -it demospring-postgres psql -U demospring -d demospring

# Or using local psql
psql -h localhost -U demospring -d demospring

# Query recommendations
SELECT id, timestamp, signal, confidence, ai_memory::text 
FROM recommendation_history 
ORDER BY timestamp DESC 
LIMIT 5;
```

---

## What Changed?

### 1. Added Dependencies (`pom.xml`)

```xml
<!-- Hibernate Types for JSONB support -->
<dependency>
    <groupId>io.hypersistence</groupId>
    <artifactId>hypersistence-utils-hibernate-60</artifactId>
    <version>3.7.0</version>
</dependency>
```

### 2. Updated Entity (`RecommendationHistory.java`)

**Before (H2-compatible):**
```java
@Convert(converter = StringListConverter.class)
@Column(columnDefinition = "TEXT")
private List<String> aiMemory;
```

**After (PostgreSQL JSONB):**
```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(columnDefinition = "jsonb")
private List<String> aiMemory;
```

### 3. Added PostgreSQL Profile

**File:** `application-postgres.properties`
- Database connection to PostgreSQL
- JSONB type mapping enabled
- Connection pooling configured

### 4. Added Docker Compose

**File:** `docker-compose.yml`
- PostgreSQL 15 Alpine image
- Persistent volume for data
- Health checks enabled

---

## JSONB Benefits

### 1. Native JSON Operations

```sql
-- Query specific memory items
SELECT * FROM recommendation_history 
WHERE ai_memory @> '["Taking profit"]';

-- Extract specific fields
SELECT 
    id, 
    timestamp,
    ai_memory->0 as first_memory_item
FROM recommendation_history;

-- Count recommendations by memory content
SELECT 
    jsonb_array_elements_text(ai_memory) as memory_item,
    COUNT(*) 
FROM recommendation_history 
GROUP BY memory_item;
```

### 2. Indexing for Performance

```sql
-- Add GIN index for fast JSON queries
CREATE INDEX idx_ai_memory_gin ON recommendation_history USING GIN (ai_memory);

-- Query is now fast even with millions of rows
SELECT * FROM recommendation_history 
WHERE ai_memory @> '["Waiting for breakout"]';
```

### 3. JSON Path Queries

```sql
-- Find recommendations with specific execution status
SELECT * FROM recommendation_history 
WHERE execution_result->>'status' = 'FILLED';

-- Find high-confidence recommendations
SELECT * FROM recommendation_history 
WHERE confidence = 'HIGH' 
  AND ai_memory @> '["Strong momentum"]';
```

---

## Database Operations

### View All Recommendations

```sql
SELECT 
    id,
    timestamp,
    signal,
    confidence,
    amount,
    amount_type,
    executed,
    jsonb_pretty(ai_memory) as memory,
    reasoning
FROM recommendation_history
ORDER BY timestamp DESC
LIMIT 10;
```

### Memory Evolution Analysis

```sql
-- See how AI's memory changes over time
SELECT 
    timestamp,
    signal,
    jsonb_array_elements_text(ai_memory) as memory_point
FROM recommendation_history
ORDER BY timestamp DESC;
```

### Execution Statistics

```sql
-- Success rate by confidence level
SELECT 
    confidence,
    COUNT(*) as total,
    SUM(CASE WHEN executed THEN 1 ELSE 0 END) as executed_count,
    ROUND(100.0 * SUM(CASE WHEN executed THEN 1 ELSE 0 END) / COUNT(*), 2) as execution_rate
FROM recommendation_history
GROUP BY confidence
ORDER BY confidence;
```

### Find Pattern Correlations

```sql
-- What memory points lead to execution?
SELECT 
    jsonb_array_elements_text(ai_memory) as memory_item,
    AVG(CASE WHEN executed THEN 1.0 ELSE 0.0 END) as execution_rate,
    COUNT(*) as occurrence_count
FROM recommendation_history
WHERE ai_memory IS NOT NULL
GROUP BY memory_item
HAVING COUNT(*) >= 3
ORDER BY execution_rate DESC;
```

---

## Database Configuration

### PostgreSQL (Default)

PostgreSQL is now the **default database** configured in `application.properties`.

```bash
# Start PostgreSQL
docker-compose up -d

# Run application (uses PostgreSQL automatically)
mvn spring-boot:run
```

### Switch to H2 (Temporary Testing)

If you need H2 for quick tests without persistence:

1. **Comment out PostgreSQL settings in `application.properties`:**
   ```properties
   #spring.datasource.url=...
   #spring.datasource.driver-class-name=org.postgresql.Driver
   #spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
   ```

2. **Run without PostgreSQL:**
   ```bash
   mvn spring-boot:run
   ```

⚠️ **Note:** H2 data is lost on restart. PostgreSQL is recommended for development.

### Verify Database Connection

Check application logs on startup:
```
HikariPool-1 - Starting...
HikariPool-1 - Start completed.
Hibernate: ... (PostgreSQL DDL statements)
```

---

## Docker Commands

### Basic Operations

```bash
# Start
docker-compose up -d

# Stop (keeps data)
docker-compose stop

# Start again (data preserved!)
docker-compose start

# Stop and remove containers (keeps data)
docker-compose down

# Stop and WIPE ALL DATA
docker-compose down -v

# View logs
docker-compose logs -f postgres

# Check status
docker-compose ps
```

### Backup and Restore

```bash
# Backup database
docker exec demospring-postgres pg_dump -U demospring demospring > backup.sql

# Restore database
docker exec -i demospring-postgres psql -U demospring demospring < backup.sql
```

### Database Shell

```bash
# Open psql
docker exec -it demospring-postgres psql -U demospring -d demospring

# Useful psql commands:
\dt              # List tables
\d+ table_name   # Describe table
\l               # List databases
\q               # Quit
```

---

## Migration from H2

### If You Have Existing H2 Data

**Option 1: Clean Start**
```bash
# Just switch to PostgreSQL (empty database)
docker-compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

**Option 2: Manual Export/Import**
```sql
-- Export from H2 (if needed)
SELECT * FROM recommendation_history;
-- Save results and insert into PostgreSQL
```

**Option 3: Keep Both**
```bash
# H2 for quick tests
mvn spring-boot:run

# PostgreSQL for development
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

---

## Troubleshooting

### Can't Connect to PostgreSQL

```bash
# Check if container is running
docker ps | grep postgres

# Check logs
docker-compose logs postgres

# Restart
docker-compose restart postgres

# Full reset
docker-compose down
docker-compose up -d
```

### Port 5432 Already in Use

```bash
# Find what's using port 5432
lsof -i :5432

# Option 1: Stop local PostgreSQL
brew services stop postgresql

# Option 2: Change Docker port
# Edit docker-compose.yml: "5433:5432"
# Update application-postgres.properties: jdbc:postgresql://localhost:5433/...
```

### Schema Issues

```bash
# Drop and recreate
docker exec -it demospring-postgres psql -U demospring -d demospring -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

# Or change ddl-auto to create
# In application-postgres.properties:
spring.jpa.hibernate.ddl-auto=create
```

### Check JSONB is Working

```sql
-- Verify JSONB columns
SELECT 
    column_name, 
    data_type 
FROM information_schema.columns 
WHERE table_name = 'recommendation_history' 
  AND column_name IN ('ai_memory', 'execution_result', 'market_context');

-- Should show: jsonb
```

---

## Production Considerations

### For Production Deployment

1. **Use Managed PostgreSQL**
   - AWS RDS, Google Cloud SQL, Azure Database
   - Automated backups
   - High availability

2. **Update Configuration**
   ```properties
   # Use environment variables
   spring.datasource.url=${DATABASE_URL}
   spring.datasource.username=${DB_USERNAME}
   spring.datasource.password=${DB_PASSWORD}
   ```

3. **Change DDL Auto**
   ```properties
   # In production, never use create/update
   spring.jpa.hibernate.ddl-auto=validate
   
   # Use Flyway or Liquibase for migrations
   ```

4. **Add Connection Pooling**
   ```properties
   spring.datasource.hikari.maximum-pool-size=20
   spring.datasource.hikari.minimum-idle=10
   ```

---

## Performance Tips

### 1. Add Indexes

```sql
-- For frequent queries
CREATE INDEX idx_rec_signal_timestamp ON recommendation_history(signal, timestamp DESC);
CREATE INDEX idx_rec_executed_timestamp ON recommendation_history(executed, timestamp DESC);

-- For JSON queries (already added in schema)
CREATE INDEX idx_ai_memory_gin ON recommendation_history USING GIN (ai_memory);
```

### 2. Analyze Tables

```sql
-- Update statistics for query planner
ANALYZE recommendation_history;

-- View index usage
SELECT schemaname, tablename, indexname, idx_scan 
FROM pg_stat_user_indexes 
WHERE schemaname = 'public';
```

### 3. Monitor Performance

```sql
-- Slow queries
SELECT query, mean_exec_time, calls 
FROM pg_stat_statements 
ORDER BY mean_exec_time DESC 
LIMIT 10;

-- Table sizes
SELECT 
    relname as table_name,
    pg_size_pretty(pg_total_relation_size(relid)) as total_size
FROM pg_catalog.pg_statio_user_tables 
ORDER BY pg_total_relation_size(relid) DESC;
```

---

## Summary

✅ **Setup Complete:**
- Docker Compose configured
- PostgreSQL profile added
- JSONB support enabled
- Converters kept for H2 compatibility

✅ **Commands to Remember:**
```bash
# Start PostgreSQL
docker-compose up -d

# Run with PostgreSQL
mvn spring-boot:run -Dspring-boot.run.profiles=postgres

# Connect to DB
docker exec -it demospring-postgres psql -U demospring -d demospring

# Stop and keep data
docker-compose stop

# Stop and wipe data
docker-compose down -v
```

✅ **Benefits Gained:**
- 💾 Data persists between restarts
- 🔍 Query AI memory with JSONB operators
- 📊 Build meaningful historical analysis
- 🎯 Production-like environment

---

**Next Steps:**
1. Start PostgreSQL: `docker-compose up -d`
2. Run app: `mvn spring-boot:run -Dspring-boot.run.profiles=postgres`
3. Generate recommendations and watch them persist!
4. Try JSONB queries on AI memory
