import { Pipe, PipeTransform } from '@angular/core';
import { CreditCardTotal } from '../../core/models/entry.model';

export interface CreditCardTotalsByBank {
  bankName: string;
  total: number;
  cards: CreditCardTotal[];
}

/**
 * Agrupa a quebra por cartão (EntryTotals.creditCardTotals) por banco — o "por banco" do
 * totalizador (01/10, quinta rodada, a pedido do Marcio: "quero ver o totalizador por cartão e
 * por banco"). O backend já manda a lista ordenada por bankName/name, então só precisa juntar em
 * grupos consecutivos, sem precisar reordenar aqui.
 */
@Pipe({ name: 'groupByBank', standalone: true, pure: true })
export class GroupByBankPipe implements PipeTransform {
  transform(cards: CreditCardTotal[] | null | undefined): CreditCardTotalsByBank[] {
    if (!cards || cards.length === 0) return [];
    const groups: CreditCardTotalsByBank[] = [];
    const byBank = new Map<string, CreditCardTotalsByBank>();
    for (const card of cards) {
      let group = byBank.get(card.bankName);
      if (!group) {
        group = { bankName: card.bankName, total: 0, cards: [] };
        byBank.set(card.bankName, group);
        groups.push(group);
      }
      group.total += card.despesa;
      group.cards.push(card);
    }
    return groups;
  }
}
