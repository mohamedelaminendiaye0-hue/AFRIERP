import { Injectable, computed, inject, signal } from '@angular/core';
import { ApiService } from './api.service';
import { DomaineActe, TypeActeVue } from './models';

export interface GroupeActes {
  domaine: DomaineActe;
  libelle: string;
  types: TypeActeVue[];
}

/** Catalogue des actes, charge une fois depuis l'API et partage entre les ecrans. */
@Injectable({ providedIn: 'root' })
export class CatalogueService {
  private readonly api = inject(ApiService);
  readonly types = signal<TypeActeVue[]>([]);

  readonly groupes = computed<GroupeActes[]>(() => {
    const groupes: GroupeActes[] = [];
    for (const t of this.types()) {
      let g = groupes.find((x) => x.domaine === t.domaine);
      if (!g) groupes.push((g = { domaine: t.domaine, libelle: t.domaineLibelle, types: [] }));
      g.types.push(t);
    }
    return groupes;
  });

  constructor() {
    this.api.typesActe().subscribe((t) => this.types.set(t));
  }

  type(code: string): TypeActeVue | undefined {
    return this.types().find((t) => t.code === code);
  }
}
