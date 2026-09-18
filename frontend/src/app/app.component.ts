import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ShellComponent } from './shared/layout/shell.component';

@Component({
  selector: 'fc-root',
  standalone: true,
  imports: [RouterOutlet, ShellComponent],
  template: `
    <fc-shell>
      <router-outlet></router-outlet>
    </fc-shell>
  `,
})
export class AppComponent {}
