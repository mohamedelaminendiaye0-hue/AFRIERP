import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { IdentiteService } from '../../core/identite.service';
import { TableauAdmin } from '../../core/models';
import { DateFrPipe } from '../../shared/format.pipes';
import { IconeComponent } from '../../shared/icone.component';

/** Vue d'ensemble du super administrateur : comptes, sécurité, activité, plateforme. */
@Component({
  selector: 'app-admin-tableau',
  standalone: true,
  imports: [RouterLink, DateFrPipe, IconeComponent],
  templateUrl: './admin-tableau.component.html',
  styleUrl: './admin.scss',
})
export class AdminTableauComponent implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly identite = inject(IdentiteService).identite;
  protected readonly tableau = signal<TableauAdmin | null>(null);
  protected readonly maxRole = computed(() => Math.max(1, ...(this.tableau()?.parRole.map((r) => r.actifs) ?? [1])));

  ngOnInit(): void {
    this.api.tableauAdmin().subscribe((t) => this.tableau.set(t));
  }
}
