import assert from 'node:assert';
import { test, describe } from 'node:test';
import { AstraClient } from '../client.js';
import { AstraAuthError, AstraNotFoundError } from '../errors.js';

describe('AstraClient Node.js SDK', () => {
  test('initialization options', () => {
    const client = new AstraClient({
      baseUrl: 'http://localhost:8080',
      apiKey: 'test-key-999',
    });
    assert.strictEqual(client['baseUrl'], 'http://localhost:8080');
    assert.strictEqual(client['apiKey'], 'test-key-999');
    assert.strictEqual(client['accessToken'], 'test-key-999');
  });

  test('typed error instantiation', () => {
    const authErr = new AstraAuthError('Unauthorized test', 401);
    assert.strictEqual(authErr.statusCode, 401);
    assert.strictEqual(authErr.name, 'AstraAuthError');

    const notFoundErr = new AstraNotFoundError('Object missing', 404);
    assert.strictEqual(notFoundErr.statusCode, 404);
    assert.strictEqual(notFoundErr.name, 'AstraNotFoundError');
  });
});
