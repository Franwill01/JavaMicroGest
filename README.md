# MicroGest

**Willy Francis BOUCKA - DITI3 - ISI**
Projet d'évaluation en JavaFX : gestion des adhérents, de l'épargne et des prêts.

## Lancer le projet

Double-cliquer sur **Lancer-MicroGest.cmd**. Au premier lancement, créer le compte administrateur. L'option « Charger des données de test » ajoute des adhérents fictifs pour essayer la pagination.

Le lanceur ouvre le paquet Windows s'il est présent. Sinon, il démarre le projet Maven avec un JDK 17 ou supérieur. La première compilation nécessite une connexion Internet.

## Fonctions

- Connexion avec mot de passe haché et droits selon le rôle.
- Ajout, consultation, modification et suppression contrôlée des adhérents.
- Recherche, pagination en base et filtres par statut, agence et dates.
- Compte d'épargne, dépôts, retraits, demandes de prêts et remboursements.
- Gestion des utilisateurs et des agences.
- Emails de bienvenue et notifications.
- Tableau de bord avec trois indicateurs, un graphique et les dernières opérations.


## Rôles

L'**administrateur** accède à toutes les fonctions. L'**agent** gère les adhérents et saisit les opérations. Le **superviseur** consulte les données, valide les prêts et peut annuler une opération.

## Essai 

1. Ajouter un adhérent.
2. Enregistrer un dépôt de 50 000 F CFA, puis un retrait de 10 000 F CFA.
3. Consulter le compte : le solde doit être 40 000 F CFA.
4. Créer un prêt, le valider avec un responsable et saisir un remboursement.
5. Essayer une recherche avec des filtres, puis changer de page.

Un retrait ne peut pas dépasser le solde. Un prêt utilise un intérêt simple : 100 000 F CFA à 10 % donne 110 000 F CFA à rembourser. Les prêts sont séparés du compte d'épargne.

## Emails

Jakarta Mail (anciennement JavaMail) : envoi des emails de bienvenue et des notifications.

## Données et fichiers

| Élément | Emplacement |
|---|---|
| Base en mode Maven | `data/microgest.mv.db` |
| Base du paquet Windows | `release/MicroGest/data/microgest.mv.db` |
| Code Java et vues | `src/main/` |
| Tests | `src/test/` |
| Script SQL | `src/main/resources/db/schema.sql` |
| Rapport à mon nom | `output/pdf/Rapport-MicroGest.pdf` |
| Version texte du rapport | `docs/RAPPORT.md` |

Les deux modes de lancement utilisent des bases distinctes. Pour sauvegarder, fermer l'application puis copier le dossier `data`.

## Compilation et tests

Depuis PowerShell, dans ce dossier :

```powershell
.\microgest.ps1 run
.\microgest.ps1 test
.\microgest.ps1 package
```

Avec Maven installé et le bon JDK : `mvn javafx:run` ou `mvn test`.

Le code est séparé entre `ui`, `service`, `repository`, `model` et `config`. Les vues sont en FXML et les styles dans `app.css`.

## Dépôt Git

[JavaMicroGest](https://github.com/Franwill01/JavaMicroGest)

Rattrapage DITI3_25-26 - BOUCKA Willy Francis.
