import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ShellComponent } from './shared/layout/shell.component';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'fc-root',
  standalone: true,
  imports: [RouterOutlet, ShellComponent],
  template: `
    @if (authService.isAuthenticated()) {
      <fc-shell>
        <router-outlet></router-outlet>
      </fc-shell>
    } @else {
      <router-outlet></router-outlet>
    }
  `,
})
export class AppComponent {
  protected readonly authService = inject(AuthService);
}
