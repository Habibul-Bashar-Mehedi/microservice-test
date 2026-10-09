import { AfterViewInit, Component, ElementRef, inject, signal, ViewChild } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '../auth.service';
import { homePathFor } from '../auth.guard';
import { GOOGLE_CLIENT_ID } from '../api-config';
import { IconComponent } from '../ui/icon';

declare global {
    interface Window {
        google?: {
            accounts: {
                id: {
                    initialize(config: {
                        client_id: string;
                        callback: (response: {credential: string}) => void;
                        use_fedcm_for_prompt?: boolean;
                        use_fedcm_for_button?: boolean;
                    }): void;
                    renderButton(
                        parent: HTMLElement,
                        options: {theme?: string; size?: string; width?: number}
                    ): void;
                };
            };
        };
    }
}

@Component({
    selector: 'app-login',
    imports: [IconComponent],
    templateUrl: './login.html',
    styleUrl: './login.css'
})
export class LoginComponent implements AfterViewInit {

    @ViewChild('googleButton') googleButton!: ElementRef<HTMLElement>;

    private auth = inject(AuthService);
    private router = inject(Router);

    message = signal('');
    isError = signal(false);

    ngAfterViewInit() {
        const google = window.google;
        if (!google?.accounts) {
            this.message.set('Google Identity Services failed to load.');
            this.isError.set(true);
            return;
        }

        google.accounts.id.initialize({
            client_id: GOOGLE_CLIENT_ID,
            use_fedcm_for_prompt: true,
            use_fedcm_for_button: true,
            callback: (response) => this.onCredential(response.credential)
        });

        google.accounts.id.renderButton(this.googleButton.nativeElement, {
            theme: 'outline',
            size: 'large'
        });
    }

    private onCredential(idToken: string) {
        this.auth.googleLogin(idToken).subscribe({
            next: (res) => {
                this.auth.setSession(res);
                this.isError.set(false);
                this.router.navigate([homePathFor(this.auth.getUser()?.role)]);
            },
            error: (err) => this.fail(err)
        });
    }

    private fail(err: any) {
        this.message.set(err?.error?.message || err?.message || 'Login failed');
        this.isError.set(true);
    }
}