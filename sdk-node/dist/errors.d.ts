export declare class AstraError extends Error {
    readonly statusCode: number;
    constructor(message: string, statusCode?: number);
}
export declare class AstraAuthError extends AstraError {
    constructor(message: string, statusCode?: number);
}
export declare class AstraNotFoundError extends AstraError {
    constructor(message: string, statusCode?: number);
}
export declare class AstraValidationError extends AstraError {
    constructor(message: string, statusCode?: number);
}
export declare class AstraServerError extends AstraError {
    constructor(message: string, statusCode?: number);
}
