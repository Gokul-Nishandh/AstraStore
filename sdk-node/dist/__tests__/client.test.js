"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const node_assert_1 = __importDefault(require("node:assert"));
const node_test_1 = require("node:test");
const client_js_1 = require("../client.js");
const errors_js_1 = require("../errors.js");
(0, node_test_1.describe)('AstraClient Node.js SDK', () => {
    (0, node_test_1.test)('initialization options', () => {
        const client = new client_js_1.AstraClient({
            baseUrl: 'http://localhost:8080',
            apiKey: 'test-key-999',
        });
        node_assert_1.default.strictEqual(client['baseUrl'], 'http://localhost:8080');
        node_assert_1.default.strictEqual(client['apiKey'], 'test-key-999');
        node_assert_1.default.strictEqual(client['accessToken'], 'test-key-999');
    });
    (0, node_test_1.test)('typed error instantiation', () => {
        const authErr = new errors_js_1.AstraAuthError('Unauthorized test', 401);
        node_assert_1.default.strictEqual(authErr.statusCode, 401);
        node_assert_1.default.strictEqual(authErr.name, 'AstraAuthError');
        const notFoundErr = new errors_js_1.AstraNotFoundError('Object missing', 404);
        node_assert_1.default.strictEqual(notFoundErr.statusCode, 404);
        node_assert_1.default.strictEqual(notFoundErr.name, 'AstraNotFoundError');
    });
});
