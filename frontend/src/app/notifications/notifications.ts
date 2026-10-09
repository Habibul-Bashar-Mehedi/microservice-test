import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Title } from '@angular/platform-browser';

import { API } from '../api-config';
import { IconComponent } from '../ui/icon';
import { ProductNotification } from '../models';

@Component({
    selector: 'app-notifications',
    imports: [IconComponent, DatePipe],
    templateUrl: './notifications.html',
    styleUrl: './notifications.css'
})
export class NotificationsComponent implements OnInit {

    private http = inject(HttpClient);
    private title = inject(Title);

    notifications = signal<ProductNotification[]>([]);
    message = signal('');
    isError = signal(false);

    get base() {
        return API.productV1;
    }

    ngOnInit() {
        this.title.setTitle('Notifications - Microservice UI');
        this.load();
    }

    load() {
        this.http.get<ProductNotification[]>(this.base + '/notifications').subscribe({
            next: (data) => this.notifications.set(data),
            error: (err) => this.fail(err)
        });
    }

    markRead(notification: ProductNotification) {
        this.http.post(this.base + '/notifications/' + notification.id + '/read', {}).subscribe({
            next: () => this.load(),
            error: (err) => this.fail(err)
        });
    }

    markAllRead() {
        const unread = this.notifications().filter(n => !n.read);
        if (unread.length === 0) {
            return;
        }
        for (const notification of unread) {
            this.http.post(this.base + '/notifications/' + notification.id + '/read', {}).subscribe({
                next: () => this.load(),
                error: (err) => this.fail(err)
            });
        }
    }

    private fail(err: any) {
        this.message.set(err?.error?.message || err?.message || 'Request failed');
        this.isError.set(true);
    }
}
