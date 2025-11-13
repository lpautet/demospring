# Database Configuration

## Current Setup: PostgreSQL (Default)

The application now uses **PostgreSQL by default** with native JSONB support for AI memory storage.

---

## Quick Start

### 1. Start PostgreSQL
```bash
docker-compose up -d
```

### 2. Start Application
```bash
# Simple way
./start-with-postgres.sh

# Or manually
mvn spring-boot:run
```

**That's it!** PostgreSQL is now the default database.

---

## Configuration

### Default Connection (from application.properties)

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/demospring
spring.datasource.username=demospring
spring.datasource.password=demospring123
```

### Override with Environment Variables

You can override these in `.env` file:

```bash
DATABASE_URL=jdbc:postgresql://your-host:5432/your-db
DB_USERNAME=your-username
DB_PASSWORD=your-password
```

---

## Benefits

✅ **Data Persistence** - Survives application restarts  
✅ **JSONB Support** - Native JSON querying for AI memory  
✅ **Production-Ready** - Same database for dev and prod  
✅ **Historical Analysis** - Unlimited recommendation history  

---

## Database Management

### Connect to Database
```bash
# Via Docker
docker exec -it demospring-postgres psql -U demospring -d demospring

# Via local psql (if installed)
psql -h localhost -U demospring -d demospring
```

### Useful Queries
```sql
-- View recommendations
SELECT * FROM recommendation_history ORDER BY timestamp DESC LIMIT 10;

-- Check for duplicates
SELECT timestamp, signal, COUNT(*) 
FROM recommendation_history 
GROUP BY timestamp, signal 
HAVING COUNT(*) > 1;

-- AI Memory analysis
SELECT 
    jsonb_array_elements_text(ai_memory) as memory_point,
    COUNT(*) 
FROM recommendation_history 
WHERE ai_memory IS NOT NULL
GROUP BY memory_point
ORDER BY count DESC;
```

### Docker Commands
```bash
# Start database
docker-compose up -d

# Stop (keeps data)
docker-compose stop

# Restart
docker-compose restart

# View logs
docker-compose logs -f postgres

# Stop and WIPE data
docker-compose down -v
```

---

## Switching to H2 (If Needed)

If you want to use H2 in-memory database temporarily:

**1. Update application.properties:**
```properties
# Comment out PostgreSQL settings
#spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:5432/demospring}
#spring.datasource.driver-class-name=org.postgresql.Driver
#spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect

# No configuration needed - H2 is automatic
```

**2. Restart application:**
```bash
mvn spring-boot:run
```

⚠️ **Warning:** H2 data is lost on restart!

---

## Troubleshooting

### Can't connect to PostgreSQL

```bash
# Check if running
docker ps | grep postgres

# Check logs
docker-compose logs postgres

# Restart
docker-compose restart
```

### Port 5432 already in use

```bash
# Check what's using it
lsof -i :5432

# Stop local PostgreSQL if installed
brew services stop postgresql
```

### Reset database

```bash
# WARNING: This deletes all data!
docker-compose down -v
docker-compose up -d
```

---

## Files

- `application.properties` - PostgreSQL configuration (default)
- `docker-compose.yml` - PostgreSQL container setup
- `start-with-postgres.sh` - Convenience startup script
- `verify-no-duplicates.sql` - Database verification queries

---

## Summary

PostgreSQL is now the **default database** - no profile needed!

**Start commands:**
```bash
# Start everything
docker-compose up -d
./start-with-postgres.sh

# Or just
docker-compose up -d
mvn spring-boot:run
```

Your AI trading bot now has persistent memory! 🧠💾
