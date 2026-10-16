# CineBook — MS SQL Server Setup Guide

## 1. Install SQL Server (if not already installed)
1. Download **SQL Server 2022 Express** (free): https://www.microsoft.com/en-us/sql-server/sql-server-downloads
2. Run the installer → choose **Basic** installation → finish. Note the **instance name** shown at the end (default is usually `SQLEXPRESS` or a default/unnamed instance on port `1433`).
3. Download and install **SQL Server Management Studio (SSMS)** separately: https://learn.microsoft.com/en-us/sql/ssms/download-sql-server-management-studio-ssms

## 2. Enable SQL Server Authentication (so `application.properties` can log in with a username/password)
1. Open **SSMS** → connect using **Windows Authentication** first (your Windows login).
2. Right-click the server (top of the tree) → **Properties** → **Security** → select **SQL Server and Windows Authentication mode** → OK.
3. Restart the SQL Server service: open **SQL Server Configuration Manager** → **SQL Server Services** → right-click your instance → **Restart**.
4. In SSMS, expand **Security → Logins → sa** → right-click → **Properties**:
   - Set a password (this must match `spring.datasource.password` in `application.properties`).
   - **Status** tab → set **Login** to **Enabled**.

## 3. Create the database
In SSMS, open a **New Query** window and run:
```sql
CREATE DATABASE cinebook_db;
```

## 4. Enable TCP/IP (needed for the app to connect over `localhost:1433`)
1. Open **SQL Server Configuration Manager** → **SQL Server Network Configuration** → **Protocols for <your instance>**.
2. Right-click **TCP/IP** → **Enable** (if not already).
3. Restart the SQL Server service again.

## 5. Update `application.properties`
Located at `src/main/resources/application.properties`. Update these lines to match your setup:
```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=cinebook_db;encrypt=true;trustServerCertificate=true
spring.datasource.username=sa
spring.datasource.password=YourStrong@Passw0rd
```
- If you installed a **named instance** (e.g. `SQLEXPRESS`), either:
  - Enable the **SQL Server Browser** service and use `jdbc:sqlserver://localhost\\SQLEXPRESS;databaseName=cinebook_db;...`, or
  - Find the exact port in SQL Server Configuration Manager → TCP/IP → IP Addresses → `IPAll` → `TCP Port`, and use that port number instead of `1433`.

## 6. Run the app
```bash
mvn spring-boot:run
```
On first run, Hibernate (`spring.jpa.hibernate.ddl-auto=update`) automatically creates every table for you inside `cinebook_db` — no manual DDL needed to just run the app. Demo accounts for all 7 roles are seeded automatically the first time (see below).

## 7. Demo login accounts (auto-seeded)
| Role | Email | Password |
|---|---|---|
| System Admin | admin@cinebook.com | Admin@123 |
| Cashier | cashier@cinebook.com | Cashier@123 |
| Theatre Manager | theatremanager@cinebook.com | Theatre@123 |
| Cinema Owner | owner@cinebook.com | Owner@123 |
| Customer Support | support@cinebook.com | Support@123 |
| Marketing Manager | marketing@cinebook.com | Marketing@123 |

Customers self-register via **Sign Up** (with email verification).

## 8. IT2140 (DDD) Part 02 submission
The `database/CineBook_IT2140_Part02.sql` file has everything the assignment asks for, ready to run manually in SSMS if you want hand-written DDL for the submission screenshots:
- Part A: EER → relational mapping comments
- Part B: `CREATE TABLE` DDL with PK/FK/CHECK constraints
- Part C: 5+ sample rows per table
- Part D: 5 SQL queries (SELECT, JOIN, aggregation, GROUP BY/HAVING, subquery)
- Part E: `sp_GetCustomerBookingHistory` stored procedure
- Part F: `trg_Payment_AfterRefund` trigger

Open it in SSMS connected to `cinebook_db`, run it top to bottom, and take your screenshots from there for the report.
