import { Routes } from '@angular/router';

import { adminGuard, authGuard, dashboardGuard, roleGuard } from './auth.guard';
import { DashboardComponent } from './dashboard/dashboard';
import { LoginComponent } from './login/login';
import { CartComponent } from './cart/cart';
import { MyOrdersComponent } from './my-orders/my-orders';
import { UserComponent } from './user/user';
import { ProductComponent } from './product/product';
import { NotificationsComponent } from './notifications/notifications';
import { OrderComponent } from './order/order';
import { LogComponent } from './log/log';

export const routes: Routes = [
    { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    { path: 'login', component: LoginComponent },
    { path: 'dashboard', component: DashboardComponent, canActivate: [dashboardGuard] },

    { path: 'cart', component: CartComponent, canActivate: [authGuard, roleGuard('USER')] },
    { path: 'my-orders', component: MyOrdersComponent, canActivate: [authGuard, roleGuard('USER')] },

    {
        path: 'product/add',
        component: ProductComponent,
        data: { feature: 'add' },
        canActivate: [authGuard, roleGuard('MANAGER')]
    },
    {
        path: 'product/list',
        component: ProductComponent,
        data: { feature: 'list' },
        canActivate: [authGuard, roleGuard('MANAGER', 'MAINTAINER')]
    },
    {
        path: 'product/pending',
        component: ProductComponent,
        data: { feature: 'pending' },
        canActivate: [authGuard, roleGuard('MANAGER', 'MAINTAINER', 'ADMIN')]
    },
    {
        path: 'product/stock',
        component: ProductComponent,
        data: { feature: 'stock' },
        canActivate: [authGuard, roleGuard('MAINTAINER', 'ADMIN')]
    },

    {
        path: 'notifications',
        component: NotificationsComponent,
        canActivate: [authGuard, roleGuard('MANAGER', 'MAINTAINER', 'ADMIN')]
    },

    {
        path: 'order/create',
        component: OrderComponent,
        data: { feature: 'create' },
        canActivate: [authGuard, roleGuard('ADMIN')]
    },
    {
        path: 'order/list',
        component: OrderComponent,
        data: { feature: 'list' },
        canActivate: [authGuard, roleGuard('ADMIN', 'MANAGER', 'MAINTAINER')]
    },

    {
        path: 'user/create',
        component: UserComponent,
        data: { feature: 'create' },
        canActivate: [authGuard, adminGuard]
    },
    {
        path: 'user/list',
        component: UserComponent,
        data: { feature: 'list' },
        canActivate: [authGuard, adminGuard]
    },

    { path: 'log', component: LogComponent, canActivate: [authGuard, adminGuard] },

    { path: '**', redirectTo: 'dashboard' }
];
