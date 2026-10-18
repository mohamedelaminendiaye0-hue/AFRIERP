import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, switchMap } from 'rxjs';
import { ApiService } from '../core/api.service';
import { CatalogueService } from '../core/catalogue.service';
import { DecompteTaxation, TypeActe } from '../core/models';
import { XofPipe } from '../shared/format.pipes';
import { IconeComponent } from '../shared/icone.component';

@Component({
  selector: 'app-simulateur',
  standalone: true,
  imports: [FormsModule, XofPipe, IconeComponent],
  templateUrl: './simulateur.component.html',
})
export class SimulateurComponent implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly decompte = signal<DecompteTaxation | null>(null);
  protected readonly catalogue = inject(CatalogueService);
  protected typeActe: TypeActe = 'VENTE_IMMOBILIERE';
  protected assiette = 185_000_000;
  protected honoraire = 0;
  protected prixAcquisition: number | null = null;

  private readonly calcul$ = new Subject<void>();

  ngOnInit(): void {
    this.calcul$
      .pipe(
        debounceTime(150),
        switchMap(() => this.api.simuler(this.typeActe, Number(this.assiette) || 0, Number(this.honoraire) || 0, Number(this.prixAcquisition) || null)),
      )
      .subscribe((d) => this.decompte.set(d));
    this.recalculer();
  }

  protected recalculer(): void {
    this.calcul$.next();
  }

  protected libelleType(code: TypeActe): string {
    return this.catalogue.type(code)?.libelle ?? code;
  }

  protected typeChoisi() {
    return this.catalogue.type(this.typeActe);
  }

  protected changerType(): void {
    if (!this.typeChoisi()?.assietteLibelle) this.assiette = 0;
    else if (!this.assiette) this.assiette = 50_000_000;
    this.recalculer();
  }

  protected imprimer(): void {
    window.print();
  }

  protected taux(t: number | null): string {
    return t === null ? 'forfait' : `${(t * 100).toLocaleString('fr-FR', { maximumFractionDigits: 2 })} %`;
  }
}
