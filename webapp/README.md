# Web frontend

The dashboard uses React 19.3, Vite, and the stable React Compiler.

## Commands

- `npm start` starts Vite at [http://localhost:5173](http://localhost:5173) and proxies `/api` to Spring Boot on port 8080.
- `npm test` runs the Vitest suite once.
- `npm run test:watch` runs tests in watch mode.
- `npm run build` creates the production bundle in `build/` for Maven to package into the Spring Boot application.

The full application can be verified from the repository root with `mvn clean verify` using Java 21.
