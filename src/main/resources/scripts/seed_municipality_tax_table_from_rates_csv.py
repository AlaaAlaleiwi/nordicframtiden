import csv
import sys
from pathlib import Path
import math

def parse_rate(s: str) -> float:
    # Swedish decimal comma -> dot
    s = (s or "").strip().replace("\u00a0", " ").replace(" ", "")
    s = s.replace(",", ".")
    if not s:
        raise ValueError("Empty rate")
    return float(s)

def table_from_rate(rate: float) -> int:
    # rounding: >= .50 => up, otherwise down
    return int(math.floor(rate + 0.5))

def main():
    args = sys.argv[1:]
    year = int(args[args.index("--year") + 1])
    inp = Path(args[args.index("--input") + 1])
    out = Path(args[args.index("--output") + 1])

    # This file (yours) is often ISO-8859-1 and semicolon separated
    with inp.open("r", encoding="ISO-8859-1", newline="") as f:
        reader = csv.DictReader(f, delimiter=";")

        # Expected headers in your CSV (based on the file you uploaded)
        K_CODE = "Församlings-kod"
        K_SUM_EXKL = "Summa, exkl. kyrkoavgift"

        # Build municipality_code -> rate (they are identical within a municipality)
        muni_rate = {}
        for r in reader:
            raw_code = (r.get(K_CODE) or "").strip()
            if not raw_code:
                continue
            muni_code = raw_code.replace(" ", "").zfill(4)  # "01 14" -> "0114"

            rate = parse_rate(r.get(K_SUM_EXKL))
            muni_rate[muni_code] = rate

    if not muni_rate:
        raise SystemExit("No municipality rates parsed - check delimiter/encoding/headers.")

    # Build INSERT rows
    rows = []
    for muni_code, rate in sorted(muni_rate.items()):
        table = table_from_rate(rate)
        rows.append((muni_code, year, table))

    sql = []
    sql.append(f"-- Auto-generated from Skatteverket 'Skattesatser kommuner' for {year}")
    sql.append("BEGIN;")
    sql.append(f"DELETE FROM municipality_tax_table WHERE tax_year = {year};")
    sql.append("INSERT INTO municipality_tax_table (municipality_code, tax_year, table_number) VALUES")

    sql.append(",\n".join([f"('{m}',{y},{t})" for (m, y, t) in rows]) + ";")
    sql.append("COMMIT;")

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(sql), encoding="utf-8")
    print(f"Wrote {out} ({len(rows)} municipalities)")

if __name__ == "__main__":
    main()