#!/bin/bash

# Start ETH Trading Bot with PostgreSQL
# This script starts PostgreSQL and the Spring Boot app

set -e

echo "🚀 Starting ETH Trading Bot with PostgreSQL..."
echo ""

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker Desktop."
    exit 1
fi

# Start PostgreSQL if not running
if ! docker ps | grep -q demospring-postgres; then
    echo "📦 Starting PostgreSQL..."
    docker-compose up -d
    
    echo "⏳ Waiting for PostgreSQL to be ready..."
    sleep 5
    
    # Wait for health check
    until docker exec demospring-postgres pg_isready -U demospring > /dev/null 2>&1; do
        echo "   Still waiting for PostgreSQL..."
        sleep 2
    done
    
    echo "✅ PostgreSQL is ready!"
else
    echo "✅ PostgreSQL is already running"
fi

echo ""
echo "🔗 Database Connection:"
echo "   Host:     localhost:5432"
echo "   Database: demospring"
echo "   Username: demospring"
echo ""

# Check if we should compile first
if [ "$1" == "--build" ]; then
    echo "🔨 Compiling application..."
    mvn clean compile
    echo ""
fi

# Start Spring Boot (PostgreSQL is now the default)
echo "🌱 Starting Spring Boot application..."
echo "   Database: PostgreSQL (default)"
echo "   Data will persist between restarts!"
echo ""

mvn spring-boot:run

# Note: When you stop the app (Ctrl+C), PostgreSQL keeps running
# To stop PostgreSQL: docker-compose stop
# To stop and wipe data: docker-compose down -v
