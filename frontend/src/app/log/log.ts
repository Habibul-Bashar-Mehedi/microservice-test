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
    clearing = signal(false);
    searchQuery = signal('');
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

    search() {
        const q = this.searchQuery().trim();
        if (!q) {
            this.load();
            return;
        }
        this.http.get<MessageLog[]>(this.base + '/logs/search', {params: {q}}).subscribe({
            next: (data) => this.logs.set(data),
            error: (err) => this.fail(err)
        });
    }

    clearAll() {
        if (this.clearing()) {
            return;
        }
        if (!confirm('Delete all log entries? This cannot be undone.')) {
            return;
        }
        this.clearing.set(true);
        this.http.delete(this.base + '/logs', {responseType: 'text'}).subscribe({
            next: () => {
                this.message.set('All log entries deleted.');
                this.isError.set(false);
                this.clearing.set(false);
                this.load();
            },
            error: (err) => {
                this.clearing.set(false);
                this.fail(err);
            }
        });
    }

    private fail(err: any) {
        this.message.set(err?.error?.message || err?.message || 'Request failed');
        this.isError.set(true);
    }
}