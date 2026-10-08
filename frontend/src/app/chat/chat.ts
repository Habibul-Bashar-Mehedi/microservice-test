import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ChatApiService } from './chat.service';
import { AuthService } from '../auth.service';
import { ChatResponse } from '../models';

interface ChatMessage {
    role: 'user' | 'assistant';
    text: string;
}

@Component({
    selector: 'app-chatbot',
    imports: [FormsModule],
    templateUrl: './chat.html',
    styleUrl: './chat.css'
})
export class ChatbotComponent {

    private api = inject(ChatApiService);
    private auth = inject(AuthService);

    open = signal(false);
    sending = signal(false);
    messages = signal<ChatMessage[]>([
        {role: 'assistant', text: 'Hi! I can help with products, approvals and orders based on your role.'}
    ]);

    draft = '';
    conversationId: string | null = null;
    pending: {id: string; summary: string} | null = null;

    get role(): string {
        return this.auth.getUser()?.role ?? 'USER';
    }

    toggle(): void {
        this.open.update(value => !value);
        if (this.open() && this.messages().length === 0) {
            this.messages.set([{role: 'assistant', text: 'Hi! How can I help?'}]);
        }
    }

    send(): void {
        const text = this.draft.trim();
        if (!text || this.sending()) {
            return;
        }
        this.push('user', text);
        this.draft = '';
        this.sending.set(true);
        this.api.send(text, this.conversationId).subscribe({
            next: (response) => this.handle(response),
            error: (err) => this.fail(err)
        });
    }

    confirm(): void {
        if (!this.pending || !this.conversationId || this.sending()) {
            return;
        }
        const confirmationId = this.pending.id;
        this.pending = null;
        this.push('user', 'Confirm');
        this.sending.set(true);
        this.api.confirm(this.conversationId, confirmationId).subscribe({
            next: (response) => this.handle(response),
            error: (err) => this.fail(err)
        });
    }

    cancel(): void {
        if (!this.pending || !this.conversationId || this.sending()) {
            return;
        }
        const confirmationId = this.pending.id;
        this.pending = null;
        this.push('user', 'Cancel');
        this.sending.set(true);
        this.api.cancel(this.conversationId, confirmationId).subscribe({
            next: (response) => this.handle(response),
            error: (err) => this.fail(err)
        });
    }

    private handle(response: ChatResponse): void {
        this.conversationId = response.conversationId;
        this.push('assistant', response.reply);
        this.pending = response.requiresConfirmation && response.confirmationId
            ? {id: response.confirmationId, summary: response.confirmationSummary ?? ''}
            : null;
        this.sending.set(false);
    }

    private fail(err: any): void {
        this.push('assistant', err?.error?.message || err?.message || 'Request failed');
        this.sending.set(false);
    }

    private push(role: 'user' | 'assistant', text: string): void {
        this.messages.update(list => [...list, {role, text}]);
    }
}
