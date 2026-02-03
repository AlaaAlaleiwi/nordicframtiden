import json
import sys
from pathlib import Path

"""
Expected input JSON:
[
  {
    "countyCode": "01",
    "countyName": "Stockholms län",
    "municipalities": [
      { "code": "0114", "name": "Upplands Väsby" }
    ]
  }
]
"""

# 🔴 YOU MUST PROVIDE THIS MAP (example values)
MUNICIPALITY_TABLE_MAP = {
    "0114": 33,
    "0115": 33,
    "0180": 33,
    "0126": 34,
    # TODO: fill from Skatteverket source
}

def main():
    args = sys.argv[1:]
    year = int(args[args.index("--year") + 1])
    inp = Path(args[args.index("--input") + 1])
    out = Path(args[args.index("--output") + 1])

    data = json.loads(inp.read_text(encoding="utf-8"))

    rows = []

    for county in data:
        for m in county.get("municipalities", []):
            muni = m["code"].zfill(4)

            if muni not in MUNICIPALITY_TABLE_MAP:
                raise SystemExit(f"Missing tax table for municipality {muni}")

            table = MUNICIPALITY_TABLE_MAP[muni]
            rows.append((muni, year, table))

    if not rows:
        raise SystemExit("No municipalities found")

    sql = []
    sql.append(f"-- Auto-generated municipality_tax_table seed for {year}")
    sql.append("BEGIN;")
    sql.append(f"DELETE FROM municipality_tax_table WHERE tax_year = {year};")
    sql.append(
        "INSERT INTO municipality_tax_table (municipality_code, tax_year, table_number) VALUES"
    )

    sql.append(
        ",\n".join(f"('{m}',{y},{t})" for (m, y, t) in rows) + ";"
    )
    sql.append("COMMIT;")

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(sql), encoding="utf-8")

    print(f"Wrote {out} ({len(rows)} rows)")

if __name__ == "__main__":
    main()