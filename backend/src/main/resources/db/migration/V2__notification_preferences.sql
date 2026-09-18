-- Preferências de notificação por usuário (Fase 3 do roadmap: alertas de conta
-- vencendo e de saldo negativo projetado). Cada canal pode ser ligado/desligado
-- individualmente — ver docs/ROADMAP.md e NotificationService.
--
-- notify_sms_enabled e phone_number já existem no schema para o dia em que um
-- provedor de SMS for integrado (ver NotificationService), mas nesta versão
-- nenhum SMS é de fato enviado.

alter table app_user
    add column notify_due_soon_email        boolean not null default true,
    add column notify_negative_balance_email boolean not null default true,
    add column notify_sms_enabled            boolean not null default false,
    add column phone_number                  varchar(30);
