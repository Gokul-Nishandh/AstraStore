export class AstraError extends Error {
  public readonly statusCode: number;

  constructor(message: string, statusCode: number = 0) {
    super(message);
    this.name = 'AstraError';
    this.statusCode = statusCode;
    Object.setPrototypeOf(this, new.target.prototype);
  }
}

export class AstraAuthError extends AstraError {
  constructor(message: string, statusCode: number = 401) {
    super(message, statusCode);
    this.name = 'AstraAuthError';
  }
}

export class AstraNotFoundError extends AstraError {
  constructor(message: string, statusCode: number = 404) {
    super(message, statusCode);
    this.name = 'AstraNotFoundError';
  }
}

export class AstraValidationError extends AstraError {
  constructor(message: string, statusCode: number = 400) {
    super(message, statusCode);
    this.name = 'AstraValidationError';
  }
}

export class AstraServerError extends AstraError {
  constructor(message: string, statusCode: number = 500) {
    super(message, statusCode);
    this.name = 'AstraServerError';
  }
}
