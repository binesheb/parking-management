import { createHash, randomBytes, timingSafeEqual } from 'node:crypto';

const hash = (value) => createHash('sha256').update(value).digest('hex');
const safeEqual = (a, b) => {
  const left = Buffer.from(String(a ?? ''));
  const right = Buffer.from(String(b ?? ''));
  return left.length === right.length && timingSafeEqual(left, right);
};
const bearer = (request) => {
  const value = String(request.headers.authorization ?? '');
  return value.startsWith('Bearer ') ? value.slice(7) : '';
};
const validId = (value) => typeof value === 'string' && /^[A-Za-z0-9_-]{3,80}$/.test(value);
const now = () => Date.now();

export function registerSensorRoutes(app, db, { bootstrapToken = process.env.SENSOR_BOOTSTRAP_TOKEN ?? '', offlineAfterMs = 120_000 } = {}) {
  db.exec(`
    CREATE TABLE IF NOT EXISTS sensor_devices (
      device_id TEXT PRIMARY KEY,
      area_id TEXT NOT NULL,
      slot_id TEXT NOT NULL,
      token_hash TEXT NOT NULL,
      firmware_version TEXT NOT NULL DEFAULT '',
      occupancy TEXT NOT NULL DEFAULT 'UNKNOWN',
      last_sequence INTEGER NOT NULL DEFAULT 0,
      last_seen_at INTEGER,
      uptime_seconds INTEGER,
      wifi_rssi INTEGER,
      camera_status TEXT NOT NULL DEFAULT 'UNKNOWN',
      detector_status TEXT NOT NULL DEFAULT 'UNKNOWN',
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS sensor_events (
      event_id TEXT PRIMARY KEY,
      device_id TEXT NOT NULL,
      area_id TEXT NOT NULL,
      slot_id TEXT NOT NULL,
      sequence INTEGER NOT NULL,
      occupancy TEXT NOT NULL CHECK (occupancy IN ('OCCUPIED','AVAILABLE','UNKNOWN')),
      confidence REAL NOT NULL,
      captured_at INTEGER NOT NULL,
      received_at INTEGER NOT NULL,
      FOREIGN KEY(device_id) REFERENCES sensor_devices(device_id)
    );
    CREATE INDEX IF NOT EXISTS idx_sensor_events_slot_time ON sensor_events(area_id, slot_id, captured_at);
  `);

  function authenticate(request) {
    const token = bearer(request);
    if (!token) return null;
    const device = db.prepare('SELECT * FROM sensor_devices WHERE token_hash = ?').get(hash(token));
    return device ?? null;
  }

  app.post('/api/v1/sensors/register', async (request, reply) => {
    if (!bootstrapToken || !safeEqual(bearer(request), bootstrapToken)) {
      return reply.code(401).send({ error: 'registration_unauthorized' });
    }
    const body = request.body ?? {};
    const deviceId = body.deviceId;
    const areaId = body.areaId;
    const slotId = body.slotId;
    const firmwareVersion = String(body.firmwareVersion ?? '').slice(0, 40);
    if (![deviceId, areaId, slotId].every(validId)) return reply.code(400).send({ error: 'invalid_registration' });
    if (db.prepare('SELECT device_id FROM sensor_devices WHERE device_id = ?').get(deviceId)) {
      return reply.code(409).send({ error: 'device_already_registered' });
    }
    const token = randomBytes(32).toString('hex');
    const timestamp = now();
    db.prepare(`INSERT INTO sensor_devices
      (device_id, area_id, slot_id, token_hash, firmware_version, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?)`)
      .run(deviceId, areaId, slotId, hash(token), firmwareVersion, timestamp, timestamp);
    return reply.code(201).send({ deviceId, areaId, slotId, deviceToken: token, tokenFormat: 'bearer' });
  });

  app.post('/api/v1/sensors/events', async (request, reply) => {
    const device = authenticate(request);
    if (!device) return reply.code(401).send({ error: 'device_unauthorized' });
    const body = request.body ?? {};
    const eventId = body.eventId;
    const sequence = Number(body.sequence);
    const occupancy = body.occupancy;
    const confidence = Number(body.confidence);
    const capturedAt = typeof body.capturedAt === 'number' ? body.capturedAt : Date.parse(body.capturedAt);
    if (typeof eventId !== 'string' || eventId.length < 8 || eventId.length > 128 ||
        !Number.isSafeInteger(sequence) || sequence < 1 ||
        !['OCCUPIED', 'AVAILABLE', 'UNKNOWN'].includes(occupancy) ||
        !Number.isFinite(confidence) || confidence < 0 || confidence > 1 ||
        !Number.isFinite(capturedAt)) {
      return reply.code(400).send({ error: 'invalid_sensor_event' });
    }
    const duplicate = db.prepare('SELECT event_id FROM sensor_events WHERE event_id = ?').get(eventId);
    if (duplicate) return reply.code(200).send({ eventId, duplicate: true });
    if (sequence <= Number(device.last_sequence)) {
      return reply.code(409).send({ error: 'stale_sequence', lastSequence: Number(device.last_sequence) });
    }
    const receivedAt = now();
    db.prepare(`INSERT INTO sensor_events
      (event_id, device_id, area_id, slot_id, sequence, occupancy, confidence, captured_at, received_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`)
      .run(eventId, device.device_id, device.area_id, device.slot_id, sequence, occupancy, confidence, capturedAt, receivedAt);
    db.prepare(`UPDATE sensor_devices SET occupancy = ?, last_sequence = ?, last_seen_at = ?,
      firmware_version = ?, updated_at = ? WHERE device_id = ?`)
      .run(occupancy, sequence, receivedAt, String(body.firmwareVersion ?? device.firmware_version).slice(0, 40), receivedAt, device.device_id);
    return reply.code(201).send({ eventId, sequence, occupancy, slotId: device.slot_id, accepted: true });
  });

  app.post('/api/v1/sensors/heartbeat', async (request, reply) => {
    const device = authenticate(request);
    if (!device) return reply.code(401).send({ error: 'device_unauthorized' });
    const body = request.body ?? {};
    const uptime = Number(body.uptimeSeconds);
    const rssi = Number(body.wifiRssi);
    if (!Number.isFinite(uptime) || uptime < 0 || (body.wifiRssi !== undefined && (!Number.isInteger(rssi) || rssi < -127 || rssi > 0))) {
      return reply.code(400).send({ error: 'invalid_heartbeat' });
    }
    const receivedAt = now();
    db.prepare(`UPDATE sensor_devices SET last_seen_at = ?, uptime_seconds = ?, wifi_rssi = ?,
      camera_status = ?, detector_status = ?, firmware_version = ?, updated_at = ? WHERE device_id = ?`)
      .run(receivedAt, uptime, body.wifiRssi === undefined ? null : rssi,
        String(body.cameraStatus ?? 'UNKNOWN').slice(0, 32),
        String(body.detectorStatus ?? 'UNKNOWN').slice(0, 32),
        String(body.firmwareVersion ?? device.firmware_version).slice(0, 40),
        receivedAt, device.device_id);
    return reply.send({ ok: true, serverTime: receivedAt });
  });

  app.get('/api/v1/sensors/slots', async () => {
    const devices = db.prepare(`SELECT device_id AS deviceId, area_id AS areaId, slot_id AS slotId,
      occupancy, firmware_version AS firmwareVersion, last_seen_at AS lastSeenAt,
      uptime_seconds AS uptimeSeconds, wifi_rssi AS wifiRssi, camera_status AS cameraStatus,
      detector_status AS detectorStatus FROM sensor_devices ORDER BY area_id, slot_id`).all();
    const timestamp = now();
    return devices.map((device) => {
      const online = Number.isFinite(device.lastSeenAt) && timestamp - Number(device.lastSeenAt) <= offlineAfterMs;
      return { ...device, online, status: online ? device.occupancy : 'UNKNOWN' };
    });
  });

  app.get('/api/v1/sensors/devices/:deviceId/events', async (request, reply) => {
    const device = authenticate(request);
    if (!device || device.device_id !== request.params.deviceId) return reply.code(401).send({ error: 'device_unauthorized' });
    const limit = Math.min(Math.max(Number(request.query?.limit ?? 50), 1), 500);
    return db.prepare(`SELECT event_id AS eventId, sequence, occupancy, confidence,
      captured_at AS capturedAt, received_at AS receivedAt FROM sensor_events
      WHERE device_id = ? ORDER BY sequence DESC LIMIT ?`).all(device.device_id, limit);
  });
}
