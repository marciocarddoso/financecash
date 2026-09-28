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
  criar `Entry` com `origin = IMPORTADO_CARTAO` (compras de cartão) ou uma nova
  origem `IMPORTADO_EXTRATO` (movimentações de conta corrente/PIX), com o mesmo
  mecanismo de deduplicação por (descrição, data, valor) que o
  `EntryImportService` (importação CSV) já usa — os dois importadores podem
  inclusive compartilhar a lógica de dedup/categorização automática.

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
6. [ ] Implementar `listAccounts(itemId)` e `listTransactions(accountId)` no
   `PluggyClient` (endpoints `GET /accounts` e `GET /transactions`) — os
   nomes de campo exatos da resposta serão confirmados contra a API real (ou
   sandbox) antes de mapear os DTOs, para não arriscar um mapeamento
   silenciosamente errado.
7. [ ] `AccountSyncService` e `TransactionImportService` (ver seção 2.3) —
   sincronização periódica de saldo e importação de transações como `Entry`.
   `AccountSyncService` também é o lugar natural para atualizar `lastSyncAt`
   em `BankConnection` a cada rodada.
8. [ ] Prototipar contra uma conta sandbox da Pluggy antes de expandir para
   todas as contas reais conectadas.

## 3. Resumo da decisão para as duas frentes

| Frente | Caminho descartado | Caminho escolhido | Por quê |
|---|---|---|---|
| Boletos | Parser de e-mail | Linha digitável colada manualmente (✅ implementado) | Menor esforço/manutenção, cobre a maior parte do ganho |
| Saldos/extratos | Virar participante direto do Open Finance | Agregador (Pluggy, tier "Meu Pluggy" gratuito) | Virar participante exige ser instituição regulada pelo Bacen — inviável para projeto pessoal |

Ambas as decisões mantêm o caminho para uma automação melhor no futuro (Open
Finance direto nos boletos, ou trocar de agregador) sem exigir retrabalho no
modelo de domínio — só trocam/acrescentam a origem do dado.
