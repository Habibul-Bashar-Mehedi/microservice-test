import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../auth.service';

@Component({
    selector: 'app-login',
    imports: [FormsModule],
    templateUrl: './login.html',
    styleUrl: './login.css'
})
export class LoginComponent {

    private auth = inject(AuthService);
    private router = inject(Router);

    form = {email: '', password: ''};
    message = signal('');
    isError = signal(false);
    showPassword = false;

    togglePassword() {
        this.showPassword = !this.showPassword;
    }

    login() {
        this.auth.login(this.form).subscribe({
            next: (res) => {
                this.auth.setSession(res);
                this.message.set(res.defaultPassword
                    ? 'Welcome back! Your default password is ' + res.defaultPassword
                    : 'Login successful.');
                this.isError.set(false);
                this.router.navigate(['/dashboard']);
            },
            error: (err) => this.fail(err)
        });
    }

    private fail(err: any) {
        this.message.set(err?.error?.message || err?.message || 'Login failed');
        this.isError.set(true);
    }
}