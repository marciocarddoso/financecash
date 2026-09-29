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
   todas as contas/cartões daquela conexão. Nova origem
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

   **Achado em produção (30/09), contradiz a doc**: o campo `category` de
   `/v2/transactions` é documentado como exclusivo de planos Pro+, mas veio
   preenchido normalmente no plano gratuito do Marcio — só que em inglês
   (taxonomia fixa da Pluggy). Adicionado `PluggyCategoryTranslator`
   (`backend/.../openfinance/PluggyCategoryTranslator.java`) com um mapa
   inglês→português cobrindo os níveis 1 e 2 da taxonomia documentada em
   docs.pluggy.ai/docs/transaction-categories; um nome sem tradução conhecida
   passa direto sem quebrar nada. `TransactionImportService.resolveCategory`
   traduz antes de resolver/criar a `Category`. Categorias que o Marcio já
   tinha criado manualmente em português não são afetadas; as poucas que já
   tinham sido importadas em inglês antes desse fix ficam como estão — ele
   pode renomeá-las pela tela de Categorias se quiser, já que a edição já
   existe.

9. [x] **Achado em produção (29/09) — janela de busca de transações estava
   presa curta**: a primeira versão da janela (`from`/`to` passados pra
   `listTransactions`) calculava a partir do `lastSyncAt` anterior da
   `BankConnection`, com 3 dias de sobreposição, ou 90 dias só na "primeira"
   sincronização. Só que `lastSyncAt` já vinha sendo preenchido desde antes
   do `TransactionImportService` existir (sincronização de contas/cartões,
   dias antes) — então a janela nunca chegava a olhar 90 dias pra trás,
   ficava presa em "`lastSyncAt` anterior menos 3 dias", e como `lastSyncAt`
   é reescrito a cada sincronização (com sucesso ou não), essa janela
   estreita nunca se corrigiria sozinha. No teste real isso apareceu como o
   cartão não importando **nenhuma** transação, só extrato de conta.

   Corrigido trocando a janela variável por uma janela fixa em toda
   sincronização (`AccountSyncService.TRANSACTION_LOOKBACK_DAYS`), sem
   depender de `lastSyncAt` — seguro porque a deduplicação por (descrição,
   dueDate, valor) já torna reprocessar o mesmo período inofensivo. Era 60
   dias inicialmente (cobre um ciclo de fatura inteiro com folga); o Marcio
   testou de novo e pediu mais histórico logo na primeira importação de cada
   conexão, então subiu pra **120 dias** — sem custo real de manter isso
   sempre (mesmo número de chamadas à Pluggy, só um intervalo de data
   maior).

