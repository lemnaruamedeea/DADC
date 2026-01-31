const express = require('express');
const cors = require('cors');
const mysql = require('mysql2/promise');
const { MongoClient } = require('mongodb');
const { v4: uuidv4 } = require('uuid');
const os = require('os');
const http = require('http');

const MYSQL_HOST = process.env.MYSQL_HOST || 'localhost';
const MYSQL_USER = process.env.MYSQL_USER || 'root';
const MYSQL_PASSWORD = process.env.MYSQL_PASSWORD || '';
const MYSQL_DB = process.env.MYSQL_DB || 'bmpdb';
const MONGO_URL = process.env.MONGO_URL || 'mongodb://localhost:27017';
const MONGO_DB = process.env.MONGO_DB || 'snmpdb';
const PORT = parseInt(process.env.PORT || '3000', 10);
const SNMP_COLLECT_INTERVAL_MS = parseInt(process.env.SNMP_COLLECT_INTERVAL_MS || '60000', 10);

const app = express();
app.use(cors());
app.use(express.json({ limit: '1mb' }));

let mysqlPool;
let mongoClient;
let mongoDb;

async function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

const MYSQL_SOCKET = process.env.MYSQL_SOCKET || '/var/run/mysqld/mysqld.sock';
const useSocket = MYSQL_HOST === 'localhost' || MYSQL_HOST === '127.0.0.1';

async function initMySQL() {
  const opts = {
    user: MYSQL_USER,
    database: MYSQL_DB,
    waitForConnections: true,
    connectionLimit: 10,
  };
  if (MYSQL_PASSWORD) opts.password = MYSQL_PASSWORD;
  if (useSocket) {
    opts.socketPath = MYSQL_SOCKET;
  } else {
    opts.host = MYSQL_HOST;
  }
  for (let i = 0; i < 30; i++) {
    try {
      mysqlPool = mysql.createPool(opts);
      const conn = await mysqlPool.getConnection();
      await conn.query(`
        CREATE TABLE IF NOT EXISTS pictures (
          id VARCHAR(64) PRIMARY KEY,
          request_id VARCHAR(64),
          zoom_percent INT,
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          data LONGBLOB
        )
      `);
      conn.release();
      console.log('[C06] MySQL connected, table pictures ready');
      return;
    } catch (e) {
      console.warn('[C06] MySQL not ready, retry', i + 1, e.message);
      await sleep(2000);
    }
  }
  throw new Error('MySQL init failed');
}

async function initMongo() {
  for (let i = 0; i < 30; i++) {
    try {
      mongoClient = new MongoClient(MONGO_URL);
      await mongoClient.connect();
      mongoDb = mongoClient.db(MONGO_DB);
      await mongoDb.collection('snmp_values').createIndex({ node: 1, timestamp: -1 }).catch(() => {});
      console.log('[C06] MongoDB connected');
      return;
    } catch (e) {
      console.warn('[C06] MongoDB not ready, retry', i + 1, e.message);
      await sleep(2000);
    }
  }
  throw new Error('MongoDB init failed');
}

