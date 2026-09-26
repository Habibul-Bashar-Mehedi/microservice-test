export interface User {
    id: number;
    name: string;
    email: string;
    active: boolean;
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
}