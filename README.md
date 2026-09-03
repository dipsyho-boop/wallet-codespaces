# wallet-codespaces
wallet assignment

# Wallet Service API

A Spring Boot-based digital wallet microservice with Redis caching, PostgreSQL persistence, pessimistic locking for deadlock prevention, and Docker deployment.

---

## Technical Architecture

* **Framework:** Java 17, Spring Boot 3
* **Database:** PostgreSQL (Transactional database with composite primary key support)
* **Cache:** Redis (`@Cacheable` / `@CacheEvict` for fast balance lookup)
* **Concurrency:** Pessimistic Locking (`SELECT ... FOR UPDATE`) with dictionary order locking to avoid deadlocks
* **Containerization:** Docker & Docker Compose

---

## How to Run

### 1. Start Services via Docker
Ensure Docker Desktop is running, then execute:
```bash
docker compose up -d --build

### 2. Verify Services
Check running containers:

docker compose ps

API Endpoints & Testing
All endpoints accept and return JSON format (⁠Content-Type: application/json⁠).

1. Create Account
curl -X POST http://localhost:8080/api/create-account -H "Content-Type: application/json" -d '{"amount": 1000.00}'

2. Check Balance
curl -X POST http://localhost:8080/api/balance -H "Content-Type: application/json" -d '{"Account_ID": "000001"}'

3. Deposit Funds
curl -X POST http://localhost:8080/api/deposit -H "Content-Type: application/json" -d '{"Account_ID": "000001", "amount": 100.00}'

4. Withdraw Funds
curl -X POST http://localhost:8080/api/withdraw -H "Content-Type: application/json" -d '{"Account_ID": "000001", "amount": 50.00}'

5. Transfer Funds
curl -X POST http://localhost:8080/api/transfer -H "Content-Type: application/json" -d '{"from_Account_ID": "000001", "to_Account_ID": "000002", "amount": 20.00}'

Core Features & Safety Guarantees
 Deadlock Prevention: During transfers, accounts are locked strictly in lexicographical order (⁠fromAcc.compareTo(toAcc)⁠).
 Cache Invalidation: Any mutation operation (⁠deposit⁠, ⁠withdraw⁠, ⁠transfer⁠) automatically evicts the balance cache for affected accounts.
 Data Integrity: All financial logic runs inside ⁠@Transactional⁠ scope with explicit rollback on error.