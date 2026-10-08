import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Title } from '@angular/platform-browser';

interface ServiceInfo {
    name: string;
    port: number;
    desc: string;
    status: string;
}

@Component({
    selector: 'app-services',
    templateUrl: './services.html',
    styleUrl: './services.css'
})
export class ServicesComponent implements OnInit {

    private http = inject(HttpClient);
    private title = inject(Title);

    services = signal<ServiceInfo[]>([
        {name: 'Auth Service', port: 8080, desc: 'Google login · role claims', status: 'checking'},
        {name: 'User Service', port: 8081, desc: 'Profiles · role administration', status: 'checking'},
        {name: 'Product Service', port: 8082, desc: 'Products · approval flow · stock', status: 'checking'},
        {name: 'Order Service', port: 8083, desc: 'Orders · cart', status: 'checking'},
        {name: 'Log Service', port: 8084, desc: 'Message publish/consume logs', status: 'checking'},
        {name: 'Chatbot Service', port: 8085, desc: 'Role-based AI assistant', status: 'checking'}
    ]);

    ngOnInit() {
        this.title.setTitle('Services - Microservice UI');
        this.refresh();
    }

    refresh() {
        this.services.update(list => list.map(s => ({...s, status: 'checking'})));
        this.services().forEach(service => {
            this.http.get<{status: string}>(this.healthUrl(service)).subscribe({
                next: health => this.setStatus(service.port, health?.status ?? 'UP'),
                error: () => this.setStatus(service.port, 'DOWN')
            });
        });
    }

    swaggerUrl(service: ServiceInfo): string {
        return `http://localhost:${service.port}/swagger-ui/index.html`;
    }

    openApiUrl(service: ServiceInfo): string {
        return `http://localhost:${service.port}/v3/api-docs`;
    }

    healthUrl(service: ServiceInfo): string {
        return `http://localhost:${service.port}/actuator/health`;
    }

    private setStatus(port: number, status: string) {
        this.services.update(list => list.map(s => s.port === port ? {...s, status} : s));
    }
}
