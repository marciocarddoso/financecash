# Open Finance e Automação de Boletos

Nota de arquitetura sobre as duas frentes de automação de lançamento do roadmap
(Fase 2): trazer boletos automaticamente para o sistema, e ler saldos/extratos dos
bancos via Open Finance em vez de digitar tudo manualmente. Este documento existe
para não perder o raciocínio por trás das decisões abaixo — releia antes de
começar a implementar qualquer uma das duas fases seguintes.

## 1. Boletos — o que já existe e o que falta

### 1.1 Fase atual (implementada): leitura de linha digitável

`BoletoLinhaDigitavelParser` (`backend/src/main/java/br/com/financecash/boleto/`)
decodifica a linha digitável de 47 dígitos de um boleto de cobrança (não cobre
boleto de concessionária/convênio, que usa 48 dígitos e outro algoritmo) e
devolve banco, valor e data de vencimento. Exposto em
`POST /api/boletos/parse-linha-digitavel`.

Isso já resolve boa parte da dor: em vez de abrir o boleto, olhar o valor e a
data e digitar um lançamento do zero, o usuário cola a linha digitável (a
sequência de números embaixo do código de barras, que dá pra copiar do
app do banco/e-mail sem precisar escanear nada) e o formulário de lançamento
manual já vem pré-preenchido. Zero dependência externa, zero credencial, funciona
hoje.

