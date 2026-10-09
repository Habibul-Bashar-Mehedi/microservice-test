import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    if (auth.isAuthenticated()) {
        return true;
    }

    return router.createUrlTree(['/login']);
};

export const adminGuard: CanActivateFn = () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    if (auth.isAuthenticated() && auth.getUser()?.role === 'ADMIN') {
        return true;
    }

    return router.createUrlTree(['/dashboard']);
};

export const roleGuard = (...roles: string[]): CanActivateFn => () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    const role = auth.getUser()?.role;
    if (auth.isAuthenticated() && role != null && roles.includes(role)) {
        return true;
    }

    return router.createUrlTree([homePathFor(role)]);
};

export function homePathFor(role: string | undefined): string {
    switch (role) {
        case 'MAINTAINER':
            return '/product/list';
        case 'MANAGER':
        case 'PRODUCT_SPECIALIST':
        case 'SALESMAN':
        case 'ADMIN':
            return '/product/pending';
        default:
            return '/dashboard';
    }
}

export const dashboardGuard: CanActivateFn = () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    if (!auth.isAuthenticated()) {
        return router.createUrlTree(['/login']);
    }

    const role = auth.getUser()?.role;
    if (role === 'ADMIN' || role === 'MANAGER' || role === 'MAINTAINER'
        || role === 'PRODUCT_SPECIALIST' || role === 'SALESMAN') {
        return router.createUrlTree([homePathFor(role)]);
    }

    return true;
};