import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { API } from './api-config';
import { AuthService } from './auth.service';
import { Product } from './models';

export interface CartItem {
    productId: number;
    name: string;
    price: number;
    quantity: number;
    availableQuantity: number;
    selected: boolean;
}

@Injectable({providedIn: 'root'})
export class CartService {

    private http = inject(HttpClient);
    private auth = inject(AuthService);

    items = signal<CartItem[]>([]);

    private userId: number | null = null;
    private userIdPending: Promise<number | null> | null = null;

    constructor() {
        this.refresh();
    }

    private get base() {
        return API.orderV1;
    }

    private resolveUserId(): Promise<number | null> {
        if (this.userId != null) {
            return Promise.resolve(this.userId);
        }
        if (this.userIdPending) {
            return this.userIdPending;
        }
        const email = this.auth.getUser()?.email;
        if (!email) {
            return Promise.resolve(null);
        }
        this.userIdPending = new Promise(resolve => {
            this.http.get<{id: number}>(API.userV1 + '/users/email/' + encodeURIComponent(email)).subscribe({
                next: profile => {
                    this.userId = profile.id;
                    resolve(profile.id);
                },
                error: () => resolve(null)
            });
        });
        return this.userIdPending;
    }

    refresh() {
        this.resolveUserId().then(id => {
            if (id == null) {
                return;
            }
            this.http.get<CartItem[]>(this.base + '/cart?userId=' + id).subscribe({
                next: data => this.items.set(data.map(i => ({
                    productId: i.productId,
                    name: i.name,
                    price: Number(i.price),
                    quantity: i.quantity,
                    availableQuantity: i.availableQuantity,
                    selected: true
                }))),
                error: () => {}
            });
        });
    }

    add(product: Product, quantity: number) {
        this.resolveUserId().then(id => {
            if (id == null) {
                return;
            }
            this.http.post(this.base + '/cart/items', {
                userId: id,
                productId: product.id,
                name: product.name,
                price: product.price,
                quantity,
                availableQuantity: product.availableQuantity
            }).subscribe({next: () => this.refresh(), error: () => {}});
        });
    }

    setQuantity(productId: number, quantity: number) {
        this.resolveUserId().then(id => {
            if (id == null) {
                return;
            }
            this.http.put(this.base + '/cart/items/' + productId
                + '?userId=' + id + '&quantity=' + quantity, {})
                .subscribe({next: () => this.refresh(), error: () => {}});
        });
    }

    remove(productId: number) {
        this.resolveUserId().then(id => {
            if (id == null) {
                return;
            }
            this.http.delete(this.base + '/cart/items/' + productId + '?userId=' + id)
                .subscribe({next: () => this.refresh(), error: () => {}});
        });
    }

    clear() {
        this.resolveUserId().then(id => {
            if (id == null) {
                return;
            }
            this.http.delete(this.base + '/cart?userId=' + id)
                .subscribe({next: () => this.items.set([]), error: () => {}});
        });
    }

    toggleSelected(productId: number) {
        this.items.update(list => list.map(i =>
            i.productId === productId ? {...i, selected: !i.selected} : i));
    }

    setAllSelected(selected: boolean) {
        this.items.update(list => list.map(i => ({...i, selected})));
    }

    total(): number {
        return this.items().reduce((sum, i) => sum + i.price * i.quantity, 0);
    }

    selectedItems(): CartItem[] {
        return this.items().filter(i => i.selected);
    }

    selectedTotal(): number {
        return this.selectedItems().reduce((sum, i) => sum + i.price * i.quantity, 0);
    }

    isAllSelected(): boolean {
        const items = this.items();
        return items.length > 0 && items.every(i => i.selected);
    }
}
