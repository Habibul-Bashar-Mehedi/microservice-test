import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AuthService } from '../auth.service';

@Component({
    selector: 'app-register',
    imports: [FormsModule],
    templateUrl: './register.html',
    styleUrl: './register.css'
})
export class RegisterComponent {

    private auth = inject(AuthService);

    form = {name: '', email: '', password: '', confirmPassword: ''};
    message = signal('');
    isError = signal(false);
    showPassword = false;

    togglePassword() {
        this.showPassword = !this.showPassword;
    }

    register() {
        if (this.form.password !== this.form.confirmPassword) {
            this.message.set('Passwords do not match.');
            this.isError.set(true);
            return;
        }

        this.auth.register(this.form).subscribe({
            next: () => {
                this.message.set('Registration successful. Please wait for admin to activate your account.');
                this.isError.set(false);
                this.form = {name: '', email: '', password: '', confirmPassword: ''};
            },
            error: (err) => this.fail(err)
        });
    }

    private fail(err: any) {
        this.message.set(err?.error?.message || err?.message || 'Registration failed');
        this.isError.set(true);
    }
}