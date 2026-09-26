import { Routes } from '@angular/router';

import { UserComponent } from './user/user';
import { ProductComponent } from './product/product';
import { OrderComponent } from './order/order';

export const routes: Routes = [
    { path: '', redirectTo: 'user', pathMatch: 'full' },
    { path: 'user', component: UserComponent },
    { path: 'product', component: ProductComponent },
    { path: 'order', component: OrderComponent }
];