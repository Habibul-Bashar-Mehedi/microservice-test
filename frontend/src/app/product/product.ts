import { Component, computed, inject, OnInit, signal, WritableSignal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { Title } from '@angular/platform-browser';

import { API } from '../api-config';
import { AuthService } from '../auth.service';
import { ConfirmService } from '../confirm-dialog/confirm.service';
import { Product } from '../models';

type ProductFeature = 'add' | 'list' | 'pending' | 'stock';

@Component({
    selector: 'app-product',
    imports: [FormsModule],
    templateUrl: './product.html',
    styleUrl: './product.css'
})
export class ProductComponent implements OnInit {

    private http = inject(HttpClient);
    private route = inject(ActivatedRoute);
    private title = inject(Title);
    private auth = inject(AuthService);
    private confirmDialog = inject(ConfirmService);

    role = this.auth.getUser()?.role ?? 'USER';
    feature: ProductFeature = 'list';

    list = signal<Product[]>([]);
    pending = signal<Product[]>([]);
    stock = signal<Product[]>([]);
    filter = signal('');
    sort = signal('');

    filteredList = computed(() => this.applyView(this.list()));
    filteredPending = computed(() => this.applyView(this.pending()));
    filteredStock = computed(() => this.applyView(this.stock()));

    managerPending = computed(() => this.applyView(this.list()).filter(p =>
        p.status === 'PENDING_MAINTAINER' || p.status === 'PENDING_ADMIN'));

    categories = ['OTHER', 'CHAL', 'DAL', 'ATA', 'MOYDA', 'CHINI', 'MOSHLA'];
    form = {name: '', price: null as number | null, availableQuantity: null as number | null, category: 'OTHER'};
    amounts: Record<number, number> = {};
    prices: Record<number, number> = {};
    names: Record<number, string> = {};
    reasons: Record<number, string> = {};
    edits: Record<number, {name: string; price: number | null; availableQuantity: number | null; category: string}> = {};
    message = signal('');
    isError = signal(false);

    get base() {
        return API.productV1;
    }

    ngOnInit() {
        this.feature = (this.route.snapshot.data['feature'] as ProductFeature) ?? 'list';
        this.title.setTitle('Products - Microservice UI');
        this.route.queryParamMap.subscribe(params => this.filter.set((params.get('q') ?? '').trim().toLowerCase()));
        this.load();
    }

    private applyFilter(items: Product[]): Product[] {
        const q = this.filter();
        if (!q) {
            return items;
        }
        return items.filter(p => p.name?.toLowerCase().includes(q));
    }

    private applyView(items: Product[]): Product[] {
        const filtered = this.applyFilter(items);
        let result = filtered;
        if (this.sort() === 'out-of-stock') {
            result = filtered.filter(p => p.availableQuantity === 0);
        } else if (this.sort() === 'low-stock') {
            result = filtered.filter(p => p.availableQuantity < 10);
        }
        return [...result].sort((a, b) => b.id - a.id);
    }

    load() {
        if (this.feature === 'list') {
            if (this.role === 'MANAGER') {
                this.get(this.base + '/products/mine', this.list);
            } else {
                this.get(this.base + '/products', this.list);
            }
        } else if (this.feature === 'pending') {
            if (this.role === 'MANAGER') {
                this.get(this.base + '/products/mine', this.list);
            } else if (this.role === 'MAINTAINER') {
                this.get(this.base + '/products/pending/maintainer', this.pending);
            } else {
                this.get(this.base + '/products/pending/admin', this.pending);
            }
        } else if (this.feature === 'stock') {
            if (this.role === 'MAINTAINER') {
                this.get(this.base + '/products', this.stock);
            } else {
                this.get(this.base + '/products/all', this.stock);
            }
        }
    }

    private get(url: string, target: WritableSignal<Product[]>) {
        this.http.get<Product[]>(url).subscribe({
            next: (data) => target.set(data),
            error: (err) => this.fail(err)
        });
    }

    async create() {
        if (!(await this.confirmDialog.ask('Are you sure you want to create this product?'))) {
            return;
        }
        this.http.post<Product>(this.base + '/products', this.form).subscribe({
            next: (created) => {
                this.message.set(created.status === 'APPROVED'
                    ? 'Product created and approved automatically.'
                    : 'Product created and sent to the maintainer for review.');
                this.isError.set(false);
                this.form = {name: '', price: null, availableQuantity: null, category: 'OTHER'};
            },
            error: (err) => this.fail(err)
        });
    }

    startEdit(product: Product) {
        this.edits[product.id] = {
            name: product.name,
            price: product.price,
            availableQuantity: product.availableQuantity,
            category: product.category ?? 'OTHER'
        };
    }

    async resubmit(product: Product) {
        const edit = this.edits[product.id];
        if (!edit) {
            return;
        }
        if (!(await this.confirmDialog.ask('Are you sure you want to resubmit this product?'))) {
            return;
        }
        this.http.put<Product>(this.base + '/products/' + product.id, edit).subscribe({
            next: (updated) => {
                this.message.set(updated.status === 'APPROVED'
                    ? 'Product ' + product.id + ' resubmitted and approved automatically.'
                    : 'Product ' + product.id + ' resubmitted to the maintainer.');
                this.isError.set(false);
                delete this.edits[product.id];
                this.load();
            },
            error: (err) => this.fail(err)
        });
    }

    async maintainerReview(product: Product, approved: boolean) {
        const reason = this.reasons[product.id];
        if (!approved && (!reason || !reason.trim())) {
            this.message.set('A rejection reason is required.');
            this.isError.set(true);
            return;
        }
        const action = approved ? 'approve' : 'reject';
        if (!(await this.confirmDialog.ask('Are you sure you want to ' + action + ' this product?'))) {
            return;
        }
        this.http.post<Product>(this.base + '/products/' + product.id + '/maintainer/review',
            {approved, reason: reason ?? null}).subscribe({
            next: (updated) => {
                this.message.set(approved
                    ? (updated.status === 'APPROVED'
                        ? 'Product ' + product.id + ' approved by maintainer.'
                        : 'Product ' + product.id + ' accepted and sent to the admin.')
                    : 'Product ' + product.id + ' rejected and sent back to the manager.');
                this.isError.set(false);
                this.reasons[product.id] = '';
                this.load();
            },
            error: (err) => this.fail(err)
        });
    }

    async adminReview(product: Product, approved: boolean) {
        const reason = this.reasons[product.id];
        if (!approved && (!reason || !reason.trim())) {
            this.message.set('A rejection reason is required.');
            this.isError.set(true);
            return;
        }
        const action = approved ? 'approve' : 'reject';
        if (!(await this.confirmDialog.ask('Are you sure you want to ' + action + ' this product?'))) {
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
            },
            error: (err) => this.fail(err)
        });
    }

    async addQuantity(product: Product) {
        const amount = this.amounts[product.id];
        if (!amount || amount < 1) {
            this.message.set('Quantity must be at least 1.');
            this.isError.set(true);
            return;
        }
        if (!(await this.confirmDialog.ask('Are you sure you want to update this product\'s stock?'))) {
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

    async updatePrice(product: Product) {
        const price = this.prices[product.id];
        if (price == null || price <= 0) {
            this.message.set('Price must be greater than 0.');
            this.isError.set(true);
            return;
        }
        if (!(await this.confirmDialog.ask('Are you sure you want to update this product\'s price?'))) {
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

    async updateName(product: Product) {
        const name = this.names[product.id];
        if (!name || !name.trim()) {
            this.message.set('Name must not be blank.');
            this.isError.set(true);
            return;
        }
        if (!(await this.confirmDialog.ask('Are you sure you want to update this product\'s name?'))) {
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
