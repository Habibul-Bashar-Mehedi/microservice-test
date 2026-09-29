import { Injectable, signal } from '@angular/core';

import { Product } from './models';

export interface CartItem {
    productId: number;
    name: string;
    price: number;
    quantity: number;
    availableQuantity: number;
}

const CART_KEY = 'cart_items';

@Injectable({providedIn: 'root'})
export class CartService {

    items = signal<CartItem[]>(this.load());

    add(product: Product, quantity: number) {
        const existing = this.items().find(i => i.productId === product.id);
        let next: CartItem[];
        if (existing) {
            next = this.items().map(i =>
                i.productId === product.id
                    ? {...i, quantity: Math.min(i.quantity + quantity, product.availableQuantity)}
                    : i
            );
        } else {
            next = [...this.items(), {
                productId: product.id,
                name: product.name,
                price: product.price,
                quantity,
                availableQuantity: product.availableQuantity
            }];
        }
        this.save(next);
    }

    setQuantity(productId: number, quantity: number) {
        this.save(this.items().map(i =>
            i.productId === productId
                ? {...i, quantity: Math.max(1, Math.min(quantity, i.availableQuantity))}
                : i
        ));
    }

    remove(productId: number) {
        this.save(this.items().filter(i => i.productId !== productId));
    }

    clear() {
        this.save([]);
    }

    total(): number {
        return this.items().reduce((sum, i) => sum + i.price * i.quantity, 0);
    }

    private save(items: CartItem[]) {
        localStorage.setItem(CART_KEY, JSON.stringify(items));
        this.items.set(items);
    }

    private load(): CartItem[] {
        try {
            return JSON.parse(localStorage.getItem(CART_KEY) || '[]') as CartItem[];
        } catch {
            return [];
        }
    }
}