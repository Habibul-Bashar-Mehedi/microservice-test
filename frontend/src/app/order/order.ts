import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Title } from '@angular/platform-browser';

import { API } from '../api-config';
import { Order } from '../models';

type ApiVersion = 'v1' | 'v2';

@Component({
    selector: 'app-order',
    imports: [FormsModule],
    templateUrl: './order.html',
    styleUrl: './order.css'
})
export class OrderComponent implements OnInit {

    private http = inject(HttpClient);
    private title = inject(Title);

    orders = signal<Order[]>([]);
    form = {userId: null as number | null, productId: null as number | null, quantity: null as number | null};
    message = signal('');
    isError = signal(false);
    creating = signal(false);
    apiVersion = signal<ApiVersion>('v1');

    get base() {
        return this.apiVersion() === 'v1' ? API.orderV1 : API.orderV2;
    }

    ngOnInit() {
        this.title.setTitle('Orders - Microservice UI');
        this.load();
    }

    setVersion(version: ApiVersion) {
        this.apiVersion.set(version);
        this.load();
    }

    create() {
        if (this.creating()) {
            return;
        }

        const version = this.apiVersion();
        this.creating.set(true);
        this.http.post<Order>(this.base + '/orders', this.form).subscribe({
            next: () => {
                this.message.set(version === 'v2'
                    ? 'Order created (PENDING). User validation happens asynchronously.'
                    : 'Order created (PENDING).');
                this.isError.set(false);
                this.form = {userId: null, productId: null, quantity: null};
                this.creating.set(false);
                this.load();
            },
            error: (err) => {
                this.creating.set(false);
                this.fail(err);
            }
        });
    }

    confirm(id: number) {
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

    private fail(err: any) {
        this.message.set(err?.error?.message || err?.message || 'Request failed');
        this.isError.set(true);
    }
}