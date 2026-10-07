import { Component, inject } from '@angular/core';

import { ConfirmService } from './confirm.service';

@Component({
    selector: 'app-confirm-dialog',
    template: `
        @if (confirm.request(); as request) {
            <div class="confirm-backdrop" (click)="confirm.cancel()">
                <div class="confirm-dialog" role="dialog" aria-modal="true"
                     (click)="$event.stopPropagation()">
                    <h3>{{ request.title }}</h3>
                    <p>{{ request.message }}</p>
                    <div class="confirm-actions">
                        <button type="button" (click)="confirm.cancel()">Cancel</button>
                        <button type="button" class="confirm-primary"
                                (click)="confirm.accept()">{{ request.confirmLabel }}</button>
                    </div>
                </div>
            </div>
        }
    `
})
export class ConfirmDialogComponent {
    confirm = inject(ConfirmService);
}
