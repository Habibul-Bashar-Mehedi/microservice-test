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

    private fail(err: any) {
        this.message.set(err?.error?.message || err?.message || 'Request failed');
        this.isError.set(true);
    }
}