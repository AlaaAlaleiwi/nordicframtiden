import csv
import sys
from pathlib import Path

"""
Usage:
  python scripts/convert_tax_tables_csv_to_sql.py \
    --year 2026 \
    --input tax_table_2026.csv \
    --output src/main/resources/db/migration/V20260202__tax_table_2026.sql

CSV expected columns (adjust mapping below if your headers differ):
  tax_year, table_number, income_from, income_to, col_1..col_6
"""

def esc(s: str) -> str:
    return s.replace("'", "''")

def main():
    args = sys.argv[1:]
    year = int(args[args.index("--year") + 1])
    inp = Path(args[args.index("--input") + 1])
    out = Path(args[args.index("--output") + 1])

    with inp.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        rows = list(reader)

    # --- Map CSV headers to our columns ---
    # Change these keys to match your CSV headers exactly:
    k_year = "tax_year"
    k_table = "table_number"
    k_from = "income_from"
    k_to = "income_to"
    k_c1 = "col_1"
    k_c2 = "col_2"
    k_c3 = "col_3"
    k_c4 = "col_4"
    k_c5 = "col_5"
    k_c6 = "col_6"

    sql_lines = []
    sql_lines.append(f"-- Auto-generated tax table rows for year {year}")
    sql_lines.append("BEGIN;")
    sql_lines.append(f"DELETE FROM tax_table_row WHERE tax_year = {year};")

    # Batch insert
    sql_lines.append(
        "INSERT INTO tax_table_row "
        "(tax_year, table_number, income_from, income_to, col_1, col_2, col_3, col_4, col_5, col_6) VALUES"
    )

    values = []
    for r in rows:
        y = int(r.get(k_year) or year)
        table = int(r[k_table])
        inc_from = int(r[k_from])
        inc_to = int(r[k_to])
        c1 = int(r[k_c1]); c2 = int(r[k_c2]); c3 = int(r[k_c3])
        c4 = int(r[k_c4]); c5 = int(r[k_c5]); c6 = int(r[k_c6])

        values.append(f"({y},{table},{inc_from},{inc_to},{c1},{c2},{c3},{c4},{c5},{c6})")

    sql_lines.append(",\n".join(values) + ";")
    sql_lines.append("COMMIT;")

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(sql_lines), encoding="utf-8")
    print(f"Wrote {out} ({len(rows)} rows)")

if __name__ == "__main__":
    main()