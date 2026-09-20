"""Pose le statut de validation sur les fermes existantes (à lancer UNE fois, avant de déployer
les règles strictes de firebase/firestore.rules).

Sans --apply, le script ne fait que lister ce qu'il ferait (mode « à blanc »).

Installation (une fois) :
    pip install firebase-admin
    # Console Firebase > Paramètres du projet > Comptes de service > Générer une clé privée
    # (fichier JSON à garder HORS du dépôt), puis :
    set GOOGLE_APPLICATION_CREDENTIALS=C:\\chemin\\vers\\cle.json     (PowerShell : $env:GOOGLE_APPLICATION_CREDENTIALS=...)

Exemples :
    python tools/migrate_farm_status.py --list
    python tools/migrate_farm_status.py --keep ID1,ID2                # à blanc
    python tools/migrate_farm_status.py --keep ID1,ID2 --apply        # écrit vraiment
    python tools/migrate_farm_status.py --keep ID1 --trial-days 30 --apply

Règle : une ferme qui a déjà un champ « status » n'est jamais modifiée.
  - ferme dans --keep  -> status = ACTIVE (validée, sans limite)
  - autre ferme        -> status = PENDING, trialEndsAt = maintenant + --trial-days (365 par défaut)
"""
import argparse
import datetime as dt
import sys

import firebase_admin
from firebase_admin import firestore


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--list", action="store_true", help="liste les fermes (id, nom, statut) et quitte")
    parser.add_argument("--keep", default="", help="ids de fermes à valider tout de suite (séparés par des virgules)")
    parser.add_argument("--trial-days", type=int, default=365, help="durée d'essai accordée aux autres (défaut : 365)")
    parser.add_argument("--apply", action="store_true", help="écrit réellement (sinon : simple simulation)")
    args = parser.parse_args()

    firebase_admin.initialize_app()
    db = firestore.client()
    keep = {i.strip() for i in args.keep.split(",") if i.strip()}
    now = dt.datetime.now(dt.timezone.utc)
    trial_end = now + dt.timedelta(days=args.trial_days)

    farms = list(db.collection("fermes").stream())
    if args.list:
        for f in farms:
            d = f.to_dict() or {}
            print(f"{f.id}\t{d.get('name', '?')}\t{d.get('city', '')}\t{d.get('status', '(aucun statut)')}")
        print(f"\n{len(farms)} ferme(s).")
        return 0

    unknown = keep - {f.id for f in farms}
    if unknown:
        print("Ids inconnus dans --keep :", ", ".join(sorted(unknown)))
        return 1

    changed = 0
    for f in farms:
        d = f.to_dict() or {}
        if d.get("status"):
            print(f"= {f.id}  {d.get('name', '?')}: déjà {d['status']}, inchangée")
            continue
        if f.id in keep:
            update = {"status": "ACTIVE"}
            label = "ACTIVE"
        else:
            update = {"status": "PENDING", "trialEndsAt": trial_end}
            label = f"PENDING jusqu'au {trial_end:%d/%m/%Y}"
        print(f"{'*' if args.apply else '?'} {f.id}  {d.get('name', '?')}: {label}")
        if args.apply:
            f.reference.set(update, merge=True)
        changed += 1

    print(f"\n{changed} ferme(s) {'modifiée(s)' if args.apply else 'à modifier (relancer avec --apply)'}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
