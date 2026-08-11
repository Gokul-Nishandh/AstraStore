"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.AstraServerError = exports.AstraValidationError = exports.AstraNotFoundError = exports.AstraAuthError = exports.AstraError = void 0;
class AstraError extends Error {
    statusCode;
    constructor(message, statusCode = 0) {
        super(message);
        this.name = 'AstraError';
        this.statusCode = statusCode;
        Object.setPrototypeOf(this, new.target.prototype);
    }
}
exports.AstraError = AstraError;
class AstraAuthError extends AstraError {
    constructor(message, statusCode = 401) {
        super(message, statusCode);
        this.name = 'AstraAuthError';
    }
}
exports.AstraAuthError = AstraAuthError;
class AstraNotFoundError extends AstraError {
    constructor(message, statusCode = 404) {
        super(message, statusCode);
        this.name = 'AstraNotFoundError';
    }
}
exports.AstraNotFoundError = AstraNotFoundError;
class AstraValidationError extends AstraError {
    constructor(message, statusCode = 400) {
        super(message, statusCode);
        this.name = 'AstraValidationError';
    }
}
exports.AstraValidationError = AstraValidationError;
class AstraServerError extends AstraError {
    constructor(message, statusCode = 500) {
        super(message, statusCode);
        this.name = 'AstraServerError';
    }
}
exports.AstraServerError = AstraServerError;
