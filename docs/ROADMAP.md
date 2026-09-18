# Roadmap

Ordenado por valor imediato para o uso pessoal primeiro; itens de "produto" (app store, monetização) vêm depois porque dependem da base estar sólida.

## Fase 0 — Base (este commit inicial)
- [x] Modelo de domínio (contas, categorias, lançamentos, recorrências, parcelamentos, cartões, transferências, saldos).
- [x] API REST (CRUD de lançamentos/categorias/contas, geração de recorrências, dashboard, relatório por categoria).
- [x] Autenticação JWT.
- [x] Frontend Angular com dashboard, lançamentos, categorias e relatórios.
- [x] Docker Compose com PostgreSQL, migrations Flyway.
- [x] CI básico (build + testes no GitHub Actions).

## Fase 1 — Uso diário real (substituir a planilha de vez)
- [x] Importar o histórico atual da planilha via CSV (`POST /api/entries/import-csv` + `scripts/import_numbers_to_csv.py` para extrair as abas Bradesco/Nubank/C6Bank da planilha Numbers).
- [ ] Edição em lote de lançamentos (ex.: marcar vários como pagos de uma vez).
- [ ] Tela de "fechamento do mês" comparando previsto vs. realizado.
- [ ] Anexar comprovante/boleto (PDF/imagem) a um lançamento.
- [ ] Filtros avançados nos relatórios (por conta, por cartão, por faixa de valor).

## Fase 2 — Automação de lançamento
- [x] **Boletos, passo 1**: leitura da linha digitável colada pelo usuário (`POST /api/boletos/parse-linha-digitavel`), pré-preenchendo o lançamento com banco/valor/vencimento — ver `docs/OPEN-FINANCE-E-BOLETOS.md`.
- [ ] **Boletos, passo 2**: automação completa via Open Finance (ver `docs/OPEN-FINANCE-E-BOLETOS.md`, seção 1.2) — parser de e-mail foi avaliado e descartado (esforço/manutenção alto para o ganho, já coberto em boa parte pela leitura de linha digitável).
- [ ] **Cartões de crédito por banco**: importação de fatura via OFX/CSV exportado do banco como primeiro passo (não depende de integração aprovada); Open Finance como evolução.
- [ ] **PIX**: mesmo tratamento — OFX/CSV do extrato como primeiro passo, Open Finance depois.
- [ ] **Open Finance**: integração via agregador (Pluggy, tier "Meu Pluggy" gratuito para uso pessoal) em vez de virar participante direto — decisão e desenho detalhados em `docs/OPEN-FINANCE-E-BOLETOS.md`, seção 2. Ainda não implementado.
- [ ] Conciliação: ao importar, o sistema sugere match com lançamentos manuais já existentes (evitar duplicidade).

## Fase 3 — Alertas e automação de rotina
- [x] Job agendado (`@Scheduled`) que roda diariamente — `RecurringEntryScheduler`:
  - [x] Gera os próximos lançamentos recorrentes automaticamente para todos os usuários ativos (antes só sob demanda).
  - Verifica contas vencendo nas próximas 24-48h e dispara notificação.
  - Verifica projeção de saldo negativo no mês e alerta.
- [ ] Canal de notificação: e-mail primeiro (mais simples, SMTP/SES), push depois (junto com o app mobile).

## Fase 4 — Mobile
- [ ] Empacotar o frontend Angular com **Capacitor** (reaproveita 100% do código Angular, gera apk/ipa) — avaliar Ionic components só se a UI exigir comportamento mais "nativo".
- [ ] Publicar versão beta fechada (TestFlight / Play Console teste interno) antes de qualquer publicação pública.
- [ ] Modelo de dados já multiusuário (Fase 0) facilita virar SaaS pessoal multiusuário nesse ponto.

## Fase 5 — Monetização (freemium)
- [ ] Definir o que é grátis (ex.: lançamentos manuais, dashboard, relatórios básicos) vs. pago (ex.: importação automática de boletos/cartão, alertas, múltiplas contas ilimitadas, backup/export).
- [ ] Entidade `Subscription`/`Plan` já prevista no domínio (não implementada na Fase 0 para não adicionar complexidade antes de precisar).
- [ ] Gateway de pagamento (Stripe ou similar com suporte a BRL/Pix).
- [ ] Política de privacidade e termos de uso (obrigatório para publicar em loja com dados financeiros).

## Notas de arquitetura para as fases futuras

O modelo de `Entry.origin` (`MANUAL`, `RECORRENCIA`, `PARCELAMENTO`, `IMPORTADO_BOLETO`, `IMPORTADO_CARTAO`) e o desenho em camadas (domain/application/api) já foram pensados para que a Fase 2 (importadores) entre como novos `services` que produzem `Entry`, sem precisar alterar o modelo de dados ou os controllers existentes — só endpoints novos. Isso evita retrabalho grande quando essas integrações forem priorizadas.

A aba "Valores Acumulados" da planilha original **não** tem um importador dedicado: ela é um resumo mensal derivado (saldo, salário, totais por banco), não uma lista de lançamentos — o Dashboard/Relatórios do FinanceCash recalculam esses números automaticamente a partir dos lançamentos importados (Bradesco/Nubank/C6Bank) somados a uma `RecurringRule` de salário, uma vez cadastrada.
