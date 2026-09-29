-- Guarda a bandeira do cartão (Visa/Mastercard/Elo/...) separada do nome — antes só vinha
-- embutida em CreditCard.name ("Banco Bradesco - Cartão (VISA)"), o que obrigava o frontend a
-- concatenar name + bankName de novo pra mostrar o cartão, duplicando o nome do banco na tela
-- (01/10, quinta rodada, a pedido do Marcio: "o nome cartão pode ser somente banco bandeira").
alter table credit_card add column brand varchar(50);
