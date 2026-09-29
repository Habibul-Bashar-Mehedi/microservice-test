import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Title } from '@angular/platform-browser';

import { API } from '../api-config';
import { MessageLog } from '../models';

@Component({
    selector: 'app-log',
    imports: [FormsModule],
    templateUrl: './log.html',
    styleUrl: './log.css'
})
export class LogComponent implements OnInit {

    private http = inject(HttpClient);
    private title = inject(Title);

    logs = signal<MessageLog[]>([]);
    message = signal('');
    isError = signal(false);
    serviceFilter = signal('all');
    directionFilter = signal('all');
    statusFilter = signal('all');

    filteredLogs = computed(() => {
        const service = this.serviceFilter();
        const direction = this.directionFilter();
        const status = this.statusFilter();
        return this.logs().filter(l =>
            (service === 'all' || l.serviceName === service) &&
            (direction === 'all' || l.direction === direction) &&
            (status === 'all' || l.status === status)
        );
    });

    get base() {
        return API.logV1;
    }

    ngOnInit() {
        this.title.setTitle('Message Logs - Microservice UI');
        this.load();
    }

    load() {
        this.http.get<MessageLog[]>(this.base + '/logs').subscribe({
            next: (data) => this.logs.set(data),
            error: (err) => this.fail(err)
        });
    }

    private fail(err: any) {
        this.message.set(err?.error?.message || err?.message || 'Request failed');
        this.isError.set(true);
    }
}