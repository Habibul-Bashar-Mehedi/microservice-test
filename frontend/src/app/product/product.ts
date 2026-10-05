import { Component, inject, OnInit, signal, WritableSignal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Title } from '@angular/platform-browser';

import { API } from '../api-config';
import { AuthService } from '../auth.service';
import { Product, ProductNotification } from '../models';

@Component({
    selector: 'app-product',
    imports: [FormsModule],
    templateUrl: './product.html',
    styleUrl: './product.css'
})
export class ProductComponent implements OnInit {

    private http = inject(HttpClient);
    private title = inject(Title);
    private auth = inject(AuthService);

    role = this.auth.getUser()?.role ?? 'USER';

    products = signal<Product[]>([]);
    pending = signal<Product[]>([]);
    notifications = signal<ProductNotification[]>([]);
    form = {name: '', price: null as number | null, availableQuantity: null as number | null};
    amounts: Record<number, number> = {};
    prices: Record<number, number> = {};
    names: Record<number, string> = {};
    reasons: Record<number, string> = {};
    edits: Record<number, {name: string; price: number | null; availableQuantity: number | null}> = {};
    message = signal('');
    isError = signal(false);

    get base() {
        return API.productV1;
    }

    ngOnInit() {
        this.title.setTitle('Products - Microservice UI');
        this.load();
        this.loadNotifications();
    }

    load() {
        if (this.role === 'MANAGER') {
            this.get(this.base + '/products/mine', this.products);
        } else if (this.role === 'MAINTAINER') {
            this.get(this.base + '/products/pending/maintainer', this.products);
        } else if (this.role === 'ADMIN') {
            this.get(this.base + '/products/pending/admin', this.pending);
            this.get(this.base + '/products/all', this.products);
        } else {
            this.get(this.base + '/products', this.products);
        }
    }

    private get(url: string, target: WritableSignal<Product[]>) {
        this.http.get<Product[]>(url).subscribe({
            next: (data) => target.set(data),
            error: (err) => this.fail(err)
        });
    }

    loadNotifications() {
        this.http.get<ProductNotification[]>(this.base + '/notifications').subscribe({
            next: (data) => this.notifications.set(data),
            error: (err) => this.fail(err)
        });
    }

    create() {
        this.http.post<Product>(this.base + '/products', this.form).subscribe({
            next: () => {
                this.message.set('Product created and sent to the maintainer for review.');
                this.isError.set(false);
                this.form = {name: '', price: null, availableQuantity: null};
                this.load();
            },
            error: (err) => this.fail(err)
        });
    }

    startEdit(product: Product) {
        this.edits[product.id] = {
            name: product.name,
            price: product.price,
            availableQuantity: product.availableQuantity
        };
    }

    resubmit(product: Product) {
        const edit = this.edits[product.id];
        if (!edit) {
            return;
        }
        this.http.put<Product>(this.base + '/products/' + product.id, edit).subscribe({
            next: () => {
                this.message.set('Product ' + product.id + ' resubmitted to the maintainer.');
                this.isError.set(false);
                delete this.edits[product.id];
                this.load();
            },
            error: (err) => this.fail(err)
        });
    }

    maintainerReview(product: Product, approved: boolean) {
        const reason = this.reasons[product.id];
        if (!approved && (!reason || !reason.trim())) {
            this.message.set('A rejection reason is required.');
            this.isError.set(true);
            return;
        }
        this.http.post<Product>(this.base + '/products/' + product.id + '/maintainer/review',
            {approved, reason: reason ?? null}).subscribe({
            next: () => {
                this.message.set(approved
                    ? 'Product ' + product.id + ' accepted and sent to the admin.'
                    : 'Product ' + product.id + ' rejected and sent back to the manager.');
                this.isError.set(false);
                this.reasons[product.id] = '';
                this.load();
                this.loadNotifications();
            },
            error: (err) => this.fail(err)
        });
    }

    adminReview(product: Product, approved: boolean) {
        const reason = this.reasons[product.id];
        if (!approved && (!reason || !reason.trim())) {
            this.message.set('A rejection reason is required.');
            this.isError.set(true);
            return;
        }
        this.http.post<Product>(this.base + '/products/' + product.id + '/admin/review',
            {approved, reason: reason ?? null}).subscribe({
            next: () => {
                this.message.set(approved
                    ? 'Product ' + product.id + ' finally approved and added to the store.'
                    : 'Product ' + product.id + ' rejected and both manager and maintainer notified.');
                this.isError.set(false);
                this.reasons[product.id] = '';
                this.load();
                this.loadNotifications();
            },
            error: (err) => this.fail(err)
        });
    }

    markRead(notification: ProductNotification) {
        this.http.post(this.base + '/notifications/' + notification.id + '/read', {}).subscribe({
            next: () => this.loadNotifications(),
            error: (err) => this.fail(err)
        });
    }

    addQuantity(product: Product) {
        const amount = this.amounts[product.id];
        if (!amount || amount < 1) {
            this.message.set('Quantity must be at least 1.');
            this.isError.set(true);
            return;
        }

        this.http.put<Product>(this.base + '/products/' + product.id + '/add-quantity', amount).subscribe({
            next: () => {
                this.message.set('Added ' + amount + ' to product ' + product.id + '.');
                this.isError.set(false);
                this.amounts[product.id] = 0;
                this.load();
            },
            error: (err) => this.fail(err)
        });
    }

    updatePrice(product: Product) {
        const price = this.prices[product.id];
        if (price == null || price <= 0) {
            this.message.set('Price must be greater than 0.');
            this.isError.set(true);
            return;
        }

        this.http.put<Product>(this.base + '/products/' + product.id + '/price', price).subscribe({
            next: () => {
                this.message.set('Price updated for product ' + product.id + '.');
                this.isError.set(false);
                this.prices[product.id] = 0;
                this.load();
            },
            error: (err) => this.fail(err)
        });
    }

    updateName(product: Product) {
        const name = this.names[product.id];
        if (!name || !name.trim()) {
            this.message.set('Name must not be blank.');
            this.isError.set(true);
            return;
        }

        this.http.put<Product>(this.base + '/products/' + product.id + '/name', name.trim()).subscribe({
            next: () => {
                this.message.set('Name updated for product ' + product.id + '.');
                this.isError.set(false);
                this.names[product.id] = '';
                this.load();
            },
            error: (err) => this.fail(err)
        });
    }

    private fail(err: any) {
        this.message.set(err?.error?.message || err?.message || 'Request failed');
        this.isError.set(true);
    }
}