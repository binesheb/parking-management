import test from 'node:test';
import assert from 'node:assert/strict';
import Fastify from 'fastify';
import { DatabaseSync } from 'node:sqlite';
import { registerSensorRoutes } from '../src/sensors.js';

test('sensor registration, event idempotency, sequence validation, heartbeat and offline state', async (t) => {
  const app = Fastify();
  const db = new DatabaseSync(':memory:');
  registerSensorRoutes(app, db, { bootstrapToken: 'test-bootstrap-token', offlineAfterMs: 60000 });
  await app.ready();
  t.after(async () => { await app.close(); db.close(); });

  const unauthorized = await app.inject({ method: 'POST', url: '/api/v1/sensors/register', payload: { deviceId: 'SP-TEST-001', areaId: 'AREA-01', slotId: 'S001' } });
  assert.equal(unauthorized.statusCode, 401);

  const registered = await app.inject({
    method: 'POST', url: '/api/v1/sensors/register',
    headers: { authorization: 'Bearer test-bootstrap-token' },
    payload: { deviceId: 'SP-TEST-001', areaId: 'AREA-01', slotId: 'S001', firmwareVersion: '0.1.0' }
  });
  assert.equal(registered.statusCode, 201);
  const token = registered.json().deviceToken;

  const event = { eventId: 'SP-TEST-001-000001', sequence: 1, occupancy: 'OCCUPIED', confidence: 0.92, capturedAt: Date.now(), firmwareVersion: '0.1.0' };
  const accepted = await app.inject({ method: 'POST', url: '/api/v1/sensors/events', headers: { authorization: `Bearer ${token}` }, payload: event });
  assert.equal(accepted.statusCode, 201);
  const duplicate = await app.inject({ method: 'POST', url: '/api/v1/sensors/events', headers: { authorization: `Bearer ${token}` }, payload: event });
  assert.equal(duplicate.statusCode, 200);
  assert.equal(duplicate.json().duplicate, true);

  const stale = await app.inject({ method: 'POST', url: '/api/v1/sensors/events', headers: { authorization: `Bearer ${token}` }, payload: { ...event, eventId: 'SP-TEST-001-000000', sequence: 1 } });
  assert.equal(stale.statusCode, 409);

  const heartbeat = await app.inject({
    method: 'POST', url: '/api/v1/sensors/heartbeat',
    headers: { authorization: `Bearer ${token}` },
    payload: { uptimeSeconds: 120, wifiRssi: -54, cameraStatus: 'OK', detectorStatus: 'READY', firmwareVersion: '0.1.0' }
  });
  assert.equal(heartbeat.statusCode, 200);

  const slots = await app.inject({ method: 'GET', url: '/api/v1/sensors/slots' });
  assert.equal(slots.statusCode, 200);
  assert.equal(slots.json()[0].status, 'OCCUPIED');
  assert.equal(slots.json()[0].online, true);

  db.prepare('UPDATE sensor_devices SET last_seen_at = ? WHERE device_id = ?').run(Date.now() - 60001, 'SP-TEST-001');
  const staleSlots = await app.inject({ method: 'GET', url: '/api/v1/sensors/slots' });
  assert.equal(staleSlots.json()[0].status, 'UNKNOWN');
  assert.equal(staleSlots.json()[0].online, false);

  const badAuth = await app.inject({ method: 'POST', url: '/api/v1/sensors/heartbeat', headers: { authorization: 'Bearer wrong' }, payload: { uptimeSeconds: 1 } });
  assert.equal(badAuth.statusCode, 401);
});
