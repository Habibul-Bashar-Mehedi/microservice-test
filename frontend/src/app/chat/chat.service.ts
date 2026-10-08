import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { API } from '../api-config';
import { ChatResponse } from '../models';

@Injectable({providedIn: 'root'})
export class ChatApiService {

    private http = inject(HttpClient);

    send(message: string, conversationId: string | null): Observable<ChatResponse> {
        return this.http.post<ChatResponse>(API.chatV1 + '/chat', {message, conversationId});
    }

    confirm(conversationId: string, confirmationId: string): Observable<ChatResponse> {
        return this.http.post<ChatResponse>(API.chatV1 + '/chat/confirm', {conversationId, confirmationId});
    }

    cancel(conversationId: string, confirmationId: string): Observable<ChatResponse> {
        return this.http.post<ChatResponse>(API.chatV1 + '/chat/cancel', {conversationId, confirmationId});
    }
}
