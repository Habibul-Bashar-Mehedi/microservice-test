import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Title } from '@angular/platform-browser';

import { API } from '../api-config';
import { User } from '../models';

@Component({
    selector: 'app-user',
    imports: [FormsModule],
    templateUrl: './user.html',
    styleUrl: './user.css'
})
export class UserComponent implements OnInit {

    private http = inject(HttpClient);
    private title = inject(Title);

    users = signal<User[]>([]);
    form = {name: '', email: ''};
    message = signal('');
    isError = signal(false);

    get base() {
        return API.userV1;
    }

    ngOnInit() {
        this.title.setTitle('Users - Microservice UI');
        this.load();
    }

    create() {
        this.http.post<User>(this.base + '/users', this.form).subscribe({
            next: () => {
                this.message.set('User created.');
                this.isError.set(false);
                this.form = {name: '', email: ''};
                this.load();
            },
            error: (err) => this.fail(err)
        });
    }

    load() {
        this.http.get<User[]>(this.base + '/users').subscribe({
            next: (data) => this.users.set(data),
            error: (err) => this.fail(err)
        });
    }

    private fail(err: any) {
        this.message.set(err?.error?.message || err?.message || 'Request failed');
        this.isError.set(true);
    }
}