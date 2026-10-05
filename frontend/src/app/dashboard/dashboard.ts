import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';

import { AuthService } from '../auth.service';
import { API } from '../api-config';
import { CartService } from '../cart.service';
import { Order, Product } from '../models';

interface OrderRequest {
    userId: number;
    productId: number;
    quantity: number;
}

type ApiVersion = 'v1' | 'v2' | 'v3';

const API_VERSION_KEY = 'order-api-version';

@Component({
    selector: 'app-dashboard',
    imports: [RouterLink, FormsModule],
    templateUrl: './dashboard.html',
    styleUrl: './dashboard.css'
})
export class DashboardComponent implements OnInit {

    private auth = inject(AuthService);
    private http = inject(HttpClient);
    cart = inject(CartService);
    private destroyRef = inject(DestroyRef);

    user = signal(this.auth.getUser());

    products = signal<Product[]>([]);
    orders = signal<Order[]>([]);
    quantities: Record<number, number> = {};
    searchQuery = signal('');
    message = signal('');
    isError = signal(false);
    placing = signal(false);
    checkingOut = signal(false);
    cancelling = signal(false);
    apiVersion = signal<ApiVersion>(this.loadSavedVersion());

    get base() {
        const version = this.apiVersion();
        if (version === 'v2') {
            return API.orderV2;
        }
        if (version === 'v3') {
            return API.orderV3;
        }
        return API.orderV1;
    }

    private get cancelBase() {
        return this.apiVersion() === 'v3' ? API.orderV3 : API.orderV1;
    }

    setVersion(version: ApiVersion) {
        this.apiVersion.set(version);
        localStorage.setItem(API_VERSION_KEY, version);
    }

    private loadSavedVersion(): ApiVersion {
        const saved = localStorage.getItem(API_VERSION_KEY);
        return saved === 'v2' || saved === 'v3' ? saved : 'v1';
    }

    private userProfileId: number | null = null;

    cartItems = this.cart.items;

    get isAdmin() {
        return this.user()?.role === 'ADMIN';
    }

    get role() {
        return this.user()?.role ?? 'USER';
    }

    get canManageProducts() {
        return ['ADMIN', 'MANAGER', 'MAINTAINER'].includes(this.role);
    }

    get productLinkLabel() {
        if (this.role === 'MANAGER') {
            return 'My Products';
        }
        if (this.role === 'MAINTAINER') {
            return 'Review Products';
        }
        return 'Manage Products';
    }

    ngOnInit() {
        if (this.role === 'USER') {
            const email = this.user()?.email;
            if (!email) {
                return;
            }
            this.http.get<{id: number}>(API.userV1 + '/users/email/' + encodeURIComponent(email)).subscribe({
                next: (profile) => {
                    this.userProfileId = profile.id;
                    this.loadProducts();
                    this.loadOrders();
                },
                error: (err) => this.fail(err)
            });
        }

        const timer = setInterval(() => this.refresh(), 5000);
        this.destroyRef.onDestroy(() => clearInterval(timer));
    }

    private refresh() {
        if (this.userProfileId != null) {
            if (!this.searchQuery().trim()) {
                this.loadProducts();
            }
            this.loadOrders();
        }
    }

    search() {
        const q = this.searchQuery().trim();
        if (!q) {
            this.loadProducts();
            return;
        }
        this.http.get<Product[]>(API.productV1 + '/products/search', {params: {q}}).subscribe({
            next: (data) => this.products.set(data),
            error: (err) => this.fail(err)
        });
    }

    loadProducts() {
        this.http.get<Product[]>(API.productV1 + '/products').subscribe({
            next: (data) => {
                this.products.set(data);
                for (const p of data) {
                    if (this.quantities[p.id] == null) {
                        this.quantities[p.id] = 1;
                    }
                }
            },
            error: (err) => this.fail(err)
        });
    }

    addToCart(product: Product) {
        const quantity = this.quantities[product.id];
        if (!quantity || quantity < 1) {
            this.message.set('Quantity must be at least 1.');
            this.isError.set(true);
            return;
        }

        this.cart.add(product, quantity);
        this.message.set('Added to cart.');
        this.isError.set(false);
    }

    placeOrder(product: Product) {
        if (this.placing()) {
            return;
        }

        const quantity = this.quantities[product.id];
        if (!quantity || quantity < 1) {
            this.message.set('Quantity must be at least 1.');
            this.isError.set(true);
            return;
        }

        if (this.userProfileId == null) {
            return;
        }

        this.placing.set(true);
        this.submitOrder(this.userProfileId, product.id, quantity);
    }

    loadOrders() {
        if (this.userProfileId == null) {
            return;
        }
        this.http.get<Order[]>(API.orderV1 + '/orders/user/' + this.userProfileId).subscribe({
            next: (data) => this.orders.set(data),
            error: (err) => this.fail(err)
        });
    }

    cancelOrder(order: Order) {
        if (this.cancelling() || this.userProfileId == null) {
            return;
        }

        this.cancelling.set(true);
        this.http.post<Order>(
            this.cancelBase + '/orders/' + order.id + '/cancel?userId=' + this.userProfileId,
            {}
        ).subscribe({
            next: () => {
                this.message.set('Order ' + order.id + ' cancelled.');
                this.isError.set(false);
                this.cancelling.set(false);
                this.loadOrders();
            },
            error: (err) => {
                this.cancelling.set(false);
                this.fail(err);
            }
        });
    }

    canCancel(status: string) {
        return status === 'PENDING' || status === 'CONFIRMING';
    }

    productName(productId: number) {
        return this.products().find(p => p.id === productId)?.name;
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

    cartTotal() {
        return this.cart.total();
    }

    selectedCount(): number {
        return this.cart.selectedItems().length;
    }

    selectedTotal(): number {
        return this.cart.selectedTotal();
    }

    checkout() {
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
            return;
        }

        this.checkingOut.set(true);
        this.submitCart(this.userProfileId, items);
    }

    private submitOrder(userId: number, productId: number, quantity: number) {
        const version = this.apiVersion();
        const body: OrderRequest = {userId, productId, quantity};
        this.http.post<Order>(this.base + '/orders', body).subscribe({
            next: () => {
                this.message.set(version === 'v2'
                    ? 'Order placed (PENDING). User validation happens asynchronously.'
                    : version === 'v3'
                        ? 'Order placed (PENDING) via Feign. The admin will confirm it.'
                        : 'Order placed successfully. The admin will confirm it.');
                this.isError.set(false);
                this.placing.set(false);
                this.loadOrders();
            },
            error: (err) => {
                this.placing.set(false);
                this.fail(err);
            }
        });
    }

    private submitCart(userId: number, items: {productId: number; quantity: number}[]) {
        let index = 0;

        const placeNext = () => {
            if (index >= items.length) {
                this.message.set('Cart checked out. Orders placed successfully.');
                this.isError.set(false);
                this.checkingOut.set(false);
                this.cart.clear();
                this.loadOrders();
                return;
            }

            const item = items[index++];
            const body: OrderRequest = {userId, productId: item.productId, quantity: item.quantity};
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