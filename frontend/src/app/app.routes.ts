import { Routes } from '@angular/router';

import { adminGuard, authGuard } from './auth.guard';
import { DashboardComponent } from './dashboard/dashboard';
import { LoginComponent } from './login/login';
import { RegisterComponent } from './register/register';
import { UserComponent } from './user/user';
import { ProductComponent } from './product/product';
import { OrderComponent } from './order/order';
import { LogComponent } from './log/log';

export const routes: Routes = [
    { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    { path: 'login', component: LoginComponent },
    { path: 'register', component: RegisterComponent },
    { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },
    { path: 'user', component: UserComponent, canActivate: [authGuard, adminGuard] },
    { path: 'product', component: ProductComponent, canActivate: [authGuard, adminGuard] },
    { path: 'order', component: OrderComponent, canActivate: [authGuard, adminGuard] },
    { path: 'log', component: LogComponent, canActivate: [authGuard, adminGuard] }
];