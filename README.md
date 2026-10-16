# MovieBookingSystem (CineBook)

A complete movie ticket booking system built with **Spring Boot** and **MS SQL Server**. It supports multiple user roles (admin, cashier, theatre manager, owner, customer support, marketing manager, and customers) with full booking, payment, notification, and promotion management.

## Tech Stack

- **Java 17+**
- **Spring Boot 4.1.0**
- **Spring MVC + Thymeleaf** (server-side rendering)
- **Spring Data JPA** (ORM)
- **Maven** (build tool)
- **MS SQL Server** (database)

## Prerequisites

- **Java 17 or later** installed and available in your PATH
- **Maven** installed (or use the included Maven Wrapper: `./mvnw`)
- **MS SQL Server 2022 Express** (or compatible edition) installed and running
- **SQL Server Management Studio (SSMS)** (optional, for database setup)

## Quick Start

### 1. Set up the database

Follow the detailed guide in [SQL_SERVER_SETUP.md](SQL_SERVER_SETUP.md):

1. Install SQL Server and SSMS
2. Enable SQL Server Authentication
3. Create the database:
   ```sql
   CREATE DATABASE cinebook_db;
   ```
4. Enable TCP/IP on port 1433
5. Update `src/main/resources/application.properties` with your database credentials

### 2. Run the application

```bash
mvn spring-boot:run
```

On first run, Hibernate (`spring.jpa.hibernate.ddl-auto=update`) automatically creates all tables. Demo accounts for all 7 roles are seeded automatically.

### 3. Access the app

Open your browser and go to: [http://localhost:8080](http://localhost:8080)

## Demo Login Accounts

| Role | Email | Password |
|---|---|---|
| System Admin | admin@cinebook.com | Admin@123 |
| Cashier | cashier@cinebook.com | Cashier@123 |
| Theatre Manager | theatremanager@cinebook.com | Theatre@123 |
| Cinema Owner | owner@cinebook.com | Owner@123 |
| Customer Support | support@cinebook.com | Support@123 |
| Marketing Manager | marketing@cinebook.com | Marketing@123 |

Customers can self-register via the **Sign Up** page (with email verification).

## Project Structure

```
src/main/java/com/moviebooking/moviebookingsystem/
├── controller/          # MVC controllers (admin, booking, cashier, etc.)
├── model/               # JPA entities (Movie, Booking, Payment, etc.)
├── repository/          # Spring Data JPA repositories
└── service/             # Business logic services
```

## Database Setup for Submission (IT2140 Part 02)

The `database/CineBook_IT2140_Part02.sql` file contains hand-written DDL and sample data for the assignment:

- **Part A**: EER → relational mapping comments
- **Part B**: `CREATE TABLE` DDL with PK/FK/CHECK constraints
- **Part C**: 5+ sample rows per table
- **Part D**: 5 SQL queries (SELECT, JOIN, aggregation, GROUP BY/HAVING, subquery)
- **Part E**: `sp_GetCustomerBookingHistory` stored procedure
- **Part F**: `trg_Payment_AfterRefund` trigger

Open it in SSMS connected to `cinebook_db`, run it top to bottom, and take screenshots from there for the report.

## Build & Run Commands

```bash
# Compile
mvn compile

# Run the application
mvn spring-boot:run

# Package as JAR
mvn package -DskipTests

# Run tests (if any)
mvn test
```

## Configuration

The main configuration file is `src/main/resources/application.properties`. See [SQL_SERVER_SETUP.md](SQL_SERVER_SETUP.md) for the required database connection settings.

## Getting Help

- **Spring Boot Documentation**: https://spring.io/projects/spring-boot
- **Spring Data JPA**: https://spring.io/projects/spring-data-jpa
- **Thymeleaf**: https://www.thymeleaf.org/
- **Maven Guides**: https://maven.apache.org/guides/index.html

## License

This project is for educational purposes (IT2140 DDD assignment).