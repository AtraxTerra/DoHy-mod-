#!/usr/bin/env python3
"""Genere prices.csv et prices.json a partir de rates.csv.

Modele : le prix de base d'un item decoule du temps necessaire pour l'obtenir.

    base_price = (TARGET_RATE[tier] / rate_per_hour) * MONEY_MULTIPLIER

ou TARGET_RATE est le revenu horaire visé pour ce tier, en debut de partie et
sans booster. Consequence : deux items du meme tier rapportent le meme $/h,
quel que soit leur debit. L'equilibrage global se fait en changeant
MONEY_MULTIPLIER, pas 150 prix a la main.

Usage:
    python3 tools/gen_prices.py                 # multiplicateur 1.0
    python3 tools/gen_prices.py --multiplier 0.6  # economie 40% moins genereuse
"""

import argparse
import csv
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RATES = ROOT / "data" / "rates.csv"
OUT_CSV = ROOT / "data" / "prices.csv"
OUT_JSON = ROOT / "data" / "generated" / "prices.json"

# Revenu horaire vise par tier, en debut de partie, sans booster.
TARGET_RATE = {
    "FOOD": 12_000,
    "FARM": 18_000,
    "MOB": 25_000,
    "FOUND": 40_000,
    "RARE": 60_000,
}

# Demi-saturation de la courbe de prix : quantite vendue a laquelle le prix
# tombe a (1/2)^0.6 = 66% du prix de base.
S0 = {
    "FOOD": 2_000,
    "FARM": 2_000,
    "MOB": 256,
    "FOUND": 64,
    "RARE": 8,
}

# Plancher de prix, en fraction du prix de base. Les items rares ne doivent
# pas s'effondrer : on n'en vend jamais assez pour que ce soit du spam.
FLOOR = {
    "FOOD": 0.15,
    "FARM": 0.15,
    "MOB": 0.20,
    "FOUND": 0.35,
    "RARE": 0.50,
}

CEILING = 1.8          # plafond commun, en fraction du prix de base
DECAY_EXPONENT = 0.6   # exposant de la courbe (voir DESIGN.md 3.2)
BUY_MARKUP = 4.0       # prix d'achat = prix de vente de base x ce facteur

# Items dont le prix d'achat au shop doit rester dissuasif malgre un prix de
# vente bas, parce qu'une source infinie existe (villageois).
FORCED_FLOOR_PRICE = {
    "minecraft:emerald": 2,
}

# Boucle "hall de troc -> emeraudes -> achat au villageois -> revente au shop".
# Tant qu'un villageois vend un item contre N emeraudes, le prix de vente de cet
# item ne peut pas depasser N x prix_emeraude sans creer un revenu infini.
# Format : item -> (emeraudes, unites obtenues).
VILLAGER_TRADES = {
    "minecraft:lapis_lazuli": (1, 1),     # clerc
    "minecraft:redstone": (1, 4),         # clerc
    "minecraft:glowstone_dust": (1, 2),   # clerc
    "minecraft:ender_pearl": (5, 1),      # clerc expert
    "minecraft:quartz": (1, 1),           # macon
    "minecraft:terracotta": (1, 1),       # macon
    "minecraft:arrow": (1, 16),           # archer
    # TODO auditer : briques, argile, papier, verre teinte, charbon, fer
}


