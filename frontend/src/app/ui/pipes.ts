import { Pipe, PipeTransform } from '@angular/core';

/**
 * Maps a backend status/state string to a badge tone class.
 * Purely presentational — does not change any business logic.
 */
@Pipe({ name: 'statusTone', standalone: true })
export class StatusTonePipe implements PipeTransform {
    transform(value: string | null | undefined): string {
        const s = (value ?? '').toUpperCase();
        if (s === '') {
            return 'badge badge-neutral';
        }
        if (
            s === 'APPROVED' ||
            s === 'CONFIRMED' ||
            s === 'SUCCESS' ||
            s === 'ACTIVE' ||
            s === 'UP' ||
            s === 'READ' ||
            s === 'PUBLISHED'
        ) {
            return 'badge badge-success';
        }
        if (
            s.startsWith('REJECTED') ||
            s === 'FAILED' ||
            s === 'DOWN' ||
            s === 'INACTIVE' ||
            s === 'CANCELLED' ||
            s === 'CANCELED'
        ) {
            return 'badge badge-danger';
        }
        if (s === 'PENDING' || s === 'CONFIRMING' || s.startsWith('PENDING_')) {
            return 'badge badge-warning';
        }
        if (s === 'CONSUMED') {
            return 'badge badge-info';
        }
        return 'badge badge-neutral';
    }
}

/**
 * Turns an enum-style status into a human readable label.
 * e.g. PENDING_PRODUCT_SPECIALIST -> "Pending Product Specialist"
 */
@Pipe({ name: 'prettyStatus', standalone: true })
export class PrettyStatusPipe implements PipeTransform {
    transform(value: string | null | undefined): string {
        if (!value) {
            return '—';
        }
        return value
            .toLowerCase()
            .split('_')
            .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
            .join(' ');
    }
}
