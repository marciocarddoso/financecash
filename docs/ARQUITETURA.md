# Arquitetura

## Visão geral

O FinKeeper é um monorepo com dois módulos independentes que se comunicam por HTTP/JSON:

- **backend/** — API REST em Spring Boot, organizada em camadas inspiradas em arquitetura hexagonal simplificada (sem o overhead de portas/adaptadores completo, mas com separação clara de responsabilidades).
- **frontend/** — SPA Angular, standalone components, consumindo a API via services HTTP tipados.

O monorepo foi escolhido (em vez de dois repositórios separados) porque o projeto é de um único desenvolvedor, o versionamento de contrato entre front e back fica mais simples de acompanhar, e para portfólio é mais fácil de navegar em um único link. Nada impede separar em dois repositórios no futuro se o projeto crescer em equipe.

## Camadas do backend

```
br.com.finkeeper
├── domain
│   ├── model         # Entidades JPA + enums de domínio (sem dependência de framework web)
│   └── repository     # Interfaces Spring Data JPA
├── application
│   ├── service         # Regras de negócio (orquestram repositórios, não conhecem HTTP)
│   └── dto              # Objetos de transporte entre application e api
├── api
│   └── controller      # Controllers REST — tradução HTTP ↔ DTO ↔ service
├── security             # Autenticação JWT, filtros, UserDetails
├── config                # Beans de configuração (segurança, CORS, OpenAPI)
└── exception             # Exceções de negócio + handler global (@ControllerAdvice)
```

Regra de dependência: `api` depende de `application`, que depende de `domain`. `domain` não depende de nada acima dele. Isso permite, por exemplo, trocar o transporte HTTP por mensageria no futuro sem tocar nas regras de negócio.

### Por que essa separação e não "tudo em cima da entidade" (JPA + Controller direto)?

Para um projeto pessoal simples, entidade-direto-no-controller seria mais rápido de escrever. A separação em camadas foi uma escolha deliberada porque:

1. É o padrão que se espera avaliar em um portfólio de dev sênior.
2. Os services concentram regras que não são triviais (geração de lançamentos recorrentes, cálculo de saldo necessário do dia/mês, rateio de parcelas) — isolá-las facilita testar sem subir contexto web.
3. Facilita a evolução para multiusuário/mobile sem reescrever a base.

## Autenticação

Autenticação via JWT (`spring-boot-starter-security` + `jjwt`). Cada usuário (`AppUser`) tem suas próprias contas, categorias e lançamentos — o modelo já nasce multiusuário mesmo sendo hoje usado por uma pessoa só, pensando no plano de virar app mobile com múltiplos usuários (free/pago).

Endpoints de autenticação (`/api/auth/register`, `/api/auth/login`) são públicos; os demais exigem `Authorization: Bearer <token>`.

> No primeiro uso local, crie seu usuário chamando `POST /api/auth/register` (endpoint público) — a resposta já vem com o token JWT para usar nas chamadas seguintes. Uma migration de seed para o ambiente `dev` pode ser adicionada depois (`V2__seed_dev.sql`) se fizer sentido automatizar isso.

## Geração de lançamentos futuros (provisionamento)

Esse é o núcleo do sistema, equivalente ao que hoje é feito manualmente na planilha:

- **`RecurringRule`**: representa um lançamento que se repete (salário, aluguel, assinatura, décimo terceiro). Tem frequência (`MENSAL`, `SEMESTRAL`, `ANUAL`), dia de vencimento, valor vigente e um histórico de valores (`RecurringRuleValueHistory`) para suportar reajustes anuais/semestrais sem perder o valor praticado em cada período.
- **`InstallmentPlan`**: representa uma compra parcelada. Ao ser criado (ex.: 12x de R$ 150), o `InstallmentPlanService` gera os `Entry` (lançamentos) futuros de cada parcela de uma vez, todos vinculados ao plano — permitindo ver "faltam 7 parcelas de 12" e o total já comprometido.
- **`RecurringEntryGenerationService`**: roda sob demanda (endpoint) e, futuramente, também de forma agendada (`@Scheduled`/job) — projeta os próximos `N` meses de lançamentos recorrentes que ainda não existem como `Entry`, evitando duplicar o que já foi gerado.

Esse desenho separa **"regra que gera"** (`RecurringRule`, `InstallmentPlan`) de **"lançamento efetivo"** (`Entry`), que é o mesmo princípio usado na planilha atual (uma aba de regras/parâmetros e os lançamentos mês a mês), só que sistematizado.

## Dashboard e relatórios

- `DashboardService` calcula: lançamentos com vencimento hoje, total a pagar no mês corrente (pendente), saldo consolidado atual (soma de `BalanceSnapshot` mais recente por conta) e saldo necessário para cobrir o restante do dia/mês.
- `ReportService` agrega `Entry` por `Category` em um período, retornando o "onde eu mais gasto" (mercado, farmácia, passeios etc.), reaproveitável tanto para um gráfico no frontend quanto para exportação futura.

## Frontend

Angular standalone (sem NgModules), roteamento lazy por feature, um `core/services` fino que só faz chamadas HTTP e mapeia para os models TypeScript (espelhando os DTOs do backend). Estado de UI mantido nos próprios componentes com `signals` — sem necessidade de um state manager (NgRx) neste estágio; se a complexidade crescer (ex.: cache offline no mobile), isso entra no roadmap.

## Decisões que ficaram deliberadamente simples por enquanto

- Sem multi-moeda (assume-se BRL).
- Sem importação bancária automática ainda — os pontos de extensão (interfaces de "importador") já estão previstos no roadmap e nominados no modelo de domínio (`origem` do lançamento: `MANUAL`, `IMPORTADO_BOLETO`, `IMPORTADO_CARTAO`), para não exigir migração de schema quando essa funcionalidade entrar.
- Sem push/e-mail ainda — o `DashboardService` já expõe os dados que um futuro `NotificationScheduler` vai consumir.
