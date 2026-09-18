#!/usr/bin/env python3
"""
Extrai os lançamentos de cartão de crédito (Bradesco, Nubank, C6Bank) da planilha
Numbers de provisionamento e gera um CSV por banco no formato aceito por
POST /api/entries/import-csv (description,amount,dueDate,type,categoryName,origin).

Uso:
    pip install numbers-parser --break-system-packages
    python3 import_numbers_to_csv.py <arquivo.numbers> <pasta_saida>

A planilha "Valores Acumulados" NÃO é processada por este script: ela é um resumo
mensal derivado (saldo, salário, totais por banco), não uma lista de lançamentos —
esses números o FinanceCash recalcula sozinho a partir dos lançamentos + da regra
recorrente de salário, uma vez que os CSVs abaixo forem importados. A aba
"DespesasMensais" também é ignorada, pois o usuário informou que parou de
populá-la.

## Layout reconhecido em cada aba de banco

A aba é dividida em "blocos" (bandas de linhas) de 4 meses lado a lado, 6 colunas
por mês:
    linha N:   "Fatura {Mês}/{Ano}[ (Vencimento|Venc|Vence) dd/mm[/aaaa]]"
    linha N+1: "Data" | "Local Compra" | (mesclado) | (mesclado) | "Nº Parcela" | "Valor"
    linha N+2..: uma compra por linha, terminando numa linha de total (só "Valor")

## Tratamento de inconsistências reais da planilha

A data de vencimento de cada fatura é resolvida assim, em ordem de confiança:
  1. Se o cabeçalho "Fatura ..." tiver uma data explícita (Vencimento/Venc/Vence
     dd/mm[/aaaa]), essa data é usada literalmente — é o dado mais confiável porque
     foi digitado à mão pelo usuário para aquela fatura específica.
  2. Caso contrário, usa-se o dia de vencimento padrão do banco (Bradesco=10,
     Nubank=22, C6Bank=15) aplicado ao mês/ano do cabeçalho.

O mês é sempre confiável no texto do cabeçalho, mas o ANO às vezes está errado
(erro de copiar/colar da planilha original — ex.: a aba C6Bank tem um bloco onde
duas das quatro faturas foram rotuladas com o ano anterior). Para corrigir isso
sem "adivinhar", o script mantém uma sequência esperada de mês/ano (cada fatura é
o mês seguinte à anterior) enquanto varre a aba de cima para baixo, esquerda para
direita: quando o MÊS do cabeçalho bate com o esperado mas o ANO não, o ano é
corrigido para o esperado (e a correção é registrada no relatório final); quando
nem o mês bate (um hiato real na planilha, que também acontece), a sequência é
reiniciada a partir do que o próprio cabeçalho diz — sem forçar nada.
"""
import csv
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

import numbers_parser

MONTH_NAMES = {
    "janeiro": 1, "fevereiro": 2, "março": 3, "marco": 3, "abril": 4,
    "maio": 5, "junho": 6, "julho": 7, "agosto": 8, "setembro": 9,
    "outubro": 10, "novembro": 11, "dezembro": 12,
}

BANKS = {
    "Bradesco": {"default_due_day": 10, "category": "Cartão de Crédito - Bradesco"},
    "Nubank": {"default_due_day": 22, "category": "Cartão de Crédito - Nubank"},
    "C6Bank": {"default_due_day": 15, "category": "Cartão de Crédito - C6 Bank"},
}

FATURA_RE = re.compile(r"^Fatura\s+([A-Za-zçÇ]+)/(\d{4})(.*)$")
DATE_RE = re.compile(r"(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?")


@dataclass
class Anomaly:
    sheet: str
    row: int
    col: int
    header_text: str
    detail: str


@dataclass
class SkippedRow:
    sheet: str
    row: int
    reason: str


@dataclass
class ExtractionReport:
    sheet: str
    entries: int = 0
    blocks: int = 0
    anomalies: list = field(default_factory=list)
    skipped: list = field(default_factory=list)
    min_due_date: str = None
    max_due_date: str = None
    total_amount: float = 0.0


def parse_fatura_header(text):
    """Retorna (mes, ano, explicit_date_str_ou_None) a partir do texto do cabeçalho."""
    match = FATURA_RE.match(text.strip())
    if not match:
        return None
    month_name, year_str, rest = match.groups()
    month = MONTH_NAMES.get(month_name.lower())
    if month is None:
        return None
    year = int(year_str)

    explicit_date = None
    date_match = DATE_RE.search(rest)
    if date_match:
        day, mon, yr = date_match.groups()
        day, mon = int(day), int(mon)
        if yr is None:
            yr = year
        elif len(yr) == 2:
            yr = 2000 + int(yr)
        else:
            yr = int(yr)
        explicit_date = (yr, mon, day)

    return month, year, explicit_date


def next_month(month, year):
    return (1, year + 1) if month == 12 else (month + 1, year)


def format_date(year, month, day):
    return f"{year:04d}-{month:02d}-{day:02d}"


