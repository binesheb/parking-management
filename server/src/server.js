import Fastify from 'fastify';
import { mkdirSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { dirname, resolve } from 'node:path';

const dbPath = resolve(process.env.DB_PATH ?? './data/parking.sqlite');
mkdirSync(dirname(dbPath), { recursive: true });
const db = new DatabaseSync(dbPath);
db.exec(`CREATE TABLE IF NOT EXISTS events (id INTEGER PRIMARY KEY AUTOINCREMENT, plate TEXT NOT NULL, event_type TEXT NOT NULL, timestamp INTEGER NOT NULL, device_role TEXT NOT NULL, created_at INTEGER NOT NULL)`);
const app = Fastify({ logger: true });

const normalize = (v) => String(v ?? '').toUpperCase().replace(/[^A-Z0-9]/g, '');
app.get('/health', async () => ({ ok: true, service: 'parking-management-api', version: '0.2.0' }));
app.post('/api/v1/events', async (request, reply) => {
  const body = request.body ?? {};
  const plate = normalize(body.plate);
  const eventType = String(body.eventType ?? '');
  const deviceRole = String(body.deviceRole ?? '');
  const timestamp = Number(body.timestamp);
  if (!/^[A-Z]{2}[0-9]{1,2}[A-Z]{1,3}[0-9]{1,4}$/.test(plate) || !['COMPOUND_IN','COMPOUND_OUT','PARKING_IN','PARKING_OUT'].includes(eventType) || !Number.isFinite(timestamp)) return reply.code(400).send({ error: 'invalid_event' });
  const result = db.prepare('INSERT INTO events(plate,event_type,timestamp,device_role,created_at) VALUES(?,?,?,?,?)').run(plate,eventType,timestamp,deviceRole,Date.now());
  return reply.code(201).send({ id: Number(result.lastInsertRowid), plate, eventType, timestamp, deviceRole });
});
app.get('/api/v1/events', async (request) => {
  const limit = Math.min(Math.max(Number(request.query?.limit ?? 100), 1), 1000);
  return db.prepare('SELECT id,plate,event_type AS eventType,timestamp,device_role AS deviceRole FROM events ORDER BY timestamp DESC LIMIT ?').all(limit);
});
app.get('/api/v1/vehicles/:plate', async (request, reply) => {
  const plate = normalize(request.params.plate);
  const events = db.prepare('SELECT id,plate,event_type AS eventType,timestamp,device_role AS deviceRole FROM events WHERE plate=? ORDER BY timestamp ASC').all(plate);
  if (!events.length) return reply.code(404).send({ error: 'not_found' });
  let compound = false, parking = false;
  for (const e of events) { if (e.eventType === 'COMPOUND_IN') compound = true; if (e.eventType === 'COMPOUND_OUT') compound = false; if (e.eventType === 'PARKING_IN') parking = true; if (e.eventType === 'PARKING_OUT') parking = false; }
  return { plate, insideCompound: compound, insideParking: parking, events };
});
const port = Number(process.env.PORT ?? 8080);
app.listen({ port, host: process.env.HOST ?? '0.0.0.0' }).catch((error) => { app.log.error(error); process.exit(1); });
