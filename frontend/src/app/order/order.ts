import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { Title } from '@angular/platform-browser';

import { API } from '../api-config';
import { AuthService } from '../auth.service';
import { ConfirmService } from '../confirm-dialog/confirm.service';
import { Order } from '../models';

type ApiVersion = 'v1' | 'v2' | 'v3';

type OrderFeature = 'create' | 'list';

const API_VERSION_KEY = 'order-api-version';

@Component({
    selector: 'app-order',
    imports: [FormsModule],
    templateUrl: './order.html',
    styleUrl: './order.css'
})
export class OrderComponent implements OnInit {

    private http = inject(HttpClient);
    private route = inject(ActivatedRoute);
    private title = inject(Title);
    private destroyRef = inject(DestroyRef);
    private auth = inject(AuthService);
    private confirmDialog = inject(ConfirmService);

    role = this.auth.getUser()?.role ?? 'USER';
    feature: OrderFeature = 'list';

    orders = signal<Order[]>([]);
    filter = signal('');
    filteredOrders = computed(() => {
        const q = this.filter();
        const base = q
            ? this.orders().filter(o =>
                String(o.id).includes(q) ||
                o.userName?.toLowerCase().includes(q) ||
                o.userEmail?.toLowerCase().includes(q) ||
                o.status?.toLowerCase().includes(q) ||
                String(o.userId).includes(q) ||
                String(o.productId).includes(q))
            : this.orders();
        return [...base].sort((a, b) => b.id - a.id);
    });
    form = {userId: null as number | null, productId: null as number | null, quantity: null as number | null};
    message = signal('');
    isError = signal(false);
    creating = signal(false);
    apiVersion = signal<ApiVersion>(this.loadSavedVersion());

    get canCreate() {
        return this.role === 'ADMIN';
    }

    get canConfirm() {
        return this.role === 'ADMIN' || this.role === 'MANAGER';
    }

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

    ngOnInit() {
        this.feature = (this.route.snapshot.data['feature'] as OrderFeature) ?? 'list';
        this.title.setTitle('Orders - Microservice UI');
        this.route.queryParamMap.subscribe(params => this.filter.set((params.get('q') ?? '').trim().toLowerCase()));
        if (this.feature === 'list') {
            this.load();
            const timer = setInterval(() => this.load(), 5000);
            this.destroyRef.onDestroy(() => clearInterval(timer));
        }
    }

    setVersion(version: ApiVersion) {
        this.apiVersion.set(version);
        localStorage.setItem(API_VERSION_KEY, version);
        if (this.feature === 'list') {
            this.load();
        }
    }

    async create() {
        if (this.creating()) {
            return;
        }
        if (!(await this.confirmDialog.ask('Are you sure you want to create this order?'))) {
            return;
        }

        const version = this.apiVersion();
        this.creating.set(true);
        this.http.post<Order>(this.base + '/orders', this.form).subscribe({
            next: () => {
                this.message.set(version === 'v2'
                    ? 'Order created (PENDING). User validation happens asynchronously.'
                    : version === 'v3'
                        ? 'Order created (PENDING) via Feign.'
                        : 'Order created (PENDING).');
                this.isError.set(false);
                this.form = {userId: null, productId: null, quantity: null};
                this.creating.set(false);
            },
            error: (err) => {
                this.creating.set(false);
                this.fail(err);
            }
        });
    }

    async confirm(id: number) {
        if (!(await this.confirmDialog.ask('Are you sure you want to confirm this order?'))) {
            return;
        }
        const version = this.apiVersion();
        this.http.post<Order>(this.base + '/orders/' + id + '/confirm', {}).subscribe({
            next: () => {
                this.message.set(version === 'v2'
                    ? 'Confirm request accepted (async). Order will be confirmed after stock update.'
                    : 'Order ' + id + ' confirmed. Product updated.');
                this.isError.set(false);
                this.load();
            },
            error: (err) => this.fail(err)
        });
    }

    load() {
        this.http.get<Order[]>(API.orderV1 + '/orders').subscribe({
            next: (data) => this.orders.set(data),
            error: (err) => this.fail(err)
        });
    }

    private loadSavedVersion(): ApiVersion {
        const saved = localStorage.getItem(API_VERSION_KEY);
        return saved === 'v2' || saved === 'v3' ? saved : 'v1';
    }

    private fail(err: any) {
        this.message.set(err?.error?.message || err?.message || 'Request failed');
        this.isError.set(true);
    }
}
