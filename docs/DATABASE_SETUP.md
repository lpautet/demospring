# Database Setup Guide

## Current Setup: PostgreSQL (Default)

The application uses **PostgreSQL** with native JSONB support for AI memory storage and trading data.

---

## Quick Start

### 1. Start PostgreSQL

```bash
# Using Docker Compose (Recommended)
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

### 2. Start Application

```bash
# Option A: Using startup script (recommended)
./start-with-postgres.sh

# Option B: Using Maven
mvn spring-boot:run

# Option C: Using JAR
java -jar target/demospring.jar
```

**Note:** PostgreSQL is the default database - no profile activation needed!

### 3. Verify Connection

Check application logs for:
```
✅ HikariPool started
✅ Initialized JPA EntityManagerFactory
✅ Tables auto-created
```

Or test manually:
```bash
docker exec -it demospring-postgres psql -U demospring -d demospring -c "SELECT count(*) FROM recommendation_history;"
```

---

## Configuration

### Default Connection (application.properties)

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/demospring
spring.datasource.username=demospring
spring.datasource.password=demospring123
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

### Override with Environment Variables

Create a `.env` file:

```bash
DATABASE_URL=jdbc:postgresql://your-host:5432/your-db
DB_USERNAME=your-username
DB_PASSWORD=your-password
```

### Production (Heroku)

Heroku automatically sets `DATABASE_URL`. The application uses `DatabaseConfig.java` to parse it.

---

## Why PostgreSQL?

### Benefits

✅ **Data Persistence** - Data survives application restarts  
✅ **Production Parity** - Same database in dev and prod  
✅ **JSONB Support** - Native JSON querying and indexing for AI memory  
✅ **Better Testing** - Real data accumulates for analysis  
✅ **Performance** - Test with realistic dataset sizes  
✅ **Scalability** - Production-ready database  

### Features Used

- **JSONB columns** for AI memory storage (efficient JSON queries)
- **Indexes** on frequently queried fields (symbol, timestamp)
- **Foreign keys** for referential integrity
- **Auto-increment** primary keys
- **Timestamps** with time zone support

---

## Database Schema

### Main Tables

#### 1. recommendation_history
Stores AI trading recommendations with memory context.

```sql
CREATE TABLE recommendation_history (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255),
    signal VARCHAR(20),
    confidence VARCHAR(20),
    reasoning TEXT,
    amount DECIMAL(19,2),
    amount_type VARCHAR(20),
    target_price DECIMAL(19,2),
    stop_loss DECIMAL(19,2),
    hold_duration_minutes INTEGER,
    created_at TIMESTAMP WITH TIME ZONE,
    memory JSONB  -- AI's memory context
);
```

#### 2. trading_rules
Configuration for trading parameters.

```sql
CREATE TABLE trading_rules (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20),
    max_position_size DECIMAL(19,2),
    risk_per_trade DECIMAL(5,2),
    enabled BOOLEAN,
    created_at TIMESTAMP WITH TIME ZONE
);
```

---

## Management

### Docker Commands

```bash
# Start PostgreSQL
docker-compose up -d

# Stop PostgreSQL
docker-compose down

# Stop and remove data
docker-compose down -v

# Restart PostgreSQL
docker-compose restart postgres

# View logs
docker-compose logs -f postgres

# Access PostgreSQL shell
docker exec -it demospring-postgres psql -U demospring -d demospring
```

### SQL Queries

```sql
-- View recent recommendations
SELECT id, signal, confidence, reasoning, created_at 
FROM recommendation_history 
ORDER BY created_at DESC 
LIMIT 10;

-- Count recommendations by signal
SELECT signal, COUNT(*) 
FROM recommendation_history 
GROUP BY signal;

-- View AI memory size
SELECT id, jsonb_array_length(memory) as memory_items 
FROM recommendation_history 
WHERE memory IS NOT NULL;

-- Check trading rules
SELECT * FROM trading_rules;
```

### Backup & Restore

```bash
# Backup
docker exec demospring-postgres pg_dump -U demospring demospring > backup.sql

# Restore
docker exec -i demospring-postgres psql -U demospring demospring < backup.sql
```

---

## Troubleshooting

### Connection Refused

**Symptom:**
```
Connection refused: connect
```

**Solution:**
```bash
# Check if PostgreSQL is running
docker-compose ps

# If not running, start it
docker-compose up -d

# Check logs for errors
docker-compose logs postgres
```

### Authentication Failed

**Symptom:**
```
FATAL: password authentication failed
```

**Solution:**
- Verify credentials in `application.properties`
- Check environment variables in `.env`
- Restart PostgreSQL if credentials were changed

### Port Already in Use

**Symptom:**
```
port 5432 already allocated
```

**Solution:**
```bash
# Check what's using port 5432
lsof -i :5432

# Stop other PostgreSQL instance
brew services stop postgresql  # If using Homebrew
# or
sudo systemctl stop postgresql # If using systemctl
```

### Slow Performance

**Solution:**
1. Add indexes on frequently queried columns
2. Increase Docker memory allocation
3. Tune PostgreSQL settings in `docker-compose.yml`

```yaml
command:
  - "postgres"
  - "-c"
  - "shared_buffers=256MB"
  - "-c"
  - "effective_cache_size=1GB"
```

---

## Migration Notes

### From H2 to PostgreSQL

The application was migrated from H2 (in-memory) to PostgreSQL on **2025-11-05**.

**Key Changes:**
- Data now persists across restarts
- JSONB support for efficient memory storage
- Production-ready setup
- Better SQL dialect support

**See:** `docs/archive/POSTGRESQL_MIGRATION_SUMMARY.md` for full details.

---

## Development Workflow

### Reset Database

```bash
# Stop app
# Stop PostgreSQL
docker-compose down -v

# Start PostgreSQL (fresh database)
docker-compose up -d

# Start app (tables will be auto-created)
mvn spring-boot:run
```

### View Real-Time Changes

```bash
# Terminal 1: Watch PostgreSQL logs
docker-compose logs -f postgres

# Terminal 2: Watch application logs
tail -f logs/spring.log

# Terminal 3: Monitor database
watch -n 5 'docker exec demospring-postgres psql -U demospring -d demospring -c "SELECT COUNT(*) FROM recommendation_history;"'
```

---

## Production Deployment

### Heroku PostgreSQL

1. **Add Heroku Postgres addon:**
```bash
heroku addons:create heroku-postgresql:mini
```

2. **Database URL automatically set:**
```bash
heroku config:get DATABASE_URL
```

3. **Application auto-configures** via `DatabaseConfig.java`

### Other Cloud Providers

For AWS RDS, Google Cloud SQL, etc.:

1. Create PostgreSQL instance
2. Get connection URL
3. Set environment variables:
```bash
export DATABASE_URL=jdbc:postgresql://your-host:5432/yourdb
export DB_USERNAME=your-user
export DB_PASSWORD=your-password
```

---

## Resources

- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Hibernate PostgreSQL Dialect](https://docs.jboss.org/hibernate/orm/5.6/userguide/html_single/Hibernate_User_Guide.html)
- [JSONB in PostgreSQL](https://www.postgresql.org/docs/current/datatype-json.html)

---

## Summary

✅ **PostgreSQL is the default database**  
✅ **Start with:** `docker-compose up -d`  
✅ **Connect automatically** - no configuration needed  
✅ **Data persists** across restarts  
✅ **Production-ready** setup  

For troubleshooting or advanced configuration, see sections above.
