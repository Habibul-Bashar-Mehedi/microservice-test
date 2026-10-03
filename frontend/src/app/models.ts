export interface User {
    id: number;
    name: string;
    email: string;
    active: boolean;
    role: string;
}

export interface Product {
    id: number;
    name: string;
    price: number;
    availableQuantity: number;
}

export interface Order {
    id: number;
    userId: number;
    productId: number;
    quantity: number;
    status: string;
    productUpdated: boolean;
    userName?: string;
    userEmail?: string;
}

export interface MessageLog {
    id: number;
    serviceName: string;
    direction: 'PUBLISHED' | 'CONSUMED';
    routingKey: string;
    queue: string;
    payload: string;
    status: 'SUCCESS' | 'FAILED';
    detail: string;
    email: string | null;
    createdAt: string;
}