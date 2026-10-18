import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { IdentiteService } from '../core/identite.service';
import { LigneRepertoire, Repertoire } from '../core/models';
import { DateFrPipe, XofPipe } from '../shared/format.pipes';
import { IconeComponent } from '../shared/icone.component';

/**
 * Répertoire des actes : registre légal tenu dans l'ordre chronologique.
 * Chaque acte signé y reçoit un numéro d'ordre définitif ; aucune inscription
 * ne peut être modifiée ni supprimée. Il est présenté chaque trimestre au visa
 * du receveur de l'enregistrement.
 */
@Component({
  selector: 'app-repertoire',
  standalone: true,
  imports: [RouterLink, FormsModule, LowerCasePipe, DateFrPipe, XofPipe, IconeComponent],
  templateUrl: './repertoire.component.html',
  styleUrl: './repertoire.component.scss',
})
export class RepertoireComponent implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly identite = inject(IdentiteService).identite;

  protected readonly repertoire = signal<Repertoire | null>(null);
  protected readonly recherche = signal('');
  protected readonly nature = signal('');
  protected readonly mois = signal('');

  protected readonly natures = computed(() => [...new Set((this.repertoire()?.lignes ?? []).map((l) => l.nature))].sort());
  protected readonly moisDisponibles = computed(() =>
    [...new Set((this.repertoire()?.lignes ?? []).map((l) => l.date?.slice(0, 7)).filter(Boolean) as string[])].sort().reverse(),
  );
  protected readonly lignes = computed(() => {
    const q = normaliser(this.recherche());
    return (this.repertoire()?.lignes ?? []).filter(
      (l) =>
        (!this.nature() || l.nature === this.nature()) &&
        (!this.mois() || l.date?.startsWith(this.mois())) &&
        (!q || normaliser([l.numero, l.nature, l.dossierId, ...l.parties.map((p) => p.nom)].join(' ')).includes(q)),
    );
  });
  protected readonly trimestreCourant = computed(() => {
    const t = this.repertoire()?.trimestres ?? [];
    return t.length ? t[t.length - 1] : null;
  });

  ngOnInit(): void {
    this.charger();
  }

  protected charger(annee?: number): void {
    this.api.repertoire(annee).subscribe((r) => this.repertoire.set(r));
  }

  protected libelleMois(m: string): string {
    return new Date(`${m}-01T00:00:00`).toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
  }

  protected imprimer(): void {
    window.print();
  }

  protected exporter(): void {
    const entetes = ["N° d'ordre", 'Date', "Nature de l'acte", 'Forme', 'Parties', 'Enregistrement', 'Droits perçus (FCFA)', 'Notaire'];
    const cellules = (l: LigneRepertoire) => [
      l.numero,
      l.date ?? '',
      l.nature,
      l.forme,
      l.parties.map((p) => `${p.nom} (${p.qualite})`).join(' ; '),
      l.enregistrement ? `${l.enregistrement.reference ?? ''} ${l.enregistrement.dateAchevement ?? ''}`.trim() : 'À enregistrer',
      l.droitsEnregistrement?.toString() ?? '',
      l.notaire ?? '',
    ];
    const csv = [entetes, ...this.lignes().map(cellules)]
      .map((ligne) => ligne.map((c) => `"${c.replace(/"/g, '""')}"`).join(';'))
      .join('\r\n');
    const lien = document.createElement('a');
    lien.href = URL.createObjectURL(new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' }));
    lien.download = `repertoire-${this.repertoire()?.annee}.csv`;
    lien.click();
    URL.revokeObjectURL(lien.href);
  }
}

function normaliser(s: string): string {
  return s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
}
