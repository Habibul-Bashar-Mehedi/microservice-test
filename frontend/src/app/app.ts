import { Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

import { AuthService } from './auth.service';

@Component({
    selector: 'app-root',
    imports: [RouterLink, RouterOutlet],
    templateUrl: './app.html',
    styleUrl: './app.css'
})
export class App {
    protected readonly title = 'Microservice UI';

    private auth = inject(AuthService);

    get isAuthenticated(): boolean {
        return this.auth.isAuthenticated();
    }

    get isAdmin(): boolean {
        return this.auth.getUser()?.role === 'ADMIN';
    }

    logout() {
        this.auth.logout();
    }
}