app.get('/api/snmp', async (req, res) => {
  try {
    const col = mongoDb.collection('snmp_values');
    const docs = await col.find({}).sort({ timestamp: -1 }).limit(100).toArray();
    res.json(docs);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.get('/api/bmp/:id', async (req, res) => {
  try {
    const [rows] = await mysqlPool.execute('SELECT id, data FROM pictures WHERE id = ?', [req.params.id]);
    if (!rows || rows.length === 0) {
      console.log('[C06] GET /api/bmp/' + req.params.id + ' -> 404');
      return res.status(404).send('Not found');
    }
    const blob = rows[0].data;
    console.log('[C06] GET /api/bmp/' + req.params.id + ' -> 200, ' + (blob ? blob.length : 0) + ' bytes');
    res.set('Content-Type', 'image/bmp');
    res.send(blob);
  } catch (e) {
    console.error('[C06] GET /api/bmp error:', e.message);
    res.status(500).json({ error: e.message });
  }
});

app.post('/api/snmp', async (req, res) => {
  try {
    const { node, osName, cpuUsage, ramUsage } = req.body;
    if (!node) return res.status(400).json({ error: 'Missing node' });
    await mongoDb.collection('snmp_values').insertOne({
      node,
      osName: osName || 'unknown',
      cpuUsage: typeof cpuUsage === 'number' ? cpuUsage : parseFloat(cpuUsage) || 0,
      ramUsage: typeof ramUsage === 'number' ? ramUsage : parseFloat(ramUsage) || 0,
      timestamp: new Date(),
    });
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.post('/api/bmp', express.raw({ type: ['application/octet-stream', 'image/bmp', 'application/octet-stream; charset=binary'], limit: '50mb' }), async (req, res) => {
  let data = req.body;
  const requestId = req.headers['x-request-id'] || uuidv4();
  const zoomPercent = parseInt(req.headers['x-zoom-percent'] || '100', 10);
  const id = req.headers['x-picture-id'] || uuidv4();
  console.log('[C06] POST /api/bmp requestId=' + requestId + ' pictureId=' + id + ' zoom%=' + zoomPercent + ' bodyType=' + (typeof data));
  if (typeof data === 'string') {
    data = Buffer.from(data, 'base64');
  } else {
    console.error('[C06] POST /api/bmp reject: expected binary/base64, got ' + typeof data);
    return res.status(400).json({ error: 'Expected binary or base64 body' });
  }
  const size = data && data.length ? data.length : 0;
  try {
    await mysqlPool.execute(
      'INSERT INTO pictures (id, request_id, zoom_percent, data) VALUES (?, ?, ?, ?)',
      [id, requestId, zoomPercent, data]
    );
    const baseUrl = process.env.C06_PUBLIC_URL || `http://localhost:${PORT}`;
    const downloadUrl = `${baseUrl}/api/bmp/${id}`;
    console.log('[C06] POST /api/bmp stored id=' + id + ' size=' + size + ' downloadUrl=' + downloadUrl);
    res.json({ id, requestId, downloadUrl });
  } catch (e) {
    console.error('[C06] POST /api/bmp error:', e.message);
    res.status(500).json({ error: e.message });
  }
});

app.get('/health', (req, res) => res.json({ status: 'ok' }));

app.get('/metrics', (req, res) => {
  const load = os.loadavg();
  const cpus = os.cpus().length;
  const cpuUsage = cpus > 0 && load[0] >= 0 ? Math.min(100, (load[0] / cpus) * 100) : 0;
  const totalMem = os.totalmem();
  const freeMem = os.freemem();
  const ramUsage = totalMem > 0 ? ((totalMem - freeMem) / totalMem) * 100 : 0;
  res.json({
    node: 'c06',
    osName: os.type() + ' ' + os.release() + ' ' + os.arch(),
    cpuUsage: Math.round(cpuUsage * 100) / 100,
    ramUsage: Math.round(ramUsage * 100) / 100,
  });
});

const NODES = [
  { name: 'c01', url: 'http://c01:7000/metrics' },
  { name: 'c02', url: 'http://c02:8080/metrics/metrics' },
  { name: 'c03', url: 'http://c03:8080/c03/metrics' },
  { name: 'c04', url: 'http://c04:8080/c04-rmi/metrics' },
  { name: 'c05', url: 'http://c05:8080/c05-rmi/metrics' },
  { name: 'c06', url: `http://localhost:${PORT}/metrics` },
];

function fetchMetrics(url) {
  return new Promise((resolve) => {
    const req = http.get(url, { timeout: 5000 }, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        try {
          resolve(JSON.parse(data));
        } catch {
          resolve(null);
        }
      });
    });
    req.on('error', () => resolve(null));
    req.on('timeout', () => { req.destroy(); resolve(null); });
  });
}

async function collectSnmpFromAllNodes() {
  if (!mongoDb) return;
  for (const node of NODES) {
    try {
      const metrics = await fetchMetrics(node.url);
      if (metrics && metrics.node) {
        await mongoDb.collection('snmp_values').insertOne({
          node: metrics.node,
          osName: metrics.osName || 'unknown',
          cpuUsage: typeof metrics.cpuUsage === 'number' ? metrics.cpuUsage : parseFloat(metrics.cpuUsage) || 0,
          ramUsage: typeof metrics.ramUsage === 'number' ? metrics.ramUsage : parseFloat(metrics.ramUsage) || 0,
          timestamp: new Date(),
        });
      }
    } catch (e) {
      console.warn('[C06] SNMP collect', node.name, e.message);
    }
  }
}

function startSnmpCollector() {
  setInterval(() => {
    collectSnmpFromAllNodes().then(() => {
      console.log('[C06] SNMP collected from all nodes');
    }).catch((e) => console.warn('[C06] SNMP collect error:', e.message));
  }, SNMP_COLLECT_INTERVAL_MS);
  collectSnmpFromAllNodes().catch(() => {});
}

async function main() {
  await initMySQL();
  await initMongo();
  app.listen(PORT, '0.0.0.0', () => {
    console.log(`[C06] listening on ${PORT}; /api/snmp, /api/bmp, /api/bmp/:id, /metrics`);
    startSnmpCollector();
  });
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
