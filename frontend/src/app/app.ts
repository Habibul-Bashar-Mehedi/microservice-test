import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { API } from './api-config';
import { AuthService } from './auth.service';
import { ConfirmDialogComponent } from './confirm-dialog/confirm-dialog.component';
import { MessageLog, Order, Product, User } from './models';

interface NavItem {
    label: string;
    link: string;
}

type SearchContext = 'products' | 'users' | 'orders' | 'logs';

interface SuggestionItem {
    label: string;
    sub: string;
    query: string;
}

function matches(lower: string, ...values: (string | number | null | undefined)[]): boolean {
    return values.some(v => v != null && String(v).toLowerCase().includes(lower));
}

@Component({
    selector: 'app-root',
    imports: [RouterLink, RouterLinkActive, RouterOutlet, FormsModule, ConfirmDialogComponent],
    templateUrl: './app.html',
    styleUrl: './app.css'
})
export class App {
    protected readonly title = 'Microservice UI';

    private auth = inject(AuthService);
    private router = inject(Router);
    private http = inject(HttpClient);

    navSearch = '';
    accountOpen = signal(false);
    suggestions = signal<SuggestionItem[]>([]);
    showSuggestions = signal(false);
    context = signal<SearchContext>('products');
    currentPath = signal(this.router.url.split('?')[0]);

    private searchTimer: ReturnType<typeof setTimeout> | null = null;
    private hideTimer: ReturnType<typeof setTimeout> | null = null;
    private searchSeq = 0;
    private userProfileId: number | null = null;
    private profileLookup: Promise<number | null> | null = null;

    constructor() {
        this.context.set(this.contextForUrl(this.router.url));
        this.router.events.pipe(filter(event => event instanceof NavigationEnd)).subscribe(() => {
            this.currentPath.set(this.router.url.split('?')[0]);
            this.context.set(this.contextForUrl(this.router.url));
            this.navSearch = '';
            this.suggestions.set([]);
            this.showSuggestions.set(false);
        });
    }

    get isAuthenticated(): boolean {
        return this.auth.isAuthenticated();
    }

    get showSearch(): boolean {
        return this.isAuthenticated && this.currentPath() !== '/login';
    }

    get user() {
        return this.auth.getUser();
    }

    get role(): string {
        return this.auth.getUser()?.role ?? 'USER';
    }

    get searchPlaceholder(): string {
        switch (this.context()) {
            case 'users':
                return 'Search users...';
            case 'orders':
                return 'Search orders...';
            case 'logs':
                return 'Search logs...';
            default:
                return 'Search products...';
        }
    }

    get navItems(): NavItem[] {
        switch (this.role) {
            case 'MANAGER':
                return [
                    {label: 'Add Product', link: '/product/add'},
                    {label: 'Product List', link: '/product/list'},
                    {label: 'Pending Approvals', link: '/product/pending'},
                    {label: 'Notifications', link: '/notifications'},
                    {label: 'Orders', link: '/order/list'}
                ];
            case 'MAINTAINER':
                return [
                    {label: 'Product List', link: '/product/list'},
                    {label: 'Pending Approvals', link: '/product/pending'},
                    {label: 'Stock Management', link: '/product/stock'},
                    {label: 'Notifications', link: '/notifications'},
                    {label: 'Orders', link: '/order/list'}
                ];
            case 'ADMIN':
                return [
                    {label: 'Pending Approvals', link: '/product/pending'},
                    {label: 'All Products', link: '/product/stock'},
                    {label: 'Notifications', link: '/notifications'},
                    {label: 'Orders', link: '/order/list'},
                    {label: 'Create Order', link: '/order/create'},
                    {label: 'Users', link: '/user/list'},
                    {label: 'Create User', link: '/user/create'},
                    {label: 'Logs', link: '/log'}
                ];
            default:
                return [
                    {label: 'Dashboard', link: '/dashboard'},
                    {label: 'Cart', link: '/cart'},
                    {label: 'My Orders', link: '/my-orders'}
                ];
        }
    }

    logout() {
        this.accountOpen.set(false);
        this.auth.logout();
    }

    toggleAccount() {
        this.accountOpen.set(!this.accountOpen());
    }

    onSearchInput() {
        if (this.hideTimer) {
            clearTimeout(this.hideTimer);
            this.hideTimer = null;
        }
        if (this.searchTimer) {
            clearTimeout(this.searchTimer);
        }

        const q = this.navSearch.trim();
        if (!q) {
            this.suggestions.set([]);
            this.showSuggestions.set(false);
            return;
        }

        this.searchTimer = setTimeout(() => this.fetchSuggestions(q), 120);
    }

