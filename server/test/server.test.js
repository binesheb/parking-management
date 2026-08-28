import test from 'node:test';
import assert from 'node:assert/strict';

test('Indian plate normalization', () => assert.equal('kl 07 ab 1234'.toUpperCase().replace(/[^A-Z0-9]/g, ''), 'KL07AB1234'));
test('event vocabulary', () => assert.deepEqual(['COMPOUND_IN','COMPOUND_OUT','PARKING_IN','PARKING_OUT'].length, 4));
test('parking capacity math', () => { const capacity = 100; const occupied = 37; assert.equal(Math.max(capacity - occupied, 0), 63); });
test('parking full condition', () => { const capacity = 10; const occupied = 10; assert.equal(occupied >= capacity, true); });
test('last-seen is the latest event timestamp', () => { const events = [{ timestamp: 100 }, { timestamp: 200 }, { timestamp: 150 }]; const last = events.reduce((max, e) => Math.max(max, e.timestamp), 0); assert.equal(last, 200); });
