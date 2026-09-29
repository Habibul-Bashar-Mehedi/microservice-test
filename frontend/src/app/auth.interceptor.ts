import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
    const auth = inject(AuthService);
    const token = auth.getToken();

    if (token) {
        const cloned = req.clone({
            setHeaders: {Authorization: 'Bearer ' + token}
        });
        return next(cloned).pipe(
            catchError((err: HttpErrorResponse) => {
                if (err.status === 401 && auth.getToken()) {
                    auth.logout();
                }
                return throwError(() => err);
            })
        );
    }

    return next(req);
};