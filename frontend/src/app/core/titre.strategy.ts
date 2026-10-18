import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { IdentiteService } from './identite.service';

/** Titre d'onglet « Page — Nom de l'étude » (marque blanche). */
@Injectable()
export class TitreStrategy extends TitleStrategy {
  private readonly titre = inject(Title);
  private readonly identite = inject(IdentiteService);

  override updateTitle(etat: RouterStateSnapshot): void {
    const page = this.buildTitle(etat);
    this.titre.setTitle(page ? this.identite.titrePage(page) : this.identite.identite().nom);
  }
}
