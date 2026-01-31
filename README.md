# BMP Zoom – 6-Container Architecture (DAD)

Six Docker containers (from `critoma/linux-u20-dev-security-ism`) for zooming in/out a BMP image: **Front-end (React)** → **C01 (Javalin + JMS)** → **C02 (TomEE 10 + JMS Broker)** → **C03 (MDB + RMI Client)** → **C04/C05 (RMI Servers)** → **C06 (Node.js + MySQL + MongoDB)**.

## Architecture

- **`architecture/bmp-zoom-architecture.drawio`** – Open in [draw.io](https://app.diagrams.net/) or [diagrams.net](https://www.diagrams.net/). Diagram shows **End-to-End Data Flow (Upload → Process → Store → Notify)** with components, endpoints, JMS topics, and DBs.

### Flow

1. **Front-end (React)** – Upload BMP + zoom % via REST, poll job status, download from C06 when ready. Two pages: **BMP Zoom** (`/`) and **SNMP Monitor** (`/snmp`).
2. **C01** – Javalin REST API; receives upload, publishes binary message to JMS Topic `bmp.topic`; exposes `/job-complete` for C03 callback; exposes `/metrics` for SNMP collector. Does **not** serve the frontend.
3. **C02** – Apache TomEE 10 + JMS Broker (ActiveMQ 5.18); listens on `61616`. Topic `bmp.topic`, Topic `job.done.topic`. Deploys `metrics.war` for SNMP `/metrics`.
4. **C03** – TomEE Plus + EJB MDB (subscription to `bmp.topic`) + RMI client; calls **C04 and C05** for zoom (zoom pics united), stores result in C06 via REST, notifies C01 via `/job-complete`, publishes "job done" to JMS topic `job.done.topic`. Exposes `/metrics`.
5. **C04, C05** – Apache TomEE 10 + RMI servers; expose `ZoomService` for BMP zoom (scale by %) and `/metrics` for SNMP.
6. **C06** – Node.js Express + **MySQL and MongoDB in the same container** (2 DBs); REST:
   - `GET /api/snmp` – SNMP values (collected from all nodes);
   - `GET /api/bmp/:id` – download BMP;
   - `POST /api/bmp` – store BMP (called by C03);
   - `GET /metrics` – local metrics for SNMP collector.
   - **SNMP collector**: periodically fetches OS name, CPU and RAM usage from **all nodes** (C01–C06) via `/metrics` and stores them in MongoDB.

## Prerequisites

- Docker & Docker Compose
- Base image: `critoma/linux-u20-dev-security-ism:latest`

```bash
docker pull critoma/linux-u20-dev-security-ism:latest
```

If you see `lookup auth.docker.io: no such host`, `TLS handshake timeout`, or similar, Docker can't reach Docker Hub. See **`TROUBLESHOOTING.md`** for network/DNS fixes.

## Build & Run

```bash
# Build all images
docker compose build

# Start all 7 containers (6 backend + frontend)
docker compose up -d

# Logs
docker compose logs -f
```

### Ports

| Service | Ports |
|--------|--------|
| **Frontend** (React) | 5173 |
| C01 (API) | 7000 |
| C02 (JMS + TomEE) | 61616 |
| C03 (MDB) | 8083 |
| C04 (RMI) | 8084, 1094 |
| C05 (RMI) | 8085, 1095 |
| C06 (Node) | 3000 |

### Usage

1. **BMP Zoom** – Open **http://localhost:5173**. Choose a BMP, set **Zoom %** (e.g. 50 or 200), click **Upload & zoom**. Wait for “Ready” and use **Download BMP** (link to C06 `GET /api/bmp/:id`).
2. **SNMP Monitor** – Open **http://localhost:5173/snmp** (or click **SNMP Monitor** in the nav). View OS name, CPU and RAM usage for all nodes (c01–c06); data auto-refreshes and can be refreshed manually.

### Frontend environment (build time)

- `VITE_API_BASE` – Base URL for C01 (default: `http://localhost:7000`).
- `VITE_C06_URL` – Base URL for C06 (SNMP API; default: `http://localhost:3000`).

Set in `frontend/Dockerfile` or `.env` when building.

### Test BMP

A sample 64×64 BMP is included: **`test-sample.bmp`** (gradient + border). Use it to verify the flow.

### C06 databases

C06 **has 2 DBs in the same container**: MySQL (BMP BLOB in table `pictures`) and MongoDB (SNMP values in collection `snmp_values`). The Dockerfile installs `mysql-server` and `mongodb-org`; `start.sh` starts both before Node.js. No separate DB containers required.

## Alternative: 8 containers (MySQL + MongoDB separate)

If you prefer dedicated DB containers instead of running MySQL and MongoDB inside C06:

1. Add `mysql` and `mongo` services in `docker-compose.yml`.
2. Set C06 env: `MYSQL_HOST=mysql`, `MONGO_URL=mongodb://mongo:27017`.
3. Use a C06 image/start that skips starting MySQL/Mongo (only `node server.js`).

## Project Layout

```
├── architecture/
│   └── bmp-zoom-architecture.drawio   # draw.io diagram (End-to-End Data Flow)
├── frontend/                           # React (Vite) – BMP Zoom + SNMP Monitor
├── c01-backend/                        # Javalin REST + JMS publisher
├── c02-jms-broker/                     # TomEE 10 + ActiveMQ (JMS Broker)
├── c02-metrics/                        # WAR for C02 /metrics (SNMP)
├── c03-mdb-rmi-client/                 # TomEE + MDB + RMI client
├── c04-rmi-server/                     # TomEE + RMI ZoomService
├── c05-rmi-server/                     # TomEE + RMI ZoomService
├── c06-node-db/                        # Node.js + MySQL + MongoDB
├── zoom-rmi-api/                       # RMI interface (shared)
├── docker-compose.yml
├── TROUBLESHOOTING.md                  # Network/DNS/build issues
└── README.md
```

## Local development (without Docker)

- **Front-end:** `cd frontend && npm install && npm run dev`. Set `VITE_API_BASE` and `VITE_C06_URL` if needed (e.g. in `.env`).
- **C01:** `mvn -f c01-backend/pom.xml exec:java` (or run `Main`); ensure JMS URL → C02.
- **C02–C05:** Run TomEE/ActiveMQ locally or use Docker for JMS/RMI only.
- **C06:** `cd c06-node-db && npm install && node server.js` (MySQL and MongoDB must be running).

## License

MIT.
