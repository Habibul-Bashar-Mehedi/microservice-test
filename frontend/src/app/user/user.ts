import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { Title } from '@angular/platform-browser';

import { API } from '../api-config';
import { AuthService } from '../auth.service';
import { ConfirmService } from '../confirm-dialog/confirm.service';
import { IconComponent } from '../ui/icon';
import { StatusTonePipe } from '../ui/pipes';
import { User } from '../models';

type UserFeature = 'create' | 'list';

@Component({
    selector: 'app-user',
    imports: [FormsModule, IconComponent, StatusTonePipe],
    templateUrl: './user.html',
    styleUrl: './user.css'
})
export class UserComponent implements OnInit {

    private http = inject(HttpClient);
    private route = inject(ActivatedRoute);
    private title = inject(Title);
    private auth = inject(AuthService);
    private confirmDialog = inject(ConfirmService);

    role = this.auth.getUser()?.role ?? 'USER';
    feature: UserFeature = 'list';

    users = signal<User[]>([]);
    filter = signal('');
    filteredUsers = computed(() => {
        const q = this.filter();
        if (!q) {
            return this.users();
        }
        return this.users().filter(u =>
            u.name?.toLowerCase().includes(q) ||
            u.email?.toLowerCase().includes(q) ||
            u.role?.toLowerCase().includes(q));
    });
    form = {name: '', email: ''};
    roles = ['USER', 'ADMIN', 'MANAGER', 'MAINTAINER', 'PRODUCT_SPECIALIST', 'SALESMAN'];
    selectedRole: Record<number, string> = {};
    message = signal('');
    isError = signal(false);

    get base() {
        return API.userV1;
    }

    ngOnInit() {
        this.feature = (this.route.snapshot.data['feature'] as UserFeature) ?? 'list';
        this.title.setTitle('Users - Microservice UI');
        this.route.queryParamMap.subscribe(params => this.filter.set((params.get('q') ?? '').trim().toLowerCase()));
        if (this.feature === 'list') {
            this.load();
        }
    }

    async create() {
        if (!(await this.confirmDialog.ask('Are you sure you want to create this user?'))) {
            return;
        }
        this.http.post<User>(this.base + '/users', this.form).subscribe({
            next: () => {
                this.message.set('User created.');
                this.isError.set(false);
                this.form = {name: '', email: ''};
                if (this.feature === 'list') {
                    this.load();
                }
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

    async setActive(u: User, active: boolean) {
        const action = active ? 'activate' : 'deactivate';
        if (!(await this.confirmDialog.ask('Are you sure you want to ' + action + ' this user?'))) {
            return;
        }
        this.http.patch<User>(this.base + '/users/' + u.id + '/active', {active}).subscribe({
            next: () => {
                this.message.set(active ? 'User activated.' : 'User deactivated.');
                this.isError.set(false);
                this.load();
            },
            error: (err) => this.fail(err)
        });
    }

    async changeRole(u: User, role: string) {
        if (role === u.role) {
            return;
        }
        if (!(await this.confirmDialog.ask('Are you sure you want to change this user\'s role?'))) {
            return;
        }
        this.http.patch<User>(this.base + '/users/' + u.id + '/role', {role}).subscribe({
            next: () => {
                this.message.set(u.name + ' is now ' + role + '.');
                this.isError.set(false);
                delete this.selectedRole[u.id];
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