def round_sig(x: float, digits: int = 2) -> int:
    """Arrondit a N chiffres significatifs, minimum 1. Des prix lisibles
    (750, 1200, 15) valent mieux que des prix exacts (748.33)."""
    if x <= 0:
        return 1
    magnitude = math.floor(math.log10(x))
    factor = 10 ** (magnitude - digits + 1)
    return max(1, int(round(x / factor) * factor))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--multiplier", type=float, default=1.0)
    parser.add_argument(
        "--no-villager-caps",
        action="store_true",
        help="desactive les plafonds lies au troc villageois (a n'utiliser que "
             "si la partie se joue sans hall de troc)",
    )
    args = parser.parse_args()
    mult = args.multiplier
    caps_on = not args.no_villager_caps

    with RATES.open(encoding="utf-8") as fh:
        rows = list(csv.DictReader(fh))

    # Prix de l'emeraude d'abord : il sert de reference aux plafonds villageois.
    emerald_price = FORCED_FLOOR_PRICE["minecraft:emerald"]
    for row in rows:
        if row["item_id"].strip() == "minecraft:emerald":
            emerald_price = max(
                round_sig(TARGET_RATE[row["tier"].strip()]
                          / float(row["rate_per_hour"]) * mult),
                FORCED_FLOOR_PRICE["minecraft:emerald"],
            )

    items = {}
    out_rows = []
    unknown_tiers = set()
    capped = []

    for row in rows:
        item = row["item_id"].strip()
        if not item or item.startswith("#"):
            continue
        tier = row["tier"].strip()
        if tier not in TARGET_RATE:
            unknown_tiers.add(tier)
            continue

        rate = float(row["rate_per_hour"])
        base = round_sig(TARGET_RATE[tier] / rate * mult)

        if caps_on and item in VILLAGER_TRADES:
            em, units = VILLAGER_TRADES[item]
            cap = max(1, round_sig(em / units * emerald_price))
            if cap < base:
                capped.append((item, base, cap))
                base = cap

        base = max(base, FORCED_FLOOR_PRICE.get(item, 1))

        entry = {
            "category": row["display_category"].strip(),
            "tier": tier,
            "base_price": base,
            "s0": S0[tier],
            "floor_pct": FLOOR[tier],
            "ceiling_pct": CEILING,
            "buy_price": round_sig(base * BUY_MARKUP),
            "sellable": True,
            "rate_per_hour": rate,
        }
        items[item] = entry
        out_rows.append(
            {
                "item_id": item,
                "display_category": entry["category"],
                "tier": tier,
                "rate_per_hour": rate,
                "base_price": base,
                "s0": entry["s0"],
                "floor_pct": entry["floor_pct"],
                "buy_price": entry["buy_price"],
                "notes": row.get("notes", ""),
            }
        )

    if unknown_tiers:
        raise SystemExit(f"Tiers inconnus dans rates.csv : {sorted(unknown_tiers)}")

    payload = {
        "_comment": "GENERE PAR tools/gen_prices.py - NE PAS EDITER A LA MAIN. "
                    "Modifier data/rates.csv puis relancer le script.",
        "model": {
            "money_multiplier": mult,
            "villager_caps_applied": caps_on,
            "emerald_price": emerald_price,
            "decay_exponent": DECAY_EXPONENT,
            "forget_rate_per_20min_playtime": 0.10,
            "target_rate_per_hour": TARGET_RATE,
            "buy_markup": BUY_MARKUP,
        },
        "items": items,
    }

    OUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    OUT_JSON.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n",
                        encoding="utf-8")

    with OUT_CSV.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=list(out_rows[0].keys()))
        writer.writeheader()
        writer.writerows(out_rows)

    # Verification de coherence : le revenu horaire par tier doit etre constant.
    print(f"{len(items)} items generes (multiplicateur {mult})")
    for tier in TARGET_RATE:
        sample = [
            (i, e) for i, e in items.items() if e["tier"] == tier
        ]
        if not sample:
            continue
        rates = [e["base_price"] * e["rate_per_hour"] for _, e in sample]
        lo, hi = min(rates), max(rates)
        print(f"  {tier:<6} {len(sample):>3} items | $/h de {lo:,.0f} a {hi:,.0f} "
              f"(cible {TARGET_RATE[tier]:,})")
    if capped:
        print(f"\nPlafonds villageois appliques (emeraude a {emerald_price}) :")
        for item, before, after in capped:
            print(f"  {item:<28} {before:>6} -> {after}")
    elif caps_on:
        print("\nAucun plafond villageois declenche.")
    else:
        print("\nPlafonds villageois DESACTIVES (--no-villager-caps).")
    print(f"\n-> {OUT_CSV.relative_to(ROOT)}")
    print(f"-> {OUT_JSON.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
