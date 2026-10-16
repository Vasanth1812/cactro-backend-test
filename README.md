# Backend Engineer Assessment - Cactro

A production-ready RESTful API backend service developed with **Java 21**, **Spring Boot 3.4.4**, **PostgreSQL / H2**, and **Docker**.

---

## 🚀 Live Deployment & Documentation
* **Live Service URL:** `https://<your-render-service>.onrender.com`
* **Interactive Swagger UI:** `https://<your-render-service>.onrender.com/swagger-ui/html` (or `http://localhost:8080/swagger-ui/index.html` locally)
* **API Documentation (JSON):** `/v3/api-docs`
* **Health Check Endpoint:** `/api/v1/health`

---

## 🛠️ Tech Stack & Architecture
* **Language:** Java 21 (LTS)
* **Framework:** Spring Boot 3.4.4 (Spring Web, Spring Data JPA, Spring Validation)
* **Databases:**
  * **Development / Testing:** H2 In-Memory Database (zero configuration needed)
  * **Production:** Neon Serverless PostgreSQL
* **API Documentation:** Springdoc OpenAPI 3 / Swagger UI
* **Deployment:** Docker & Render Cloud
* **Architecture Pattern:** Clean Layered Architecture (`Controller` -> `Service` -> `Repository` -> `DTO / Entity`)

---

## 📋 API Endpoints Summary

| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/health` | Service health status check | No |
| `GET` | `/swagger-ui/index.html` | Interactive Swagger API docs | No |
| *(Additional endpoints will be added during Sunday's test)* | | | |

---

## ⚙️ Local Setup & Run

### Prerequisites
* Java 21 (JDK 21)
* Maven 3.9+ (or use the included `./mvnw`)

### 1. Clone the repository
```bash
git clone <repository-url>
cd backend-test
```

### 2. Run with H2 In-Memory Database (Development)
The project defaults to the `dev` profile with an in-memory database:
```bash
./mvnw.cmd spring-boot:run
```
* The API will start on: `http://localhost:8080`
* H2 Web Console: `http://localhost:8080/h2-console`
  * JDBC URL: `jdbc:h2:mem:cactro_dev`
  * Username: `sa`
  * Password: *(leave blank)*

### 3. Run with Neon / PostgreSQL (Production Profile)
Set the environment variables or pass them as system properties:
```bash
SPRING_PROFILES_ACTIVE=prod \
SPRING_DATASOURCE_URL="jdbc:postgresql://<neon-host>/<db-name>?sslmode=require" \
SPRING_DATASOURCE_USERNAME="<username>" \
SPRING_DATASOURCE_PASSWORD="<password>" \
./mvnw spring-boot:run
```

---

## 🐳 Docker Build & Run
```bash
# Build Docker image
docker build -t cactro-backend-app .

# Run container
docker run -p 8080:8080 cactro-backend-app
```

---

## 🌐 Deploying to Render with Neon PostgreSQL
1. Create a free PostgreSQL database on **[Neon.tech](https://neon.tech)** and copy the JDBC connection URL.
2. In **[Render](https://render.com)**, create a new **Web Service** and connect this GitHub repository.
3. Configure settings:
   * **Environment:** `Docker` (Render automatically detects the `Dockerfile`).
   * **Health Check Path:** `/api/v1/health`
4. Add the following **Environment Variables** in Render:
   * `SPRING_PROFILES_ACTIVE` = `prod`
   * `SPRING_DATASOURCE_URL` = `<your-neon-jdbc-url>`
   * `SPRING_DATASOURCE_USERNAME` = `<your-neon-username>`
   * `SPRING_DATASOURCE_PASSWORD` = `<your-neon-password>`
5. Click **Deploy Web Service**.
