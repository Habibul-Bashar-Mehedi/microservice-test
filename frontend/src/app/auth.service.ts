import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';

import { API } from './api-config';

export interface LoginResponse {
    accessToken: string | null;
    tokenType: string | null;
    email: string;
    name: string;
    role: string;
    defaultPassword: string | null;
}

const TOKEN_KEY = 'access_token';
const USER_KEY = 'current_user';

@Injectable({providedIn: 'root'})
export class AuthService {

    private http = inject(HttpClient);
    private router = inject(Router);

    private logoutTimer: ReturnType<typeof setTimeout> | null = null;

    constructor() {
        this.scheduleAutoLogout();
    }

    googleLogin(idToken: string): Observable<LoginResponse> {
        return this.http.post<LoginResponse>(API.authV1 + '/google', {idToken});
    }

    setSession(res: LoginResponse) {
        localStorage.setItem(TOKEN_KEY, res.accessToken ?? '');
        localStorage.setItem(USER_KEY, JSON.stringify({email: res.email, name: res.name, role: res.role}));
        this.scheduleAutoLogout();
    }

    getToken(): string | null {
        return localStorage.getItem(TOKEN_KEY);
    }

    getUser(): {email: string; name: string; role: string} | null {
        const raw = localStorage.getItem(USER_KEY);
        return raw ? JSON.parse(raw) : null;
    }

    isAuthenticated(): boolean {
        if (!this.getToken()) {
            return false;
        }
        const exp = this.getTokenExpiry();
        return exp == null || Date.now() < exp;
    }

    private getTokenExpiry(): number | null {
        const token = this.getToken();
        if (!token) {
            return null;
        }
        try {
            const payload = token.split('.')[1];
            if (!payload) {
                return null;
            }
            const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
            const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
            const json = decodeURIComponent(
                atob(padded).split('').map(c =>
                    '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)
                ).join('')
            );
            const exp = (JSON.parse(json) as {exp?: number}).exp;
            return typeof exp === 'number' ? exp * 1000 : null;
        } catch {
            return null;
        }
    }

    private scheduleAutoLogout() {
        if (this.logoutTimer != null) {
            clearTimeout(this.logoutTimer);
            this.logoutTimer = null;
        }
        const exp = this.getTokenExpiry();
        if (exp == null) {
            return;
        }
        const delay = Math.max(0, exp - Date.now());
        this.logoutTimer = setTimeout(() => this.logout(), delay);
    }

    logout() {
        if (this.logoutTimer != null) {
            clearTimeout(this.logoutTimer);
            this.logoutTimer = null;
        }
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
        this.router.navigate(['/login']);
    }
}