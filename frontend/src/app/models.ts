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
    status?: string;
    rejectionReason?: string | null;
    rejectedByRole?: string | null;
    createdBy?: string | null;
    maintainerReviewer?: string | null;
    adminReviewer?: string | null;
}

export interface ProductNotification {
    id: number;
    productId: number;
    productName: string;
    recipientEmail: string | null;
    recipientRole: string | null;
    message: string;
    createdAt: string;
    read: boolean;
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