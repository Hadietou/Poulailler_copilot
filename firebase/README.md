# Validation des fermes : mise en place

## Principe

- Chaque ferme (`fermes/{id}`) porte un **statut** : `PENDING` (essai), `ACTIVE` (validée), `SUSPENDED`.
- À la création, une ferme est `PENDING` avec `trialEndsAt` = **1 an** (l'heure est contrôlée par les règles).
- Passé `trialEndsAt` sans validation, ou si la ferme est `SUSPENDED` : **lecture seule**. Le serveur refuse
  toute écriture, quelle que soit la version de l'app.
- L'app prévient le responsable **tous les 3 mois** d'essai (3, 6, 9, 12 mois) tant que la ferme n'est pas
  validée, avec un bouton « Demander la validation » (ouvre un email pré-rempli, rien n'est envoyé sans son accord).
- **Valider une ferme** : console Firebase > Firestore > `fermes` > la ferme > champ `status` = `ACTIVE`.
  (L'email de création contient le lien direct et l'identifiant de la ferme.)

## Ordre de mise en place (ne pas l'inverser)

1. **Publier la nouvelle version de l'app** (statut lu sur la ferme, messages clairs, rappels).
2. **Migrer les fermes existantes** : `tools/migrate_farm_status.py` (voir l'en-tête du script).
   - `python tools/migrate_farm_status.py --list` pour voir les fermes ;
   - à blanc : `--keep ID1,ID2` ; puis `--apply` pour écrire.
   - Les fermes listées dans `--keep` deviennent `ACTIVE` ; les autres `PENDING` avec 1 an d'essai.
3. **Attendre que la plupart des testeurs aient mis à jour** l'app. Les anciennes versions n'affichent pas
   de message « validation requise » : elles recevront une erreur d'accès refusé.
4. **Tester les règles** avec l'émulateur (ci-dessous), puis les déployer.

## Tester puis déployer les règles

```
npm install -g firebase-tools
firebase login
firebase init firestore        # choisir le projet pondeuses-eec3f, garder firebase/firestore.rules
firebase emulators:start --only firestore
# une fois les tests concluants :
firebase deploy --only firestore:rules
```

Les règles peuvent aussi être collées dans Console Firebase > Firestore > Règles > Publier. Gardez une copie des
règles actuelles avant de les remplacer : la console conserve l'historique et permet de revenir en arrière.

À vérifier avant déploiement :
- une ferme `PENDING` dans son année d'essai : lecture et écriture OK ;
- une ferme `PENDING` dont `trialEndsAt` est passé : lecture OK, écriture refusée ;
- une ferme sans champ `status` : écriture refusée (d'où la migration) ;
- un utilisateur qui tente de modifier `status` / `trialEndsAt` : refusé ;
- création de ferme depuis l'app, acceptation d'une invitation, changement de ferme.

## Ce que les règles cassent volontairement

- Les anciens comptes « agent pré-créé » (document `users/{email}` lu avant connexion) ne peuvent plus se
  connecter : ils sont remplacés par les invitations par téléphone.

## Limites connues — Phase 2

- `users/{uid}.farmId` et `role` sont encore écrits par l'app : un client modifié pourrait se rattacher à une ferme
  qui n'est pas la sienne. Correctif : des Cloud Functions (`createFarm`, `acceptInvitation`) qui font ces écritures
  avec les droits administrateur (plan Firebase Blaze).
- Un utilisateur peut créer plusieurs fermes, chacune avec son année d'essai. Là aussi, une Cloud Function
  `createFarm` peut limiter le nombre de fermes par numéro de téléphone.
- L'email de création part encore depuis l'app avec la clé Brevo compilée dans l'APK : **révoquer cette clé**
  dans Brevo et déplacer l'envoi dans une Cloud Function (clé stockée en secret).
- App Check (Play Integrity) pour que seules les vraies installations de l'app puissent joindre Firestore
  (prévoir des jetons de debug pour l'émulateur et les builds de test).

Ne jamais commiter la clé de compte de service (`*-firebase-adminsdk-*.json`) utilisée par le script de migration.
