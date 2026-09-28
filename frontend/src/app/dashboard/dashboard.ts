import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';

import { AuthService } from '../auth.service';
import { API } from '../api-config';
import { Order, Product } from '../models';

interface OrderRequest {
    userId: number;
    productId: number;
    quantity: number;
}

@Component({
    selector: 'app-dashboard',
    imports: [RouterLink, FormsModule],
    templateUrl: './dashboard.html',
    styleUrl: './dashboard.css'
})
export class DashboardComponent implements OnInit {

    private auth = inject(AuthService);
    private http = inject(HttpClient);

    user = signal(this.auth.getUser());

    products = signal<Product[]>([]);
    quantities: Record<number, number> = {};
    message = signal('');
    isError = signal(false);
    placing = signal(false);

    get isAdmin() {
        return this.user()?.role === 'ADMIN';
    }

    ngOnInit() {
        if (!this.isAdmin) {
            this.loadProducts();
        }
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

        const email = this.user()?.email;
        if (!email) {
            return;
        }

        this.placing.set(true);
        this.http.get<{id: number}>(API.userV1 + '/users/email/' + encodeURIComponent(email)).subscribe({
            next: (profile) => this.submitOrder(profile.id, product.id, quantity),
            error: (err) => {
                this.placing.set(false);
                this.fail(err);
            }
        });
    }

    private submitOrder(userId: number, productId: number, quantity: number) {
        const body: OrderRequest = {userId, productId, quantity};
        this.http.post<Order>(API.orderV1 + '/orders', body).subscribe({
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