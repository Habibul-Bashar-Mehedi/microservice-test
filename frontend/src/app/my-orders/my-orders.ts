import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { Title } from '@angular/platform-browser';

import { API } from '../api-config';
import { AuthService } from '../auth.service';
import { ConfirmService } from '../confirm-dialog/confirm.service';
import { Order, Product } from '../models';

type ApiVersion = 'v1' | 'v2' | 'v3';

const API_VERSION_KEY = 'order-api-version';

@Component({
    selector: 'app-my-orders',
    templateUrl: './my-orders.html'
})
export class MyOrdersComponent implements OnInit {

    private http = inject(HttpClient);
    private route = inject(ActivatedRoute);
    private title = inject(Title);
    private auth = inject(AuthService);
    private destroyRef = inject(DestroyRef);
    private confirmDialog = inject(ConfirmService);

    orders = signal<Order[]>([]);
    filter = signal('');
    filteredOrders = computed(() => {
        const q = this.filter();
        const base = q
            ? this.orders().filter(o =>
                String(o.id).includes(q) ||
                String(o.productId).includes(q) ||
                o.status?.toLowerCase().includes(q))
            : this.orders();
        return [...base].sort((a, b) => b.id - a.id);
    });
    products = signal<Product[]>([]);
    message = signal('');
    isError = signal(false);
    cancelling = signal(false);

    private userProfileId: number | null = null;

    ngOnInit() {
        this.title.setTitle('My Orders - Microservice UI');
        this.route.queryParamMap.subscribe(params => this.filter.set((params.get('q') ?? '').trim().toLowerCase()));
        const email = this.auth.getUser()?.email;
        if (!email) {
            return;
        }
        this.http.get<{id: number}>(API.userV1 + '/users/email/' + encodeURIComponent(email)).subscribe({
            next: (profile) => {
                this.userProfileId = profile.id;
                this.loadOrders();
            },
            error: (err) => this.fail(err)
        });
        this.loadProducts();

        const timer = setInterval(() => this.loadOrders(), 5000);
        this.destroyRef.onDestroy(() => clearInterval(timer));
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

    private loadProducts() {
        this.http.get<Product[]>(API.productV1 + '/products').subscribe({
            next: (data) => this.products.set(data),
            error: (err) => this.fail(err)
        });
    }

    async cancelOrder(order: Order) {
        if (this.cancelling() || this.userProfileId == null) {
            return;
        }

        if (!(await this.confirmDialog.ask('Are you sure you want to cancel this order?'))) {
            return;
        }

        const base = this.cancelBase;
        this.cancelling.set(true);
        this.http.post<Order>(
            base + '/orders/' + order.id + '/cancel?userId=' + this.userProfileId,
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

    private get cancelBase() {
        const saved = localStorage.getItem(API_VERSION_KEY);
        const version: ApiVersion = saved === 'v2' || saved === 'v3' ? saved : 'v1';
        return version === 'v3' ? API.orderV3 : API.orderV1;
    }

    canCancel(status: string) {
        return status === 'PENDING' || status === 'CONFIRMING';
    }

    productName(productId: number) {
        return this.products().find(p => p.id === productId)?.name;
    }

    private fail(err: any) {
        this.message.set(err?.error?.message || err?.message || 'Request failed');
        this.isError.set(true);
    }
}
