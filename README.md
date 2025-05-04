# VoucherConnect

**VoucherConnect** is a **high-concurrency** voucher system designed to support merchant-issued promotions and **large-scale, time-sensitive voucher claiming by users**. The system is built to ensure **consistency, reliability, and performance under extreme traffic surges**.

---

## 🚀 Key Features

- **Voucher Publishing & Real-Time Claiming**  
  Merchants can publish limited-quantity vouchers, and users compete in real time to claim them.

- **Stateless Authentication**  
  - Implemented using **JWT** and **Redis**  
  - Login state is refreshed via interceptors  
  - User context is managed using **ThreadLocal**

- **Reliable Voucher Claim Pipeline**  
  - Asynchronous claim processing using **Kafka** and **local message tables**  
  - Ensured atomic stock deduction and idempotency with **Redis Lua scripts**

- **Scheduled Compensation for Fault Recovery**  
  - Background tasks periodically retry failed Kafka sends and database writes  
  - Guarantees **eventual consistency** for successful claims

- **Resilient Caching Strategies**  
  - **Penetration Prevention**: Used **Bloom filters** and **null caching** to block invalid requests  
  - **Breakdown Handling**:  
    - For **hot keys**: applied **logical expiration** with asynchronous rebuilding  
    - For **normal keys**: used **Redisson** distributed locks to prevent cache stampede  
  - **Avalanche Mitigation**: Introduced **randomized TTLs** to stagger cache expirations

- **Traffic Control & Protection**  
  - Configured **Nginx** as a centralized gateway with IP-based rate limiting  
  - Implemented a **custom sliding window limiter** using **Redis** and **Lua scripts**  
  - Combined multi-layer protection to defend against traffic spikes and malicious access

---

## 🛠️ Tech Stack

- **Frontend**: Vue  
- **Backend Language & Framework**: Java, Spring Boot  
- **Database**: MySQL  
- **Caching**: Redis  
- **Distributed Locking & Atomicity**: Redisson, Redis Lua scripts  
- **Asynchronous Messaging**: Kafka, local message table  
- **Scheduled Tasks**: Spring Scheduler (for retry and compensation) 
- **Authentication**: JWT, Redis, ThreadLocal  
- **Traffic Management**: Nginx, Redis, Lua (custom sliding window rate limiter)
- **Others**: Bloom filters

