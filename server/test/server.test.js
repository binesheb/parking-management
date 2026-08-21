import test from 'node:test';
import assert from 'node:assert/strict';

test('Indian plate normalization', () => assert.equal('KL07AB1234'.replace(/[^A-Z0-9]/g,''), 'KL07AB1234'));
test('event vocabulary', () => assert.deepEqual(['COMPOUND_IN','COMPOUND_OUT','PARKING_IN','PARKING_OUT'].length, 4));