def extract_bank_sheet(doc, sheet_name, bank_config):
    sheet = next((s for s in doc.sheets if s.name == sheet_name), None)
    if sheet is None:
        raise ValueError(f"Aba '{sheet_name}' não encontrada na planilha.")

    table = sheet.tables[0]
    rows = list(table.rows(values_only=True))
    num_cols = len(rows[0]) if rows else 0

    report = ExtractionReport(sheet=sheet_name)
    csv_rows = []

    # 1) localizar todos os cabeçalhos "Fatura ..." com sua posição (linha, coluna)
    headers = []
    for r_idx, row in enumerate(rows):
        for c_idx, val in enumerate(row):
            if isinstance(val, str) and val.strip().startswith("Fatura"):
                headers.append((r_idx, c_idx, val.strip()))

    # 2) resolver mês/ano/data de cada cabeçalho, com correção sequencial de ano
    expected = None  # (month, year) esperado para o próximo cabeçalho
    resolved_headers = []  # (r_idx, c_idx, header_text, month, year, explicit_date_or_None)

    for r_idx, c_idx, text in headers:
        parsed = parse_fatura_header(text)
        if parsed is None:
            report.anomalies.append(Anomaly(sheet_name, r_idx, c_idx, text,
                                             "cabeçalho não reconhecido, bloco ignorado"))
            continue
        month, year, explicit_date = parsed

        if expected is not None and month == expected[0] and year != expected[1]:
            report.anomalies.append(Anomaly(
                sheet_name, r_idx, c_idx, text,
                f"ano do cabeçalho ({year}) inconsistente com a sequência "
                f"(esperado {expected[1]}) — corrigido para {expected[1]}"))
            year = expected[1]

        resolved_headers.append((r_idx, c_idx, text, month, year, explicit_date))
        # próxima fatura esperada = mês seguinte a esta (usando a data explícita
        # quando existir, por ser a informação mais confiável)
        if explicit_date is not None:
            expected = next_month(explicit_date[1], explicit_date[0])
        else:
            expected = next_month(month, year)

    report.blocks = len(resolved_headers)

    # 3) para cada cabeçalho resolvido, extrair as linhas de compra do bloco
    for idx, (r_idx, c_idx, text, month, year, explicit_date) in enumerate(resolved_headers):
        if explicit_date is not None:
            due_date_str = format_date(*explicit_date)
        else:
            due_date_str = format_date(year, month, bank_config["default_due_day"])

        data_col, local_col, parcela_col, valor_col = c_idx, c_idx + 1, c_idx + 4, c_idx + 5
        if valor_col >= num_cols:
            report.anomalies.append(Anomaly(sheet_name, r_idx, c_idx, text,
                                             "bloco fora dos limites da tabela, ignorado"))
            continue

        row_cursor = r_idx + 2  # pula a linha do cabeçalho e a linha "Data/Local/.../Valor"
        while row_cursor < len(rows):
            row = rows[row_cursor]
            data_val = row[data_col] if data_col < len(row) else None
            valor_val = row[valor_col] if valor_col < len(row) else None

            if data_val is None and valor_val is None:
                break  # linha em branco: fim do bloco
            if data_val is None:
                # linha de total (só "Valor" preenchido) ou linha sem data: não é lançamento
                row_cursor += 1
                continue

            local_val = row[local_col] if local_col < len(row) else None
            parcela_val = row[parcela_col] if parcela_col < len(row) else None

            if valor_val is None or (isinstance(valor_val, (int, float)) and valor_val == 0):
                report.skipped.append(SkippedRow(sheet_name, row_cursor, "valor ausente/zerado"))
                row_cursor += 1
                continue

            local = (local_val or "Compra não identificada").strip()
            description = f"{sheet_name} - {local}"
            if parcela_val:
                description += f" (parcela {parcela_val})"

            amount = round(float(valor_val), 2)

            csv_rows.append({
                "description": description,
                "amount": f"{amount:.2f}",
                "dueDate": due_date_str,
                "type": "DESPESA",
                "categoryName": bank_config["category"],
                "origin": "IMPORTADO_CARTAO",
            })
            report.entries += 1
            report.total_amount += amount
            if report.min_due_date is None or due_date_str < report.min_due_date:
                report.min_due_date = due_date_str
            if report.max_due_date is None or due_date_str > report.max_due_date:
                report.max_due_date = due_date_str

            row_cursor += 1

    return csv_rows, report


def write_csv(rows, path):
    fieldnames = ["description", "amount", "dueDate", "type", "categoryName", "origin"]
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def main():
    if len(sys.argv) != 3:
        print(f"Uso: {sys.argv[0]} <arquivo.numbers> <pasta_saida>")
        sys.exit(1)

    numbers_path = sys.argv[1]
    out_dir = Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)

    doc = numbers_parser.Document(numbers_path)

    reports = []
    for bank_name, bank_config in BANKS.items():
        rows, report = extract_bank_sheet(doc, bank_name, bank_config)
        out_path = out_dir / f"{bank_name.lower()}.csv"
        write_csv(rows, out_path)
        reports.append(report)

        print(f"\n=== {bank_name} ===")
        print(f"  blocos de fatura encontrados: {report.blocks}")
        print(f"  lançamentos extraídos: {report.entries}")
        if report.entries:
            print(f"  período: {report.min_due_date} a {report.max_due_date}")
            print(f"  soma dos valores: R$ {report.total_amount:,.2f}")
        print(f"  linhas puladas (sem valor): {len(report.skipped)}")
        print(f"  CSV gerado: {out_path}")
        if report.anomalies:
            print(f"  anomalias corrigidas/ignoradas ({len(report.anomalies)}):")
            for a in report.anomalies:
                print(f"    - linha {a.row}, col {a.col} ('{a.header_text}'): {a.detail}")

    total_entries = sum(r.entries for r in reports)
    print(f"\n=== Total geral ===\n  {total_entries} lançamentos em {len(reports)} arquivos CSV, em {out_dir}")


if __name__ == "__main__":
    main()
