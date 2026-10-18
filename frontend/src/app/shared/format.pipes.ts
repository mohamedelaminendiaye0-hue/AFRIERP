import { Pipe, PipeTransform } from '@angular/core';

const ENTIER = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 });

/** Montant en francs CFA (le XOF n'a pas de decimale). `compact` : 185 M FCFA. */
export function formatXof(montant: number | null | undefined, compact = false): string {
  if (montant === null || montant === undefined) return '—';
  if (compact) {
    const abs = Math.abs(montant);
    if (abs >= 1_000_000_000) return `${(montant / 1_000_000_000).toLocaleString('fr-FR', { maximumFractionDigits: 2 })} Md FCFA`;
    if (abs >= 1_000_000) return `${(montant / 1_000_000).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} M FCFA`;
  }
  return `${ENTIER.format(montant)} FCFA`;
}

@Pipe({ name: 'xof', standalone: true })
export class XofPipe implements PipeTransform {
  transform(montant: number | null | undefined, compact = false): string {
    return formatXof(montant, compact);
  }
}

@Pipe({ name: 'pct', standalone: true })
export class PourcentagePipe implements PipeTransform {
  transform(valeur: number | null | undefined, decimales = 0): string {
    if (valeur === null || valeur === undefined) return '—';
    return `${(valeur * 100).toLocaleString('fr-FR', { maximumFractionDigits: decimales })} %`;
  }
}

/** Date ISO (yyyy-mm-dd ou horodatage) au format 16/09/2026 [10:15]. */
@Pipe({ name: 'dateFr', standalone: true })
export class DateFrPipe implements PipeTransform {
  transform(valeur: string | null | undefined, avecHeure = false): string {
    if (!valeur) return '—';
    const d = new Date(valeur.length === 10 ? `${valeur}T00:00:00` : valeur);
    const date = d.toLocaleDateString('fr-FR');
    return avecHeure ? `${date} ${d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}` : date;
  }
}
