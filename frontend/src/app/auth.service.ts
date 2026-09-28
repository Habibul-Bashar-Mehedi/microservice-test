import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';

import { API } from './api-config';

export interface LoginRequest {
    email: string;
    password: string;
}

export interface RegisterRequest {
    name: string;
    email: string;
    password: string;
}

export interface LoginResponse {
    accessToken: string;
    tokenType: string;
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

    login(credentials: LoginRequest): Observable<LoginResponse> {
        return this.http.post<LoginResponse>(API.authV1 + '/login', credentials);
    }

    register(data: RegisterRequest): Observable<LoginResponse> {
        return this.http.post<LoginResponse>(API.authV1 + '/register', data);
    }

    me(): Observable<LoginResponse> {
        return this.http.get<LoginResponse>(API.authV1 + '/me');
    }

    setSession(res: LoginResponse) {
        localStorage.setItem(TOKEN_KEY, res.accessToken);
        localStorage.setItem(USER_KEY, JSON.stringify({email: res.email, name: res.name, role: res.role}));
    }

    getToken(): string | null {
        return localStorage.getItem(TOKEN_KEY);
    }

    getUser(): {email: string; name: string; role: string} | null {
        const raw = localStorage.getItem(USER_KEY);
        return raw ? JSON.parse(raw) : null;
    }

    isAuthenticated(): boolean {
        return !!this.getToken();
    }

    logout() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
        this.router.navigate(['/login']);
    }
}