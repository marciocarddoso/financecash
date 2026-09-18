import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ShellComponent } from './shared/layout/shell.component';

@Component({
  selector: 'fk-root',
  standalone: true,
  imports: [RouterOutlet, ShellComponent],
  template: `
    <fk-shell>
      <router-outlet></router-outlet>
    </fk-shell>
  `,
})
export class AppComponent {}