    private fetchSuggestions(q: string) {
        const seq = ++this.searchSeq;
        const lower = q.toLowerCase();
        const apply = (items: SuggestionItem[]) => {
            if (seq !== this.searchSeq) {
                return;
            }
            this.suggestions.set(items.slice(0, 8));
            this.showSuggestions.set(items.length > 0);
        };

        switch (this.context()) {
            case 'users':
                this.http.get<User[]>(API.userV1 + '/users').subscribe({
                    next: (users) => apply(users
                        .filter(u => matches(lower, u.name, u.email, u.role))
                        .map(u => ({label: u.name, sub: u.email, query: u.name}))),
                    error: () => apply([])
                });
                break;
            case 'orders':
                if (this.role === 'USER') {
                    this.fetchOwnOrderSuggestions(lower, apply);
                } else {
                    this.http.get<Order[]>(API.orderV1 + '/orders').subscribe({
                        next: (orders) => apply(orders
                            .filter(o => matches(lower, o.id, o.userName, o.userEmail, o.productId, o.status))
                            .map(o => ({label: 'Order #' + o.id, sub: o.userName ?? ('User ' + o.userId), query: String(o.id)}))),
                        error: () => apply([])
                    });
                }
                break;
            case 'logs':
                this.http.get<MessageLog[]>(API.logV1 + '/logs/search', {params: {q}}).subscribe({
                    next: (logs) => apply(logs
                        .map(l => ({label: l.routingKey || l.serviceName, sub: l.detail ?? '', query: q}))),
                    error: () => apply([])
                });
                break;
            default:
                this.http.get<Product[]>(API.productV1 + '/products/search', {params: {q}}).subscribe({
                    next: (products) => apply(products
                        .map(p => ({label: p.name, sub: String(p.price), query: p.name}))),
                    error: () => apply([])
                });
        }
    }

    private fetchOwnOrderSuggestions(lower: string, apply: (items: SuggestionItem[]) => void) {
        this.getProfileId().then(id => {
            if (id == null) {
                apply([]);
                return;
            }
            this.http.get<Order[]>(API.orderV1 + '/orders/user/' + id).subscribe({
                next: (orders) => apply(orders
                    .filter(o => matches(lower, o.id, o.productId, o.status))
                    .map(o => ({label: 'Order #' + o.id, sub: 'Product ' + o.productId, query: String(o.id)}))),
                error: () => apply([])
            });
        });
    }

    private getProfileId(): Promise<number | null> {
        if (this.userProfileId != null) {
            return Promise.resolve(this.userProfileId);
        }
        if (this.profileLookup) {
            return this.profileLookup;
        }
        const email = this.user?.email;
        if (!email) {
            return Promise.resolve(null);
        }
        this.profileLookup = new Promise(resolve => {
            this.http.get<{id: number}>(API.userV1 + '/users/email/' + encodeURIComponent(email)).subscribe({
                next: (profile) => {
                    this.userProfileId = profile.id;
                    resolve(profile.id);
                },
                error: () => resolve(null)
            });
        });
        return this.profileLookup;
    }

    private contextForUrl(url: string): SearchContext {
        const path = url.split('?')[0];
        if (path.startsWith('/user')) {
            return 'users';
        }
        if (path.startsWith('/order') || path.startsWith('/my-orders')) {
            return 'orders';
        }
        if (path.startsWith('/log')) {
            return 'logs';
        }
        return 'products';
    }

    private contextPage(): string {
        switch (this.context()) {
            case 'users':
                return '/user/list';
            case 'orders':
                return this.role === 'USER' ? '/my-orders' : '/order/list';
            case 'logs':
                return '/log';
            default:
                if (this.role === 'ADMIN') {
                    return '/product/stock';
                }
                if (this.role === 'MANAGER' || this.role === 'MAINTAINER') {
                    return '/product/list';
                }
                return '/dashboard';
        }
    }

    selectSuggestion(suggestion: SuggestionItem) {
        this.navSearch = suggestion.query;
        this.showSuggestions.set(false);
        this.router.navigate([this.contextPage()], {queryParams: {q: suggestion.query}});
    }

    hideSuggestions() {
        this.hideTimer = setTimeout(() => this.showSuggestions.set(false), 150);
    }

    submitSearch() {
        this.showSuggestions.set(false);
        const q = this.navSearch.trim();
        this.router.navigate([this.contextPage()], q ? {queryParams: {q}} : {});
    }
}
