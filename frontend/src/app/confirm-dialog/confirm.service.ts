import { Injectable, signal } from '@angular/core';

export interface ConfirmRequest {
    title: string;
    message: string;
    confirmLabel: string;
}

@Injectable({ providedIn: 'root' })
export class ConfirmService {

    readonly request = signal<ConfirmRequest | null>(null);

    private resolver: ((value: boolean) => void) | null = null;

    ask(message: string, title = 'Please confirm', confirmLabel = 'Confirm'): Promise<boolean> {
        this.resolve(false);
        this.request.set({title, message, confirmLabel});
        return new Promise<boolean>((resolve) => {
            this.resolver = resolve;
        });
    }

    accept() {
        this.resolve(true);
    }

    cancel() {
        this.resolve(false);
    }

    private resolve(value: boolean) {
        const resolver = this.resolver;
        this.resolver = null;
        this.request.set(null);
        resolver?.(value);
    }
}
