import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Title } from '@angular/platform-browser';

import { API } from '../api-config';
import { Product } from '../models';

@Component({
    selector: 'app-product',
    imports: [FormsModule],
    templateUrl: './product.html',
    styleUrl: './product.css'
})
export class ProductComponent implements OnInit {

    private http = inject(HttpClient);
    private title = inject(Title);

    products = signal<Product[]>([]);
    form = {name: '', price: null as number | null, availableQuantity: null as number | null};
    amounts: Record<number, number> = {};
    prices: Record<number, number> = {};
    names: Record<number, string> = {};
    message = signal('');
    isError = signal(false);

    get base() {
        return API.productV1;
    }

    ngOnInit() {
        this.title.setTitle('Products - Microservice UI');
        this.load();
    }

    create() {
        this.http.post<Product>(this.base + '/products', this.form).subscribe({
            next: () => {
                this.message.set('Product created.');
                this.isError.set(false);
                this.form = {name: '', price: null, availableQuantity: null};
                this.load();
            },
            error: (err) => this.fail(err)
        });
    }

    load() {
        this.http.get<Product[]>(this.base + '/products').subscribe({
            next: (data) => this.products.set(data),
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