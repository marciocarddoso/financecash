-- Marca se a conta veio da sincronizacao Open Finance (Pluggy) ou foi criada manualmente
-- (28/09, setima rodada, a pedido do Marcio: "pra conta sincronizada pela Pluggy, ela deveria
-- ser praticamente so leitura (saldo + talvez historico) ... a edicao completa (nome, banco,
-- tipo, saldo manual) so faz sentido de verdade pra conta manual"). Contas ja existentes no
-- banco vieram todas da sincronizacao ate hoje (nao ha fluxo de criacao manual ainda em uso
-- real), entao o default true preserva o comportamento anterior pra elas; toda conta criada
-- manualmente a partir de agora define false explicitamente no AccountService.create().
alter table account add column synced_from_open_finance boolean not null default true;
