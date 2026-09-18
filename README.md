# FinKeeper

Sistema de controle financeiro pessoal — substitui a planilha de Excel usada para lançar boletos, despesas, parcelamentos, salário, décimo terceiro e saldos bancários, trazendo tudo para um sistema com backend em **Java/Spring Boot** e frontend em **Angular**.

> Projeto pessoal/portfólio de [Marcio Cardoso](https://github.com/) — desenvolvedor Java sênior. Construído para uso real no dia a dia e, ao mesmo tempo, como vitrine de arquitetura, boas práticas e evolução incremental (com plano de virar app mobile freemium no futuro).

## Por que este projeto existe

Hoje o controle financeiro é feito manualmente em uma planilha: lançamento de boletos e despesas, provisionamento de compras parceladas, salário, décimo terceiro e reajustes de contas fixas, além de acompanhamento de saldos em diferentes bancos e aplicações em CDI. O objetivo do FinKeeper é profissionalizar esse controle, mantendo a mesma lógica de uso (lançamentos, recorrências, parcelamentos, consolidação de saldos, relatórios de gastos) em um sistema com persistência, API e uma interface própria — eliminando a manutenção manual da planilha e abrindo caminho para automações (leitura de boletos, integração bancária, alertas).

## O que o sistema já resolve (escopo desta primeira versão)

- **Lançamentos** de receitas e despesas, avulsos ou vinculados a uma regra recorrente ou a um plano de parcelamento.
- **Recorrências** configuráveis (mensal, semestral, anual) para salário, contas fixas e variáveis, com ajuste de valor ao longo do tempo (histórico de valores, não só um valor fixo) — cobre o caso de reajuste anual/semestral.
- **Parcelamentos** de compras: ao cadastrar uma compra parcelada, o sistema gera automaticamente os lançamentos futuros de cada parcela.
- **Décimo terceiro salário** como um tipo de recorrência anual (duas parcelas), já modelado no domínio.
- **Cartões de crédito por banco**: compras lançadas por cartão, consolidadas na fatura do mês, associadas ao banco/emissor.
- **Transferências (PIX)**, com destinatário nomeado — cobre o caso de envio recorrente para familiares (ex.: filhas).
- **Contas bancárias e aplicações**: saldo consolidado por banco e por aplicação (ex.: CDI), com histórico de snapshots.
- **Dashboard do dia/mês**: quais contas vencem hoje, quanto falta pagar no mês e qual saldo é necessário para cobrir o dia e o mês corrente.
- **Relatórios de gastos por categoria** (mercado, farmácia, passeios, etc.), por período e por conta/cartão.

## O que fica no roadmap (arquitetado, não implementado ainda)

Ver [`docs/ROADMAP.md`](docs/ROADMAP.md) para o detalhamento, mas em resumo:

- Notificações por e-mail/push quando uma conta está próxima do vencimento ou o saldo previsto fica negativo.
- Importação automática de boletos emitidos em nome do usuário (via e-mail parsing ou Open Finance) — enquanto isso, lançamento manual continua funcionando normalmente.
- Importação de faturas/compras de cartão de crédito por banco (Open Finance / arquivos OFX/CSV como primeiro passo).
- Aplicativo mobile (Android/iOS) reaproveitando o frontend Angular via Capacitor, com plano de monetização freemium (módulo gratuito + módulo pago).

## Arquitetura em uma imagem

```
┌────────────────────┐        HTTPS/JSON        ┌─────────────────────────┐
│   Angular (SPA)     │ ───────────────────────▶ │   Spring Boot API (REST) │
│  frontend/           │ ◀─────────────────────── │   backend/                │
└────────────────────┘                           └───────────┬──────────────┘
                                                               │ JPA / Flyway
                                                               ▼
                                                     ┌───────────────────┐
                                                     │   PostgreSQL        │
                                                     └───────────────────┘
```

Detalhes de camadas, modelo de domínio e decisões técnicas em [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md) e [`docs/MODELO-DOMINIO.md`](docs/MODELO-DOMINIO.md).

## Stack

| Camada     | Tecnologia |
|------------|------------|
| Backend    | Java 21, Spring Boot 3, Spring Data JPA, Spring Security (JWT), Flyway |
| Frontend   | Angular 18+ (standalone components), TypeScript, RxJS |
| Banco      | PostgreSQL 16 |
| Infra local| Docker Compose |
| Testes     | JUnit 5, Mockito, Testcontainers |
| CI         | GitHub Actions (build + testes a cada push) |

## Estrutura do repositório

```
finkeeper/
├── backend/     # API Spring Boot (Java 21 / Maven)
├── frontend/    # SPA Angular
├── docs/        # Arquitetura, modelo de domínio, roadmap
├── docker-compose.yml
└── .github/workflows/ci.yml
```

## Como rodar localmente

### Pré-requisitos
- Java 21+ e Maven 3.9+
- Node 20+ e npm 10+ (Angular CLI: `npm i -g @angular/cli`)
- Docker (para o PostgreSQL local)

### 1. Subir o banco de dados
```bash
docker compose up -d postgres
```

### 2. Rodar o backend
```bash
cd backend
mvn spring-boot:run
```
A API sobe em `http://localhost:8080`. Documentação OpenAPI em `http://localhost:8080/swagger-ui.html`.

### 3. Instalar e rodar o frontend
```bash
cd frontend
npm install
npm start
```
A aplicação sobe em `http://localhost:4200` e já aponta para a API local (ver `src/environments/environment.ts`).

## Status

🚧 Base inicial do projeto — domínio, API e telas principais implementados como ponto de partida. Próximos passos em [`docs/ROADMAP.md`](docs/ROADMAP.md).

## Licença

Distribuído sob a licença MIT — ver [`LICENSE`](LICENSE).
