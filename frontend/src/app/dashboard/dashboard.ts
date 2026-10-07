import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';

import { AuthService } from '../auth.service';
import { API } from '../api-config';
import { CartService } from '../cart.service';
import { ConfirmService } from '../confirm-dialog/confirm.service';
import { Order, Product } from '../models';

type ApiVersion = 'v1' | 'v2' | 'v3';

const API_VERSION_KEY = 'order-api-version';

@Component({
    selector: 'app-dashboard',
    imports: [FormsModule],
    templateUrl: './dashboard.html',
    styleUrl: './dashboard.css'
})
export class DashboardComponent implements OnInit {

    private auth = inject(AuthService);
    private http = inject(HttpClient);
    private route = inject(ActivatedRoute);
    private confirmDialog = inject(ConfirmService);
    cart = inject(CartService);

    user = signal(this.auth.getUser());

    products = signal<Product[]>([]);
    quantities: Record<number, number> = {};
    searchQuery = signal('');
    message = signal('');
    isError = signal(false);
    placing = signal(false);

    private userProfileId: number | null = null;

    get role() {
        return this.user()?.role ?? 'USER';
    }

    ngOnInit() {
        if (this.role !== 'USER') {
            return;
        }
        const email = this.user()?.email;
        if (!email) {
            return;
        }
        this.http.get<{id: number}>(API.userV1 + '/users/email/' + encodeURIComponent(email)).subscribe({
            next: (profile) => {
                this.userProfileId = profile.id;
                this.route.queryParamMap.subscribe(params => {
                    const q = (params.get('q') ?? '').trim();
                    this.searchQuery.set(q);
                    if (q) {
                        this.search();
                    } else {
                        this.loadProducts();
                    }
                });
            },
            error: (err) => this.fail(err)
        });
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

    async placeOrder(product: Product) {
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

        if (!(await this.confirmDialog.ask('Are you sure you want to place this order?'))) {
            return;
        }

        this.placing.set(true);
        this.submitOrder(this.userProfileId, product.id, quantity);
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

    private submitOrder(userId: number, productId: number, quantity: number) {
        this.http.post<Order>(this.base + '/orders', {userId, productId, quantity}).subscribe({
            next: () => {
                this.message.set('Order placed successfully. The admin will confirm it.');
                this.isError.set(false);
                this.placing.set(false);
            },
            error: (err) => {
                this.placing.set(false);
                this.fail(err);
            }
        });
    }

    private fail(err: any) {
        this.message.set(err?.error?.message || err?.message || 'Request failed');
        this.isError.set(true);
    }
}
