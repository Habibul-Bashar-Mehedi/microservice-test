import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Title } from '@angular/platform-browser';

import { API } from '../api-config';
import { AuthService } from '../auth.service';
import { CartService, CartItem } from '../cart.service';
import { ConfirmService } from '../confirm-dialog/confirm.service';
import { Order } from '../models';

type ApiVersion = 'v1' | 'v2' | 'v3';

const API_VERSION_KEY = 'order-api-version';

@Component({
    selector: 'app-cart',
    imports: [FormsModule],
    templateUrl: './cart.html'
})
export class CartComponent implements OnInit {

    private http = inject(HttpClient);
    private title = inject(Title);
    private auth = inject(AuthService);
    private confirmDialog = inject(ConfirmService);
    cart = inject(CartService);

    cartItems = this.cart.items;
    message = signal('');
    isError = signal(false);
    checkingOut = signal(false);

    private userProfileId: number | null = null;

    ngOnInit() {
        this.title.setTitle('Cart - Microservice UI');
        const email = this.auth.getUser()?.email;
        if (!email) {
            return;
        }
        this.http.get<{id: number}>(API.userV1 + '/users/email/' + encodeURIComponent(email)).subscribe({
            next: (profile) => this.userProfileId = profile.id,
            error: (err) => this.fail(err)
        });
    }

    private get base() {
        const saved = localStorage.getItem(API_VERSION_KEY);
        const version: ApiVersion = saved === 'v2' || saved === 'v3' ? saved : 'v1';
        if (version === 'v2') {
            return API.orderV2;
        }
        if (version === 'v3') {
            return API.orderV3;
        }
        return API.orderV1;
    }

    updateCartQuantity(productId: number, event: Event) {
        const value = parseInt((event.target as HTMLInputElement).value, 10);
        if (isNaN(value) || value < 1) {
            return;
        }
        this.cart.setQuantity(productId, value);
    }

    removeFromCart(productId: number) {
        this.cart.remove(productId);
    }

    selectedCount(): number {
        return this.cart.selectedItems().length;
    }

    selectedTotal(): number {
        return this.cart.selectedTotal();
    }

    async checkout() {
        if (this.checkingOut()) {
            return;
        }

        const items = this.cart.selectedItems();
        if (items.length === 0) {
            this.message.set('Select at least one item to checkout.');
            this.isError.set(true);
            return;
        }

        if (this.userProfileId == null) {
            this.message.set('User profile is still loading. Try again in a moment.');
            this.isError.set(true);
            return;
        }

        if (!(await this.confirmDialog.ask('Are you sure you want to check out the selected items?'))) {
            return;
        }

        this.checkingOut.set(true);
        this.submitCart(this.userProfileId, items);
    }

    private submitCart(userId: number, items: CartItem[]) {
        let index = 0;

        const placeNext = () => {
            if (index >= items.length) {
                this.message.set('Cart checked out. Orders placed successfully.');
                this.isError.set(false);
                this.checkingOut.set(false);
                items.forEach(item => this.cart.remove(item.productId));
                return;
            }

            const item = items[index++];
            const body = {userId, productId: item.productId, quantity: item.quantity};
            this.http.post<Order>(this.base + '/orders', body).subscribe({
                next: placeNext,
                error: (err) => {
                    this.checkingOut.set(false);
                    this.fail(err);
                }
            });
        };

        placeNext();
    }

    private fail(err: any) {
        this.message.set(err?.error?.message || err?.message || 'Request failed');
        this.isError.set(true);
    }
}