**Detalhe importante para quem for mexer nesse código**: em 22/02/2025 a
FEBRABAN mudou a regra do "fator de vencimento" (o campo de 4 dígitos que
codifica a data) porque a base antiga (07/10/1997) estourou o limite de 9999
justamente nessa data. A partir de 22/02/2025 o fator reinicia em 1000 e
incrementa 1 por dia. O parser assume sempre a era nova — um boleto com fator
abaixo de 1000 é tratado como não suportado, porque seria uma linha emitida
antes da mudança, o que não faz sentido para um lançamento novo sendo criado
hoje. Fonte: [Mudança no fator de vencimento dos boletos em 2025 — kmee.com.br](https://kmee.com.br/blog/mudanca-fator-vencimento-boletos-2025/).

### 1.2 Por que não dá pra "ler o boleto sozinho" hoje

As duas formas realistas de automatizar 100% (sem o usuário colar nada) seriam:

1. **Parser de e-mail**: um job que lê a caixa de entrada, identifica e-mails de
   cobrança de bancos/empresas e extrai o boleto anexo (PDF) ou o link. Viável
   tecnicamente (IMAP + parsing de PDF), mas frágil — cada emissor tem um layout
   de e-mail diferente, muda sem aviso, e dar acesso de leitura ao e-mail pessoal
   é um tipo de permissão que vale pensar duas vezes antes de conceder até para
   o próprio sistema.
2. **Open Finance (Iniciação de Pagamento / API de boletos do banco)**: alguns
   bancos expõem os boletos em aberto de um cliente via Open Finance. É o
   caminho "certo" a médio prazo, mas depende da frente 2 (Open Finance) já
   estar funcionando — ver seção 2.

**Decisão**: não vale a pena investir em parser de e-mail agora. É trabalho
alto/manutenção alta para um ganho que a leitura de linha digitável já cobre
80% (o esforço do usuário cai de "digitar tudo" para "colar uma linha"). Deixar
a leitura automática via Open Finance como evolução natural quando a frente 2
estiver de pé — nesse ponto, os boletos aparecem como dados, sem precisar de
parser nenhum.

## 2. Open Finance — arquitetura recomendada

### 2.1 A pergunta central: virar participante ou usar um agregador?

Pesquisei o modelo de participação do Open Finance Brasil para responder isso
com precisão, em vez de assumir. Existem dois tipos de participante:
instituições **obrigadas** (bancos dos segmentos 1 e 2, instituições de
pagamento com contas de depósito/pré-pagas, iniciadoras de pagamento) e
instituições **voluntárias** (outras instituições financeiras/de pagamento
autorizadas pelo Banco Central que decidem aderir). Não existe uma categoria
"leve" de desenvolvedor independente — participar diretamente exige ser (ou se
tornar) uma instituição financeira/de pagamento autorizada pelo Banco Central,
com todo o aparato de compliance, certificação técnica e governança que isso
implica. Fonte: [Modelo de participação — Open Finance Brasil](https://openfinancebrasil.org.br/modelo-de-participacao/).

Ou seja: virar participante direto do Open Finance **não é viável** para um
projeto pessoal/portfólio, e continua não sendo depois de pesquisar — não é
questão de burocracia, é questão de precisar ser uma instituição regulada.

**Decisão**: usar um agregador de dados que já é participante autorizado, e
consumir a API dele. É exatamente o papel de empresas como a Pluggy, Belvo e
Quanto no mercado brasileiro.

### 2.2 Por que Pluggy especificamente

Pesquisei a oferta atual (setembro/2026) e a Pluggy tem um produto chamado
**Meu Pluggy** que se encaixa quase perfeitamente no caso de uso do
FinanceCash hoje: é um tier **gratuito, sem data de expiração, para uso
pessoal** — conecta as próprias contas bancárias e permite consultar saldo,
contas e extrato/transações por API, com um Client ID/Client Secret obtidos
depois de conectar os bancos em meu.pluggy.ai e criar credenciais em
dashboard.pluggy.ai. Uso comercial (ex.: se o FinanceCash virar um produto que
outras pessoas usam, frente da Fase 5) exigiria migrar para um plano pago — mas
para o uso pessoal atual, que é a prioridade agora, o custo é zero. Fonte:
[Meu Pluggy](https://www.pluggy.ai/meu-pluggy), [Pluggy — Open Finance](https://www.pluggy.ai/produtos/open-finance).

A Pluggy é registrada como Iniciadora de Transação de Pagamento (ITP) junto ao
Banco Central, o que significa que ela é a instituição autorizada por trás da
conexão — o FinanceCash nunca fala diretamente com o Open Finance do Banco
Central, fala com a API da Pluggy, que por sua vez fala com os bancos via Open
Finance.

### 2.3 Desenho proposto (parcialmente implementado — ver progresso na seção 2.4)

```
Banco (via Open Finance) <-> Pluggy (agregador/ITP) <-> FinanceCash backend <-> FinanceCash frontend
```

Novo módulo `openfinance` (paralelo ao módulo `boleto` que já existe), com:

- `PluggyClient`: wrapper HTTP fino sobre a API da Pluggy (autenticação via
  Client ID/Secret trocado por um `apiKey` de curta duração, e depois um fluxo
  de "connect token" para o usuário autorizar a conexão de cada banco pela
  Pluggy Connect Widget — um componente de UI hospedado pela própria Pluggy, o
  FinanceCash não precisa reimplementar a tela de login do banco).
- `BankConnection` (nova entidade): guarda o `itemId` da Pluggy por
  `AppUser` + banco, status da conexão (ativa/expirada/erro), e a data da
  última sincronização — não guarda credencial nenhuma do banco, isso fica
  inteiramente do lado da Pluggy.
- `AccountSyncService`: chama a API da Pluggy periodicamente (job agendado,
  mesmo padrão do `RecurringEntryScheduler`) para trazer saldo atualizado de
  cada `Account` vinculada e gravar um novo `BalanceSnapshot` — isso substitui
  a atualização manual de saldo que existe hoje.
- `TransactionImportService`: usa as transações trazidas pela Pluggy para
  criar `Entry` com `origin = IMPORTADO_CARTAO` (compras de cartão) ou
  `IMPORTADO_EXTRATO` (movimentações de conta corrente/PIX), com o mesmo
  mecanismo de deduplicação por (descrição, data, valor) e categorização
  automática por nome que o `EntryImportService` (importação CSV) já usa.
  Implementado — ver item 8 em 2.4.

Nenhuma dessas classes muda o modelo de domínio existente (`Entry.origin` já
foi pensado para isso — ver `docs/MODELO-DOMINIO.md`); é só uma nova origem de
dado alimentando a mesma tabela `entry`.

### 2.4 Próximos passos concretos

1. [x] Criar conta em meu.pluggy.ai com o CPF do Marcio e conectar os bancos
   (Bradesco, Nubank, C6 Bank e mais um quarto banco — 4 conexões ativas,
   dentro do limite de 5 do tier gratuito).
2. [x] Criar aplicação e credenciais (Client ID/Secret) em dashboard.pluggy.ai
   — **essas credenciais vão para variáveis de ambiente do backend
   (`PLUGGY_CLIENT_ID`/`PLUGGY_CLIENT_SECRET`), nunca para o repositório**,
   seguindo o mesmo cuidado que já tomamos com o token do GitHub. Conector
   "MeuPluggy" ativado no Dashboard (agrega as contas já conectadas em
   meu.pluggy.ai — não usar o conector do banco específico).
3. [x] Implementar `PluggyClient` (`backend/.../openfinance/PluggyClient.java`)
   cobrindo autenticação (`POST /auth`, apiKey cacheado por 2h) e criação de
   Connect Token (`POST /connect_token`) — exposto em
   `POST /api/openfinance/connect-token`. Optou-se por não usar o SDK oficial
   Java da Pluggy (`github.com/pluggyai/pluggy-java`) porque ele é publicado via
   GitHub Packages, o que exigiria autenticação extra no Maven só para resolver
   a dependência — um client HTTP próprio (`RestTemplate`) é mais simples para
   as poucas chamadas que o FinanceCash precisa.
4. [x] `BankConnection` (nova entidade, `backend/.../domain/model/BankConnection.java`,
   migração `V3__bank_connection.sql`): guarda `itemId`, `bankName`, `status`
   (`ATIVA`/`EXPIRADA`/`ERRO`) e `connectedAt`/`lastSyncAt` por `AppUser`.
   Expostos `POST /api/openfinance/connections` (salva uma conexão a partir de
   um `itemId`) e `GET /api/openfinance/connections` (lista as conexões do
   usuário). Ao salvar, o backend chama `PluggyClient.getItem(itemId)` — não
   confia no que o widget manda além do `itemId` — para buscar o nome do banco
   (`connector.name`) e o status reais na Pluggy; o status granular da Pluggy
   (`UPDATED`, `OUTDATED`, `WAITING_USER_ACTION`, etc. — confirmado via
   `docs.pluggy.ai/docs/connect-an-account`) é simplificado para os três
   estados acima porque o usuário só precisa saber "está funcionando",
   "precisa reconectar" ou "tem algo errado".
5. [x] Integrar o widget **Pluggy Connect** no frontend Angular
   (`frontend/.../features/open-finance/open-finance.component.ts`, rota
   `/bancos-conectados`, link "Bancos Conectados" no menu): a tela chama
   `POST /api/openfinance/connect-token`, abre o widget com o `accessToken`
   recebido via `pluggy-connect-sdk` (pacote npm), e no `onSuccess` manda o
   `item.id` para `POST /api/openfinance/connections`.
   **Detalhe que só se confirma checando o pacote instalado, não a doc**: o
   README do `pluggy-connect-sdk` mostra `import PluggyConnect from
   'pluggy-connect-sdk'` (default export), mas os *types* realmente publicados
   na versão 2.14.2 (`dist/main/index.d.ts`) só reexportam `PluggyConnect` como
   *named export* — o import correto é `import { PluggyConnect } from
   'pluggy-connect-sdk'`. Confirmado compilando um arquivo de teste isolado
   antes de usar no componente; com o import default o TypeScript falha com
   "This expression is not constructable."
6. [x] Implementado `listAccounts(itemId)` no `PluggyClient` (`GET
   /accounts?itemId=`), confirmado contra as 4 conexões reais do Marcio — os
   campos reais batem com o esperado: `type`/`subtype` (`BANK` +
   `CHECKING_ACCOUNT`/`SAVINGS_ACCOUNT` para conta corrente/poupança, `CREDIT`
   + `CREDIT_CARD` para cartão), `name`/`marketingName` (nome do banco pra
   contas `BANK`; genérico/sem identidade de banco pra `CREDIT`, ex.:
   "OUTROS", "BANDEIRADO" — confirmado em uso real), `balance`. `listTransactions(accountId, from, to)`
   também foi implementado (`GET /transactions`), usado pelo `TransactionImportService` — ver item 8.
7. [x] `AccountSyncService` implementado
   (`backend/.../application/service/AccountSyncService.java`, exposto em
   `POST /api/openfinance/connections/{id}/sync`, botão "Sincronizar" na tela
   Bancos Conectados) — sincroniza contas tipo `BANK` (corrente/poupança):
   casa por (nome do banco, tipo) já cadastrado em `Account` e substitui o
   `BalanceSnapshot` do dia em vez de duplicar; se não existir, cria a
   `Account`. Atualiza `lastSyncAt` em `BankConnection` a cada sincronização.
   Cartões de crédito (`CREDIT`) **também já sincronizam**: o campo
   `creditData` do endpoint `/accounts` (confirmado em
   docs.pluggy.ai/docs/accounts — `balanceCloseDate`/`balanceDueDate`,
   `brand`, `creditLimit`, `availableCreditLimit`) dá o dia de
   fechamento/vencimento (extraído da data da fatura atual) e a bandeira do
   cartão. Como o nome/marketingName de uma conta `CREDIT` não identifica o
   banco de forma confiável (ex.: "OUTROS", "BANDEIRADO"), o nome do banco é
   descoberto a partir de uma conta `BANK` da mesma conexão (cada
   `BankConnection`/item representa um único banco real) e usado pra casar
   com `CreditCard.bankName` — se não existir nenhuma conta `BANK` na mesma
   conexão, ou faltar `creditData`, o cartão fica marcado como "não
   sincronizado" (`creditCardsSkipped` no retorno do endpoint) em vez de
   arriscar um cadastro errado.

   **Achado em produção, com dados reais**: `creditData.balanceCloseDate`
   veio `null` nos 3 cartões reais testados (Bradesco, C6, Nubank via
   MeuPluggy), mesmo com `balanceDueDate` preenchido — não é bug de parsing
   (o mesmo tipo de campo funcionou pro vencimento), a Pluggy simplesmente
   não manda esse dado nesse conector/tier. Pra não travar a sincronização
   por causa disso: ao atualizar um cartão já cadastrado, só mexe em
   `dueDay` (o `closingDay` que já está lá fica intacto); ao criar um cartão
   novo sem essa data, estima o fechamento como 10 dias antes do vencimento
   (convenção comum, mas é só uma estimativa) e sinaliza isso no retorno do
   endpoint (`creditCardsWithEstimatedClosingDay`) pra tela avisar o
   usuário. Por causa disso, também foi adicionada edição de `CreditCard`
   (nome, banco, dia de fechamento/vencimento — `PUT /api/credit-cards/{id}`,
   botão "Editar" em Cartões), pra corrigir a estimativa com o dado real
   quando o usuário souber.
8. [x] `TransactionImportService` implementado
   (`backend/.../application/service/TransactionImportService.java`), chamado
   automaticamente pelo `AccountSyncService` dentro do próprio `sync` — não é
   um passo separado, cada clique em "Sincronizar" já importa transações de
   todas as contas/cartões daquela conexão. A janela de busca (`from`/`to`
   passados pra `listTransactions`) usa o `lastSyncAt` anterior da
   `BankConnection` com 3 dias de sobreposição (cobre transações que só
   assentam como `POSTED` alguns dias depois de aparecerem), ou os últimos 90
   dias na primeira sincronização de uma conexão. Nova origem
   `EntryOrigin.IMPORTADO_EXTRATO` para movimentações de conta corrente/PIX
   (sem migração — `entry.origin` é `varchar` sem `CHECK` constraint).
   Reaproveita a dedup por (descrição, `dueDate`, valor) e a categorização
   automática por nome do `EntryImportService` (CSV).

   Contas: só transações `status=POSTED` viram `Entry`, já como `PAGO` (é um
   movimento que já aconteceu) — `RECEITA` se `amount` positivo, `DESPESA` se
   negativo (convenção padrão da Pluggy pra contas).

   Cartões: só `amount` positivo (compra/débito na fatura, convenção da
   Pluggy pra cartão) vira `Entry`, como `PENDENTE` com origem
   `IMPORTADO_CARTAO`; `amount` negativo (pagamento/estorno da fatura) é
   ignorado de propósito, porque esse valor já aparece como débito na conta
   bancária que paga a fatura — importar os dois lados duplicaria o gasto. O
   `dueDate` é calculado a partir de `closingDay`/`dueDay` do `CreditCard`
   (novo método `CreditCard.calculateInvoiceDueDate`): compra até o dia de
   fechamento entra na fatura que fecha naquele mês, senão entra na do mês
   seguinte; o vencimento nunca fica igual ou antes do fechamento (empurra
   pro mês seguinte quando `dueDay` < `closingDay`, caso comum).

   **Achado em produção, com dado real**: o endpoint documentado
   `GET /transactions` estava desativado — a Pluggy retornou `410 Gone`
   (`ENDPOINT_DEPRECATED`) já no primeiro teste real, orientando usar
   `GET /v2/transactions` com paginação por cursor. `PluggyClient.listTransactions`
   foi migrado pra v2 (`dateFrom`/`dateTo` no lugar de `from`/`to`; sem
   `pageSize`, o v2 já pagina fixo em até 500 por página). Sem paginação
   implementada por enquanto — cobre o volume esperado de um usuário pessoa
   física num intervalo de poucos dias/meses; se algum dia passar de 500
   transações numa única sincronização, precisa seguir o cursor `next` da
   resposta.

   **Ponto ainda não confirmado com dado real**: o campo `category` de
   `/v2/transactions` é documentado como exclusivo de planos Pro+. O código já
   assume que vai vir `null` no plano gratuito do Marcio e cai numa categoria
   genérica "Outros" nesse caso — mas isso só será confirmado de fato quando
   ele testar com transações reais.

9. [ ] Prototipar contra uma conta sandbox da Pluggy antes de expandir para
   todas as contas reais conectadas — ficou menos crítico depois dos itens
   6/7/8 já terem sido validados direto com as contas reais, mas continua
   útil pra testes automatizados no futuro.

   **Pendência aberta, primeiro teste real (28/09)**: a sincronização
   funcionou (sem erro), mas importou bem menos do que o esperado —
   praticamente só lançamentos de extrato de conta, quase nada de cartão.
   Hipótese mais provável, a investigar antes de mais nada na próxima sessão:
   essa `BankConnection` já tinha `lastSyncAt` preenchido de testes
   anteriores (da sincronização de contas/cartões, feita antes do
   `TransactionImportService` existir) — então a importação de transações
   não caiu no caminho "primeira sincronização" (90 dias pra trás), e sim no
   caminho normal (`lastSyncAt` anterior menos 3 dias de sobreposição), que é
   uma janela bem estreita. Isso explicaria bater com o que apareceu: PIX/
   débitos de conta dos últimos dias entraram, mas compras de cartão de mais
   cedo na fatura ainda aberta (que pode ter até ~25 dias, dependendo do
   `closingDay`) ficaram de fora. Se for isso, o comportamento real (assim
   que a janela normal passar a cobrir um ciclo de fatura inteiro) deve ficar
   correto sozinho nas próximas sincronizações — mas vale confirmar contando
   quantas transações a Pluggy realmente tem no período e comparando com o
   que entrou, e considerar aumentar a sobreposição/janela mínima se o
   ciclo de fatura for tipicamente maior que uns dias.

## 3. Resumo da decisão para as duas frentes

| Frente | Caminho descartado | Caminho escolhido | Por quê |
|---|---|---|---|
| Boletos | Parser de e-mail | Linha digitável colada manualmente (✅ implementado) | Menor esforço/manutenção, cobre a maior parte do ganho |
| Saldos/extratos | Virar participante direto do Open Finance | Agregador (Pluggy, tier "Meu Pluggy" gratuito) | Virar participante exige ser instituição regulada pelo Bacen — inviável para projeto pessoal |

Ambas as decisões mantêm o caminho para uma automação melhor no futuro (Open
Finance direto nos boletos, ou trocar de agregador) sem exigir retrabalho no
modelo de domínio — só trocam/acrescentam a origem do dado.
