import Fastify from 'fastify';
import { mkdirSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { dirname, resolve } from 'node:path';

const dbPath = resolve(process.env.DB_PATH ?? './data/parking.sqlite');
mkdirSync(dirname(dbPath), { recursive: true });
const db = new DatabaseSync(dbPath);
const CAPACITY = Math.max(0, Number(process.env.PARKING_CAPACITY ?? 0));

db.exec(`CREATE TABLE IF NOT EXISTS events (id INTEGER PRIMARY KEY AUTOINCREMENT, event_id TEXT UNIQUE NOT NULL, plate TEXT NOT NULL, event_type TEXT NOT NULL, timestamp INTEGER NOT NULL, device_role TEXT NOT NULL, created_at INTEGER NOT NULL)`);
const app = Fastify({ logger: true });
const normalize = (v) => String(v ?? '').toUpperCase().replace(/[^A-Z0-9]/g, '');
const validPlate = (plate) => /^[A-Z]{2}[0-9]{1,2}[A-Z]{1,3}[0-9]{1,4}$/.test(plate);
const events = () => db.prepare('SELECT id,event_id AS eventId,plate,event_type AS eventType,timestamp,device_role AS deviceRole FROM events ORDER BY timestamp ASC').all();
const state = () => {
  const vehicles = new Map();
  for (const e of events()) {
    const v = vehicles.get(e.plate) ?? { plate: e.plate, insideCompound: false, insideParking: false, lastSeenAt: e.timestamp, lastEventType: e.eventType };
    v.lastSeenAt = e.timestamp; v.lastEventType = e.eventType;
    if (e.eventType === 'COMPOUND_IN') v.insideCompound = true;
    if (e.eventType === 'COMPOUND_OUT') { v.insideCompound = false; v.insideParking = false; }
    if (e.eventType === 'PARKING_IN' && v.insideCompound) v.insideParking = true;
    if (e.eventType === 'PARKING_OUT') v.insideParking = false;
    vehicles.set(e.plate, v);
  }
  const list = [...vehicles.values()];
  const parked = list.filter(v => v.insideParking).length;
  const compound = list.filter(v => v.insideCompound).length;
  return { capacity: CAPACITY, occupied: parked, free: CAPACITY ? Math.max(CAPACITY - parked, 0) : null, insideCompound: compound, vehicles: list.filter(v => v.insideCompound) };
};

app.get('/health', async () => ({ ok: true, service: 'parking-management-api', version: '0.3.0' }));
app.post('/api/v1/events', async (request, reply) => {
  const b = request.body ?? {}; const eventId = String(b.eventId ?? ''); const plate = normalize(b.plate);
  const eventType = String(b.eventType ?? ''); const deviceRole = String(b.deviceRole ?? ''); const timestamp = Number(b.timestamp);
  if (!eventId || !validPlate(plate) || !['COMPOUND_IN','COMPOUND_OUT','PARKING_IN','PARKING_OUT'].includes(eventType) || !Number.isFinite(timestamp)) return reply.code(400).send({ error: 'invalid_event' });
  if (eventType === 'PARKING_IN' && CAPACITY > 0 && state().occupied >= CAPACITY) return reply.code(409).send({ error: 'parking_full', capacity: CAPACITY });
  const existing = db.prepare('SELECT id FROM events WHERE event_id=?').get(eventId);
  if (existing) return reply.code(200).send({ id: Number(existing.id), eventId, duplicate: true });
  const r = db.prepare('INSERT INTO events(event_id,plate,event_type,timestamp,device_role,created_at) VALUES(?,?,?,?,?,?)').run(eventId, plate, eventType, timestamp, deviceRole, Date.now());
  return reply.code(201).send({ id: Number(r.lastInsertRowid), eventId, plate, eventType, timestamp, deviceRole });
});
app.get('/api/v1/events', async (request) => { const limit = Math.min(Math.max(Number(request.query?.limit ?? 100), 1), 1000); return db.prepare('SELECT id,event_id AS eventId,plate,event_type AS eventType,timestamp,device_role AS deviceRole FROM events ORDER BY timestamp DESC LIMIT ?').all(limit); });
app.get('/api/v1/status', async () => state());
app.get('/api/v1/vehicles/:plate', async (request, reply) => { const plate = normalize(request.params.plate); const result = state().vehicles.find(v => v.plate === plate); if (!result) return reply.code(404).send({ error: 'not_found' }); return { ...result, events: db.prepare('SELECT id,event_id AS eventId,plate,event_type AS eventType,timestamp,device_role AS deviceRole FROM events WHERE plate=? ORDER BY timestamp ASC').all(plate) }; });

const port = Number(process.env.PORT ?? 8080); app.listen({ port, host: process.env.HOST ?? '0.0.0.0' }).catch(e => { app.log.error(e); process.exit(1); });
