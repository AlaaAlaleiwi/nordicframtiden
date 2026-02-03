import csv
import sys
from pathlib import Path

import re

def to_int(v: str) -> int:
    if v is None:
        return 0

    v = str(v).strip()

    # Remove NBSP and spaces
    v = v.replace("\u00a0", "").replace(" ", "")

    # Keep only leading digits (handles 30B, 31A, etc.)
    m = re.match(r"^(\d+)", v)
    if not m:
        return 0

    return int(m.group(1))

def main():
    args = sys.argv[1:]
    year = int(args[args.index("--year") + 1])
    inp = Path(args[args.index("--input") + 1])
    out = Path(args[args.index("--output") + 1])

    # CSV is semicolon-separated
    with inp.open("r", encoding="latin-1", newline="") as f:
        reader = csv.DictReader(f, delimiter=";")
        rows = list(reader)

    # Exact headers from your file:
    K_YEAR = "År"
    K_DAYS = "Antal dgr"
    K_TABLE = "Tabellnr"
    K_FROM = "Inkomst fr.o.m."
    K_TO = "Inkomst t.o.m."
    K_C1 = "Kolumn 1"
    K_C2 = "Kolumn 2"
    K_C3 = "Kolumn 3"
    K_C4 = "Kolumn 4"
    K_C5 = "Kolumn 5"
    K_C6 = "Kolumn 6"
    K_C7 = "Kolumn 7"

    sql = []
    sql.append(f"-- Auto-generated from Skatteverket CSV for tax year {year}")
    sql.append("BEGIN;")
    sql.append(f"DELETE FROM tax_table_row WHERE tax_year = {year};")
    sql.append(
        "INSERT INTO tax_table_row "
        "(tax_year, days_count, table_number, income_from, income_to, col_1, col_2, col_3, col_4, col_5, col_6, col_7) VALUES"
    )

    values = []
    for r in rows:
        y = to_int(r.get(K_YEAR) or str(year))
        if y != year:
            continue

        days = to_int(r[K_DAYS])
        table = to_int(r[K_TABLE])
        inc_from = to_int(r[K_FROM])
        inc_to = to_int(r[K_TO])

        c1 = to_int(r[K_C1])
        c2 = to_int(r[K_C2])
        c3 = to_int(r[K_C3])
        c4 = to_int(r[K_C4])
        c5 = to_int(r[K_C5])
        c6 = to_int(r[K_C6])
        c7 = to_int(r[K_C7])

        values.append(f"({y},{days},{table},{inc_from},{inc_to},{c1},{c2},{c3},{c4},{c5},{c6},{c7})")

    if not values:
        raise SystemExit("No rows matched the selected --year. Check the CSV year values.")

    sql.append(",\n".join(values) + ";")
    sql.append("COMMIT;")

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(sql), encoding="utf-8")
    print(f"Wrote {out} ({len(values)} rows)")

if __name__ == "__main__":
    main()