# Modelo de domínio

## Entidades principais

### AppUser
Usuário do sistema. Hoje só você usa, mas todo dado (contas, categorias, lançamentos) é escopado por `AppUser` para já nascer pronto para multiusuário (app mobile com contas free/pagas).

### Account (Conta)
Uma conta bancária ou carteira de aplicação (ex.: "Nubank", "Itaú Corrente", "XP - CDI 110%"). Campos principais: `name`, `bankName`, `type` (`CORRENTE`, `POUPANCA`, `INVESTIMENTO`), `active`.

### BalanceSnapshot (Saldo consolidado)
Uma "fotografia" do saldo de uma `Account` em uma data (`referenceDate`, `balance`). Em vez de recalcular saldo a partir de todos os lançamentos desde o início (o que a planilha faz de forma manual e sujeita a erro), o usuário registra o saldo real do banco periodicamente (ex.: toda vez que confere o extrato) e o sistema usa o snapshot mais recente de cada conta como base — igual ao que se faz manualmente hoje. Preparado para, no futuro, ser alimentado automaticamente via Open Finance.

### Category (Categoria)
Categoria de gasto/receita (ex.: "Mercado", "Farmácia", "Passeios", "Salário", "Décimo Terceiro", "Transferência - Filhas"). Campo `type` (`RECEITA`/`DESPESA`) e `colorHex` (para os relatórios/gráficos no frontend).

### Entry (Lançamento)
O lançamento em si — equivalente a uma linha na planilha atual. Campos:
- `description`, `amount`, `dueDate`, `paymentDate` (nulo até ser pago), `status` (`PENDENTE`, `PAGO`, `ATRASADO`, `CANCELADO`)
- `type` (`RECEITA`/`DESPESA`)
- `category` (FK)
- `account` (FK, opcional — de qual conta saiu/entrou)
- `origin` (`MANUAL`, `RECORRENCIA`, `PARCELAMENTO`, `IMPORTADO_BOLETO`, `IMPORTADO_CARTAO`) — permite saber se o lançamento foi digitado à mão ou gerado automaticamente
- `recurringRule` (FK opcional — se veio de uma recorrência)
- `installmentPlan` + `installmentNumber` (FK opcional + número da parcela, ex. "3/12" — se veio de um parcelamento)
- `creditCard` (FK opcional — se é uma compra no cartão)

### RecurringRule (Regra recorrente)
Representa algo que se repete: salário, aluguel, assinatura, conta de luz (valor variável mas recorrente), décimo terceiro salário. Campos:
- `name`, `type` (`RECEITA`/`DESPESA`), `category`
- `frequency` (`MENSAL`, `BIMESTRAL`, `SEMESTRAL`, `ANUAL`)
- `dayOfMonth` (dia de vencimento) e, para `ANUAL`/`SEMESTRAL`, `referenceMonths` (em quais meses ocorre — ex. décimo terceiro em novembro e dezembro)
- `active`, `startDate`, `endDate` (opcional)

### RecurringRuleValueHistory (Histórico de valor)
Como o valor de uma conta recorrente muda com reajustes (anual/semestral), o valor não fica fixo na regra — fica em um histórico com `effectiveFrom` (a partir de quando vale) e `amount`. Ao gerar lançamentos futuros, o sistema usa o valor vigente na data de cada ocorrência. Isso resolve exatamente o caso citado: "algumas variáveis que sempre ocorrem os ajustes anuais ou semestrais".

### InstallmentPlan (Plano de parcelamento)
Uma compra parcelada. Campos: `description`, `totalAmount`, `installmentsCount`, `firstDueDate`, `category`, `account`/`creditCard`. Ao salvar, o backend gera automaticamente os `Entry` de cada parcela (parcela = `totalAmount / installmentsCount`, com tratamento de arredondamento na última parcela).

### CreditCard (Cartão de crédito)
Um cartão vinculado a um banco/emissor (`bankName`, `name` ex. "Nubank Ultravioleta", `closingDay`, `dueDay`). Cada compra no cartão é um `Entry` com `origin=IMPORTADO_CARTAO` ou `MANUAL` e `creditCard` preenchido — permite consolidar "quanto vou pagar de fatura desse cartão esse mês" e, por banco, comparar gasto entre cartões.

### Transfer (Transferência / PIX)
Registrada como um `Entry` do tipo `DESPESA` com `category` apropriada (ex. "Transferência - Filha 1"), mas com um complemento `Transfer` (`recipientName`, `pixKeyMasked`, `recurring` boolean) para diferenciar no relatório "quanto enviei de PIX esse mês e para quem", sem misturar com despesas de consumo.

## Diagrama (simplificado)

```
AppUser 1───* Account 1───* BalanceSnapshot
   │
   ├──* Category
   │
   ├──* RecurringRule 1───* RecurringRuleValueHistory
   │        │
   │        └──* Entry (origin=RECORRENCIA)
   │
   ├──* InstallmentPlan
   │        │
   │        └──* Entry (origin=PARCELAMENTO)
   │
   ├──* CreditCard
   │        │
   │        └──* Entry (creditCard != null)
   │
   └──* Entry ──0..1 Transfer
```

## Por que `Entry` é a entidade central em vez de eventos separados por tipo

Poderia existir `Salary`, `Bill`, `CardPurchase`, `PixTransfer` como entidades separadas — mas isso multiplicaria consultas para responder "quanto eu gastei esse mês" ou "quais contas vencem hoje", que precisam olhar tudo junto. Por isso o modelo usa **uma entidade `Entry` central** com campos que indicam a origem/natureza (`origin`, `recurringRule`, `installmentPlan`, `creditCard`, `transfer`), e entidades auxiliares só para o que é específico de cada mecanismo (como a regra gera, como a parcela é dividida). Isso é o mesmo padrão de "lançamento único, tags de origem" que qualquer planilha de controle financeiro usa na prática.
