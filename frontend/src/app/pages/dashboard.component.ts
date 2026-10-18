import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { TableauDeBord } from '../core/models';
import { BadgeNiveauComponent, BadgeStatutComponent, libelleStatut } from '../shared/badges';
import { PourcentagePipe, XofPipe } from '../shared/format.pipes';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, BadgeStatutComponent, BadgeNiveauComponent, XofPipe, PourcentagePipe],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly router = inject(Router);

  protected readonly tableau = signal<TableauDeBord | null>(null);
  protected readonly erreur = signal(false);
  protected readonly libelleStatut = libelleStatut;

  protected readonly maxPipeline = computed(() => Math.max(1, ...(this.tableau()?.pipeline.map((p) => p.dossiers) ?? [1])));
  protected readonly maxCharge = computed(() => Math.max(1, ...(this.tableau()?.charges.map((c) => c.dossiersActifs) ?? [1])));
  protected readonly maxCa = computed(() => Math.max(1, ...(this.tableau()?.caParType.map((c) => c.caPrevisionnel) ?? [1])));

  ngOnInit(): void {
    this.api.tableauDeBord().subscribe({ next: (t) => this.tableau.set(t), error: () => this.erreur.set(true) });
  }
}
