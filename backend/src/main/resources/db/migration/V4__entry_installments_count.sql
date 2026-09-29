-- Total de parcelas para lançamentos de cartão importados via Open Finance (Pluggy), que não
-- passam pelo InstallmentPlanService (parcelamento manual) e por isso não têm um
-- installment_plan vinculado — ver TransactionImportService.importForCreditCard e
-- docs/OPEN-FINANCE-E-BOLETOS.md. Para lançamentos com installment_plan_id preenchido, o total
-- "oficial" de parcelas continua vindo de installment_plan.installments_count; esta coluna é o
-- fallback usado só quando não há um InstallmentPlan (compra parcelada importada da Pluggy).

alter table entry add column installments_count int;
