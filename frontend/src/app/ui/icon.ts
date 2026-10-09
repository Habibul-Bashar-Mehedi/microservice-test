import { Component, Input } from '@angular/core';

export type IconName =
    | 'dashboard'
    | 'cart'
    | 'orders'
    | 'users'
    | 'products'
    | 'approvals'
    | 'bell'
    | 'logs'
    | 'server'
    | 'search'
    | 'logout'
    | 'menu'
    | 'close'
    | 'chevron-down'
    | 'chevron-left'
    | 'chevron-right'
    | 'arrow-left'
    | 'plus'
    | 'refresh'
    | 'trash'
    | 'check'
    | 'check-circle'
    | 'alert'
    | 'info'
    | 'chat'
    | 'send'
    | 'edit'
    | 'tag'
    | 'external'
    | 'user'
    | 'mail'
    | 'package'
    | 'box'
    | 'clock'
    | 'shield'
    | 'filter'
    | 'wallet';

@Component({
    selector: 'app-icon',
    standalone: true,
    template: `
        <svg
            [attr.width]="size"
            [attr.height]="size"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.75"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
            focusable="false"
        >
            @switch (name) {
                @case ('dashboard') {
                    <rect x="3" y="3" width="7" height="8" rx="1.5" />
                    <rect x="14" y="3" width="7" height="5" rx="1.5" />
                    <rect x="14" y="12" width="7" height="9" rx="1.5" />
                    <rect x="3" y="15" width="7" height="6" rx="1.5" />
                }
                @case ('cart') {
                    <circle cx="9" cy="20" r="1.4" />
                    <circle cx="18" cy="20" r="1.4" />
                    <path d="M2 3h3l2.4 12.2a1.6 1.6 0 0 0 1.6 1.3h8.6a1.6 1.6 0 0 0 1.6-1.3L21 7H6" />
                }
                @case ('orders') {
                    <path d="M5 3h14a1 1 0 0 1 1 1v17l-3-2-2 2-2-2-2 2-2-2-3 2V4a1 1 0 0 1 1-1Z" />
                    <path d="M9 8h6M9 12h6M9 16h3" />
                }
                @case ('users') {
                    <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
                    <circle cx="9" cy="7" r="4" />
                    <path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
                }
                @case ('products') {
                    <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z" />
                    <path d="m3.3 7 8.7 5 8.7-5M12 22V12" />
                }
                @case ('box') {
                    <path d="M21 8 12 3 3 8v8l9 5 9-5V8Z" />
                    <path d="M3 8l9 5 9-5M12 13v8" />
                }
                @case ('package') {
                    <path d="m7.5 4.27 9 5.15" />
                    <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z" />
                    <path d="M3.3 7 12 12l8.7-5M12 22V12" />
                }
                @case ('approvals') {
                    <rect x="8" y="2.5" width="8" height="4" rx="1" />
                    <path d="M16 4.5h2a2 2 0 0 1 2 2V20a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6.5a2 2 0 0 1 2-2h2" />
                    <path d="m9 14 2 2 4-4" />
                }
                @case ('bell') {
                    <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
                    <path d="M13.7 21a2 2 0 0 1-3.4 0" />
                }
                @case ('logs') {
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
                    <path d="M14 2v6h6M16 13H8M16 17H8M10 9H8" />
                }
                @case ('server') {
                    <rect x="2" y="3" width="20" height="7" rx="2" />
                    <rect x="2" y="14" width="20" height="7" rx="2" />
                    <path d="M6 6.5h.01M6 17.5h.01" />
                }
                @case ('search') {
                    <circle cx="11" cy="11" r="7" />
                    <path d="m20 20-3.2-3.2" />
                }
                @case ('logout') {
                    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                    <path d="m16 17 5-5-5-5M21 12H9" />
                }
                @case ('menu') {
                    <path d="M3 6h18M3 12h18M3 18h18" />
                }
                @case ('close') {
                    <path d="M18 6 6 18M6 6l12 12" />
                }
                @case ('chevron-down') {
                    <path d="m6 9 6 6 6-6" />
                }
                @case ('chevron-left') {
                    <path d="m15 18-6-6 6-6" />
                }
                @case ('chevron-right') {
                    <path d="m9 18 6-6-6-6" />
                }
                @case ('arrow-left') {
                    <path d="M19 12H5M12 19l-7-7 7-7" />
                }
                @case ('plus') {
                    <path d="M12 5v14M5 12h14" />
                }
                @case ('refresh') {
                    <path d="M21 12a9 9 0 1 1-2.64-6.36" />
                    <path d="M21 3v6h-6" />
                }
                @case ('trash') {
                    <path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
                    <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M10 11v6M14 11v6" />
                }
                @case ('check') {
                    <path d="M20 6 9 17l-5-5" />
                }
                @case ('check-circle') {
                    <circle cx="12" cy="12" r="9" />
                    <path d="m8.5 12 2.5 2.5 4.5-5" />
                }
                @case ('alert') {
                    <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z" />
                    <path d="M12 9v4M12 17h.01" />
                }
                @case ('info') {
                    <circle cx="12" cy="12" r="9" />
                    <path d="M12 16v-4M12 8h.01" />
                }
                @case ('chat') {
                    <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5Z" />
                }
                @case ('send') {
                    <path d="m22 2-7 20-4-9-9-4Z" />
                    <path d="M22 2 11 13" />
                }
                @case ('edit') {
                    <path d="M12 20h9" />
                    <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z" />
                }
                @case ('tag') {
                    <path d="M20.6 13.4 12 22 2 12V2h10l8.6 8.6a2 2 0 0 1 0 2.8Z" />
                    <path d="M7 7h.01" />
                }
                @case ('external') {
                    <path d="M15 3h6v6M10 14 21 3" />
                    <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
                }
                @case ('user') {
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                    <circle cx="12" cy="7" r="4" />
                }
                @case ('mail') {
                    <rect x="2" y="4" width="20" height="16" rx="2" />
                    <path d="m22 6-10 7L2 6" />
                }
                @case ('clock') {
                    <circle cx="12" cy="12" r="9" />
                    <path d="M12 7v5l3 2" />
                }
                @case ('shield') {
                    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z" />
                }
                @case ('filter') {
                    <path d="M22 3H2l8 9.5V19l4 2v-8.5Z" />
                }
                @case ('wallet') {
                    <path d="M3 7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v1h-4a3 3 0 0 0 0 6h4v1a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z" />
                    <path d="M15 10h6v4h-6a2 2 0 0 1 0-4Z" />
                }
            }
        </svg>
    `,
    styles: [
        `
            :host {
                display: inline-flex;
                align-items: center;
                justify-content: center;
                line-height: 0;
            }
        `,
    ],
})
export class IconComponent {
    @Input({ required: true }) name!: IconName;
    @Input() size = 18;
}
