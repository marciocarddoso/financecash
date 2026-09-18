import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { NotificationPreferences } from '../models/notification-preferences.model';

@Injectable({ providedIn: 'root' })
export class NotificationPreferencesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/me/notification-preferences`;

  get(): Observable<NotificationPreferences> {
    return this.http.get<NotificationPreferences>(this.baseUrl);
  }

  update(preferences: NotificationPreferences): Observable<NotificationPreferences> {
    return this.http.put<NotificationPreferences>(this.baseUrl, preferences);
  }
}
