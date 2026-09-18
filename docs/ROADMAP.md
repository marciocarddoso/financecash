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
- [ ] Importar o histórico atual da planilha via CSV/Excel (endpoint de importação em lote, um `import job` por arquivo).
- [ ] Edição em lote de lançamentos (ex.: marcar vários como pagos de uma vez).
- [ ] Tela de "fechamento do mês" comparando previsto vs. realizado.
- [ ] Anexar comprovante/boleto (PDF/imagem) a um lançamento.
- [ ] Filtros avançados nos relatórios (por conta, por cartão, por faixa de valor).

## Fase 2 — Automação de lançamento
- [ ] **Boletos**: avaliar viabilidade de leitura automática via e-mail (parser de e-mails de bancos/empresas que emitem boleto em seu nome) e, como alternativa mais robusta, via Open Finance (Iniciação de Pagamento/Dados). Enquanto não sai do papel, lançamento manual continua sendo o caminho — a UI já foi pensada para isso ser rápido (atalhos, duplicar lançamento).
- [ ] **Cartões de crédito por banco**: importação de fatura via OFX/CSV exportado do banco como primeiro passo (não depende de integração aprovada); Open Finance como evolução.
- [ ] **PIX**: mesmo tratamento — OFX/CSV do extrato como primeiro passo, Open Finance depois.
- [ ] Conciliação: ao importar, o sistema sugere match com lançamentos manuais já existentes (evitar duplicidade).

## Fase 3 — Alertas e automação de rotina
- [ ] Job agendado (`@Scheduled` ou worker separado) que roda diariamente:
  - Gera os próximos lançamentos recorrentes automaticamente (hoje é sob demanda).
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