10. [x] **Achado em produção (29/09) — nem todas as compras parceladas de
    cartão apareceram na importação**, mesmo depois do fix da janela.
    Investigado, parcialmente corrigido:

    - Causa concreta já corrigida: `docs.pluggy.ai/docs/transactions`
      documenta que transações da fatura ainda aberta — e parcelas futuras
      de uma compra parcelada — vêm com `status=PENDING` em vez de
      `POSTED`. O `TransactionImportService` só aceitava `POSTED`, ou seja,
      estava descartando de propósito justamente esse tipo de transação
      pra cartão (pra conta bancária continua só `POSTED`, faz sentido lá:
      um PENDING de conta pode ainda mudar/cancelar). Corrigido: cartão
      agora aceita `POSTED` e `PENDING` — ainda não confirmado com dado
      real do Marcio, só com a documentação (mesma cautela de sempre com a
      doc da Pluggy).
    - Causa ainda em aberto: não está confirmado se a Pluggy chega a
      devolver uma transação separada pra cada parcela futura de uma
      compra parcelada, ou só a parcela "atual" (com metadados de
      parcelamento indicando quantas faltam). Pra descobrir isso sem
      adivinhar, foi adicionado `PluggyClient.CreditCardMetadataInfo`
      (`installmentNumber`, `totalInstallments`, `purchaseDate`, campos
      confirmados só contra a doc — `totalAmount` não foi mapeado porque a
      doc diz que não vem em conectores Open Finance como o do Marcio) e
      um log temporário em `TransactionImportService.
      logInstallmentMetadataIfPresent` (remover depois de confirmar) que
      registra o que realmente chega quando `totalInstallments > 1`.

      **Atualização (30/09)**: o Marcio pediu que a tela de Lançamentos
      mostre "parcela X de Y" e o cartão/banco de origem pra compras
      importadas. `installmentNumber` e um novo campo
      `Entry.installmentsCount` (migration V4, usado só quando não há
      `InstallmentPlan`) já são preenchidos direto do metadata da Pluggy em
      `importForCreditCard`.

      **Confirmado com dado real (30/09)**: o Marcio exportou os 336
      lançamentos de cartão de 2026 direto do Postgres (C6, Bradesco,
      Nubank) e **nenhum** tinha `creditCardMetadata` preenchido pela
      Pluggy — resolve a dúvida de fundo: pros conectores Open Finance
      desse Marcio (tier "Meu Pluggy" gratuito), esse campo simplesmente
      não vem, então o fallback baseado nele nunca vai disparar sozinho.

      Só que o mesmo export revelou como cada banco realmente expõe
      parcelamento, e é diferente por banco:
      - **Nubank**: a `description` da transação já vem com "N/M" no fim
        (ex.: `"Pmz Distribuidora 2/4"`, `"MP *CASABULHOESCE 8/10"`) — não
        documentado, achado direto no dado real. `TransactionImportService`
        agora extrai isso com regex (`parseInstallmentFromDescription`) e
        preenche `installmentNumber`/`installmentsCount` a partir do texto
        quando o metadata estruturado não vem. A `description` salva NÃO é
        limpa desse sufixo de propósito — o dedup por description literal
        quebraria pra entries já importadas antes desse fix.
      - **C6 e Bradesco**: nenhum sinal, nem metadata nem texto — mas o
        export confirma que a Pluggy manda uma transação nova a cada fatura
        pra compra parcelada (mesma descrição+valor repetindo em faturas
        consecutivas, ex.: `"AVELL *AVELL"` R$109,88 em jul/ago/set/out —
        bate com o "Monitor Avell" 10x que o Marcio tem na planilha). Ou
        seja: a pergunta "a Pluggy manda cada parcela futura ou só a atual"
        está respondida — manda cada uma — mas **sem** nenhum jeito de saber
        que é parcela 7 de 10 e não uma cobrança avulsa, exceto inferindo
        pela repetição. Isso é arriscado: cobranças recorrentes de verdade
        (ex.: "Tarifa Anuidade Diferenciada" R$98/mês) têm exatamente o
        mesmo padrão de repetição e não são parcelamento — não implementado
        ainda, decisão em aberto com o Marcio.

      **Atualização (01/10)**: o caso da "Tarifa Anuidade Diferenciada"
      R$98/mês do C6, usado acima como exemplo de cobrança recorrente que
      quebraria a heurística de repetição, acabou sendo o oposto do que se
      imaginava — o Marcio confirmou (e mandou print real do app do C6)
      que é uma tarifa de anuidade normal, cobrada em 12 parcelas, só que
      ele tem isenção promocional por 1 ano: o banco cobra "Tarifa
      Anuidade" e no mesmo dia lança um "Estorno Tarifa" negativo
      cancelando ela (print mostrou "Parcela 10 de 12" na fatura de
      setembro/2026, série começando dez/2025 e terminando nov/2026). Ou
      seja, tanto o débito quanto o estorno fazem parte do MESMO padrão de
      12 parcelas que uma compra parcelada normal — não muda a conclusão
      sobre o risco da heurística de repetição (continua não implementada,
      decisão em aberto), mas revela um bug separado e real: antes desse
      dia, `TransactionImportService.importForCreditCard` ignorava
      silenciosamente QUALQUER transação de cartão com valor negativo,
      pensado só pro "Pagamento de fatura" (transferência que paga a
      fatura inteira, não um evento por si só) — só que isso também
      descartava o "Estorno Tarifa", fazendo o FinanceCash mostrar os R$98
      como despesa real todo mês sem nunca refletir o estorno que zera
      ela. Corrigido: agora só "Pagamento de fatura" (por texto na
      descrição) continua sendo ignorado; qualquer outra transação
      negativa (estorno, cancelamento, crédito) vira um Entry RECEITA
      PENDENTE separado — visível na tela de Lançamentos, sem tentar
      parear automaticamente com a compra/tarifa original (mesma cautela
      da heurística de parcelamento: parear errado é pior que não parear).
      Ver `TransactionImportService.importCreditOrRefund` e
      `isInvoicePaymentDescription`.

      **Atualização (01/10, segunda rodada)**: o fix acima criou um bug novo,
      pego comparando saldo líquido por cartão/mês com o valor real de
      fatura mostrado no app do C6 (print real, 7 meses: mar-set/2026). O
      filtro `isInvoicePaymentDescription` só reconhecia literalmente
      "pagamento de fatura" — mas **nenhum dos 3 bancos do Marcio usa esse
      texto**. Dado real confirmado: Bradesco manda `"PAGTO. POR DEB EM
      C/C"`; C6 manda `"Inclusao de Pagamento Ciclo Corrente"` (fatura
      normal), `"Pagamento Ent parcelamento fat"` e `"Credito de
      Refinanciamento Saldo Financiado"` (quando o saldo é
      financiado/parcelado pelo banco — o mesmo valor às vezes reaparece
      como `"Pagamento recebido"`, parece ser o mesmo evento bancário
      chegando em duas transações Pluggy diferentes); Nu manda `"Pagamento
      recebido"`. Como cada uma dessas transações é grande (fatura inteira
      ou boa parte dela), estavam virando Entry RECEITA e zerando boa parte
      das compras reais do mês no saldo líquido — exatamente o "os valores
      por cartão estão totalmente fora da realidade" que o Marcio reportou.
      Corrigido com duas camadas: (1) categoria estruturada que a própria
      Pluggy manda (`category = "Credit card payment"`, já mapeada em
      `PluggyCategoryTranslator` pra "Pagamento de fatura de cartão") tem
      prioridade sobre texto — não depende de cada banco escrever igual;
      (2) lista dos textos reais confirmados acima como reforço; (3) só
      importa como RECEITA quando a descrição tem palavra de estorno
      (`estorno`/`cancelamento`/`reembolso` — é assim que todo estorno real
      visto até agora se anuncia); (4) qualquer transação negativa que não
      bate com nada disso é ignorada por segurança (não vira Entry) e gera
      log de aviso, em vez de arriscar contar um pagamento desconhecido
      como renda — mesmo padrão de "logar o desconhecido pra decidir com
      dado real depois" usado na tradução de categoria. Ver
      `TransactionImportService.isInvoiceSettlementTransaction` e
      `looksLikeGenuineCredit`. Gerado `_scratch/fix_liquidacao_fatura.sql`
      (com `_scratch/preview_liquidacao_fatura.sql` pra conferir antes) pra
      limpar os lançamentos RECEITA indevidos já importados no banco real
      antes desse fix — ainda não rodado, aguardando o Marcio validar com
      `mvn test` primeiro.

      **Atualização (01/10, terceira rodada)**: depois do totalizador da
      tela de Lançamentos ir pro ar (ver `docs/ROADMAP.md`, item do
      totalizador), o Marcio reportou que o total de despesa/receita de um
      mês continuava "totalmente fora da realidade" mesmo sem filtrar por
      cartão. Comparando a soma de setembro por categoria (query real),
      achamos dois grupos que infestavam o total sem ser gasto/receita de
      verdade: (a) transferência entre as próprias contas do usuário —
      Bradesco/Nubank/C6, categoria "Same person transfer" da Pluggy
      (traduzida pra "Transferência entre contas próprias" e as variações
      de nível 2 "Transferência própria - Dinheiro/PIX/TED") — R$19.549,50
      de despesa mais R$16.049,88 de receita só em setembro; como as duas
      pernas se cancelam, nunca dava pra ver isso olhando só o saldo
      líquido, só somando despesa e receita separadamente; (b) o débito em
      conta corrente que paga a fatura do cartão — a mesma transação de
      liquidação de fatura que `isInvoiceSettlementTransaction` já
      reconhece do lado do cartão (ver rodada anterior), só que ela também
      aparece do lado da CONTA que pagou (`importForAccount`), com o
      mesmo texto/categoria — R$6.513,88 no mesmo mês, duplicando um gasto
      que já tinha sido contado quando a compra foi feita no cartão.
      Confirmado com o Marcio (via pergunta direta, não decisão unilateral):
      excluir os dois do totalizador (`EntryRepository.sumByFilters`), mas
      sem esconder da lista de Lançamentos — cada lançamento afetado
      continua aparecendo, só ganha uma tag "fora do total" na tela.
      Implementado: `Entry.excludedFromTotals` (migration V6, default
      false); `TransactionImportService.importForAccount` calcula isso
      automaticamente pra toda sincronização nova (`isExcludedFromTotals`,
      reaproveitando o mesmo `isInvoiceSettlementTransaction` do caso b, e
      um novo prefixo de categoria `"same person transfer"` pro caso a).
      Gerado `_scratch/fix_excluidos_totalizador.sql` (com preview) pra
      marcar retroativamente o que já tinha sido importado antes desse fix
      — ainda não rodado. **Em aberto, sem decisão ainda**: as categorias
      "Transferências" (genérica, R$3.106,76 despesa / R$13.670,61 receita)
      e "Transferência - PIX" (R$3.499,62 receita) são ambíguas — podem ser
      transferência pra terceiros/família (conta como gasto/renda de
      verdade) ou renda real tipo salário via PIX — não mexemos nelas até
      o Marcio confirmar o que são de fato; "Transporte público" R$2.600,00
      num lançamento só em setembro também parece alto pra fazer sentido
      sozinho, mas não foi tratado como erro sem confirmação dele.

      **Atualização (01/10, quarta rodada)**: o Marcio trouxe um print real
      da própria planilha dele ("SETEMBRO de 2026") pra comparar, e explicou
      o contexto por trás dos R$41.792,99/R$33.347,57 que ainda não faziam
      sentido pra ele — nada disso registrado na planilha: (1) o cartão do
      C6 exigiu financiar parte da fatura (~R$2.900 de entrada + 3 parcelas
      de R$707,55); (2) ele pegou e devolveu dinheiro emprestado com a
      esposa; (3) recebeu R$2.800 de um amigo pra comprar remédio, gastou
      R$200 a mais que isso, deu R$100 pra esposa e ficou com R$100. Duas
      coisas ficaram claras a partir disso. Primeiro, uma diferença
      estrutural que não é bug: a planilha do Marcio conta o gasto do mês
      pelo valor do PAGAMENTO da fatura (regime de caixa, uma linha por
      cartão/mês), enquanto o FinanceCash conta pelo valor de cada COMPRA
      no momento em que ela acontece (regime de competência, item a item) —
      os dois são válidos, mas nunca vão bater centavo a centavo num mês
      calendário específico, porque compra e pagamento/vencimento caem em
      ciclos diferentes; mantido o regime de competência (já implementado e
      já aprovado antes pelo Marcio), por preservar detalhe por categoria
      que a planilha não tem. Segundo, empréstimo/repasse entre pessoas
      (itens 2 e 3 acima) não tem NENHUM sinal confiável vindo do banco —
      a categoria da Pluggy não distingue "amigo devolvendo dinheiro" de
      receita real — então não dá pra resolver com mais heurística
      automática, só com confirmação manual do próprio Marcio. Duas
      decisões implementadas: (a) resolvida a pendência em aberto da
      rodada anterior sobre "Transferências"/"Transferência - PIX": em vez
      de revisar uma por uma (minha recomendação, por risco de esconder
      renda real tipo salário via PIX), o Marcio decidiu excluir as duas
      categorias do totalizador por padrão —
      `TransactionImportService.isExcludedFromTotals` ganhou
      `GENERIC_TRANSFER_CATEGORY_MARKERS` (match exato do nome da
      categoria, não prefixo, pra não pegar de tabela "Transferência
      interna"/"- TED"/"- Boleto"/"- Cheque"/"- DOC"/"- Câmbio"/"para
      terceiros", que continuam fora dessa decisão); (b) criado um toggle
      manual por lançamento — `PATCH /api/entries/{id}/exclude-from-totals`
      e `/include-in-totals` (reaproveita o mesmo campo
      `excludedFromTotals` e o mesmo padrão de `markAsPaid`/
      `markAsPending`), com botão "Excluir do total"/"Incluir no total" na
      tela de Lançamentos — mantém o princípio de não esconder nada da
      lista, só tira da soma quando o Marcio confirma manualmente que não
      é gasto/receita real. `_scratch/fix_excluidos_totalizador.sql` e o
      preview foram atualizados pra também cobrir retroativamente
      "Transferências"/"Transferência - PIX" — ainda não rodados. Segue em
      aberto, não mexido nessa rodada: "Transporte público" R$2.600,00 e a
      tela dedicada "Faturas" (adiada desde a rodada do totalizador).

      **Atualização (01/10, quinta rodada)**: depois de ver a tela de
      Lançamentos com o totalizador ainda difícil de conferir e o layout
      quebrando (coluna de cartão gigante, botões de ação amontoados), o
      Marcio pediu quatro coisas juntas. Primeiro, revisitando a decisão da
      rodada anterior de manter só regime de competência: em vez de
      escolher um ou outro em definitivo, virou um toggle na própria tela
      — "Competência"/"Caixa" — já que os dois são úteis pra propósitos
      diferentes (competência pra detalhe por categoria, caixa pra bater
      com a planilha). Decisão do Marcio sobre como calcular caixa pro
      cartão: usar o que o próprio banco informou por fatura
      (`InvoiceSettlement`, já existente desde a rodada do totalizador),
      não o `paymentDate` de cada compra individual (que raramente existe
      de verdade pra cartão, já que a compra nasce PENDENTE). Segundo,
      "separar as despesas de cartão do totalizador... quero ver o
      totalizador por cartão e por banco": em vez de reduzir o totalizador
      geral, foi ADICIONADA uma quebra por cartão (`EntryTotals.
      creditCardTotals`) que o frontend agrupa por banco — sem esconder o
      total geral, só detalhando (mesmo princípio de "não esconde nada de
      você" das rodadas anteriores). Terceiro, decisão do Marcio sobre o
      escopo do "por banco": soma só cartão, não mistura com conta
      corrente/poupança do mesmo banco — mais simples de entender e evita
      um resultado que mistura duas fontes bem diferentes numa soma só.
      Quarto, o nome de cartão: a tela mostrava "Banco Bradesco - Cartão
      (VISA) — Banco Bradesco", porque o `name` do cartão (já formatado por
      `AccountSyncService.friendlyCardName` na hora do sync) era
      concatenado de novo com o `bankName` cru no frontend — redundante e
      feio. Corrigido guardando a bandeira separada (`CreditCard.brand`,
      migration V7, preenchida de `creditData.brand()` tanto na criação
      quanto atualizada num sync seguinte pra cartão já existente) e
      montando o nome de exibição num lugar só (`CreditCardDisplayNameFormatter`,
      novo em `openfinance/`, mesmo pacote do `PluggyCategoryTranslator`):
      "Bradesco Visa", "C6 Mastercard". Como não dá pra confirmar contra
      produção o texto exato que a Pluggy manda de `bankName` pra cada
      banco (varia pelo marketingName da conta vinculada, não por um
      catálogo fixo — mesmo problema já visto com categoria em inglês), a
      normalização usa reconhecimento por palavra-chave ("contém",
      case-insensitive) com fallback pro nome cru sem o prefixo "Banco " e
      log quando cai nesse fallback, em vez de comparação exata que
      quebraria silenciosamente pra qualquer variação de texto real. Esse
      fix também resolveu boa parte do "layout quebrado" reportado: a
      coluna de Cartão ficou bem mais curta (não concatena mais dois
      textos), e a linha de ações da tabela ganhou `flex-wrap` +
      `white-space: nowrap` em vez de quebrar "Excluir do total" no meio
      da palavra.

11. [ ] Prototipar contra uma conta sandbox da Pluggy antes de expandir para
    todas as contas reais conectadas — ficou menos crítico depois dos itens
    6/7/8 já terem sido validados direto com as contas reais, mas continua
    útil pra testes automatizados no futuro.

## 3. Resumo da decisão para as duas frentes

| Frente | Caminho descartado | Caminho escolhido | Por quê |
|---|---|---|---|
| Boletos | Parser de e-mail | Linha digitável colada manualmente (✅ implementado) | Menor esforço/manutenção, cobre a maior parte do ganho |
| Saldos/extratos | Virar participante direto do Open Finance | Agregador (Pluggy, tier "Meu Pluggy" gratuito) | Virar participante exige ser instituição regulada pelo Bacen — inviável para projeto pessoal |

Ambas as decisões mantêm o caminho para uma automação melhor no futuro (Open
Finance direto nos boletos, ou trocar de agregador) sem exigir retrabalho no
modelo de domínio — só trocam/acrescentam a origem do dado.
