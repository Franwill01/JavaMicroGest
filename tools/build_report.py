from pathlib import Path
from xml.etree import ElementTree as ET
from reportlab.pdfgen import canvas
from reportlab.lib.colors import HexColor, white
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import Paragraph, Table, TableStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.utils import ImageReader
import re

BASE=Path(__file__).resolve().parents[1]
OUT=BASE/'output/pdf/Rapport-MicroGest.pdf'
OUT.parent.mkdir(parents=True,exist_ok=True)
for name,file in [('Body','segoeui.ttf'),('Bold','segoeuib.ttf')]:
    pdfmetrics.registerFont(TTFont(name,str(Path('C:/Windows/Fonts')/file)))
INK=HexColor('#222222'); GREEN=HexColor('#333333'); MUTED=HexColor('#647C8A'); LIGHT=HexColor('#ECF3F6'); BORDER=HexColor('#DCE5EB')
W,H=595.28,841.89
style=ParagraphStyle('body',fontName='Body',fontSize=11,leading=17,textColor=INK,spaceAfter=10)
small=ParagraphStyle('small',parent=style,fontSize=8.8,leading=13)
cell=ParagraphStyle('cell',parent=style,fontSize=9,leading=13)
c=canvas.Canvas(str(OUT),pagesize=(W,H));c.setTitle('MicroGest - Rapport technique');c.setAuthor('Willy Francis BOUCKA')
page=0;y=0;md=[]
def begin(title,kicker='RAPPORT TECHNIQUE'):
    global page,y
    page+=1;c.setFillColor(GREEN);c.rect(44,H-43,W-88,0.5,fill=1,stroke=0)
    c.setFillColor(MUTED);c.setFont('Bold',8);c.drawString(44,H-40,'MICROGEST / '+kicker)
    c.setFillColor(INK);c.setFont('Bold',24);c.drawString(44,H-82,title)
    c.setStrokeColor(BORDER);c.line(44,48,W-44,48)
    c.setFont('Body',8);c.setFillColor(MUTED);c.drawString(44,31,'Willy Francis BOUCKA - DITI3 - ISI');c.drawRightString(W-44,31,f'{page} / 10')
    y=H-112;md.append('\n# '+title+'\n')
def p(text,compact=False):
    global y
    para=Paragraph(text,small if compact else style);_,h=para.wrap(W-88,700)
    assert y-h>60,(page,text[:50],y,h)
    para.drawOn(c,44,y-h);y-=h+12;md.append(re.sub('<[^>]*>','',text.replace('<br/>','\n\n'))+'\n')
def h(text):
    global y
    y-=8;c.setFillColor(GREEN);c.setFont('Bold',13);c.drawString(44,y,text);y-=24;md.append('## '+text+'\n')
def table(rows,widths=None):
    global y
    data=[[Paragraph(str(v),cell) for v in row] for row in rows]
    t=Table(data,colWidths=widths or [(W-88)/len(rows[0])]*len(rows[0]))
    t.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),LIGHT),('GRID',(0,0),(-1,-1),.5,BORDER),('VALIGN',(0,0),(-1,-1),'TOP'),('LEFTPADDING',(0,0),(-1,-1),10),('RIGHTPADDING',(0,0),(-1,-1),10),('TOPPADDING',(0,0),(-1,-1),8),('BOTTOMPADDING',(0,0),(-1,-1),8)]))
    _,height=t.wrap(W-88,700);assert y-height>60,(page,'table',y,height);t.drawOn(c,44,y-height);y-=height+18
    table_lines=['| ' + ' | '.join(str(v) for v in rows[0]) + ' |', '| ' + ' | '.join('---' for v in rows[0]) + ' |']
    table_lines += ['| ' + ' | '.join(str(v) for v in row) + ' |' for row in rows[1:]]
    md.append('\n'.join(table_lines)+'\n')

def screenshot(file,caption):
    global y
    path=BASE/'docs/images'/file;img=ImageReader(str(path));iw,ih=img.getSize();width=W-88;height=width*ih/iw
    assert y-height-30>60,(page,'image',y,height)
    c.drawImage(img,44,y-height,width,height);c.setStrokeColor(BORDER);c.rect(44,y-height,width,height,stroke=1,fill=0);y-=height+18
    c.setFont('Body',8);c.setFillColor(MUTED);c.drawString(44,y,caption);y-=21
    md.append(f'![{caption}](images/{file})\n')
def end():c.showPage()
def box(x,top,w,title,lines):
    height=34+len(lines)*17;c.setFillColor(LIGHT);c.setStrokeColor(BORDER);c.roundRect(x,top-height,w,height,7,fill=1,stroke=1)
    c.setFont('Bold',10);c.setFillColor(INK);c.drawString(x+10,top-21,title);c.line(x,top-31,x+w,top-31);c.setFont('Body',8.5)
    for i,line in enumerate(lines):c.drawString(x+10,top-48-i*17,line)
    return height

def arrow(x1,y1,x2,y2,label=''):
    import math
    c.setStrokeColor(GREEN);c.setFillColor(GREEN);c.setLineWidth(1.2);c.line(x1,y1,x2,y2);a=math.atan2(y2-y1,x2-x1)
    for d in [-.45,.45]:c.line(x2,y2,x2-7*math.cos(a+d),y2-7*math.sin(a+d))
    if label:c.setFont('Body',8);c.drawString((x1+x2)/2+4,(y1+y2)/2+4,label)

begin('MicroGest', 'PROJET DE FIN DE MODULE')
h('Gestion simple d’une microfinance')
p('<b>Présenté par : Willy Francis BOUCKA</b><br/>Classe : DITI3<br/>Établissement : ISI<br/>Module : Développement d’applications de bureau en Java')
p('Ce rapport présente mon projet MicroGest. Il s’agit d’une petite application JavaFX pour gérer des adhérents, leurs dépôts, leurs retraits et leurs prêts. Je me limite aux fonctions demandées dans les consignes afin de garder un projet compréhensible et facile à présenter.')
h('Sommaire')
table([['Partie','Page'],['1. Présentation et objectifs','2'],['2. Utilisateurs et besoins','3'],['3. Modèle de données','4'],['4. Organisation du code','5'],['5. Gestion des adhérents','6'],['6. Comptes et opérations','7'],['7. Connexion et emails','8'],['8. Tableau de bord et vérifications','9'],['9. Difficultés et bilan','10']],[430,77])
p('Le projet utilise JavaFX, FXML, CSS, Maven et Hibernate avec une base H2 locale. Les captures du rapport utilisent uniquement des données fictives.',True)
end()
begin('1. Présentation et objectifs')
h('Le sujet')
p('Le sujet proposé dans le module est la gestion d’un établissement de microfinance. L’application doit permettre de retrouver un adhérent et de suivre ses opérations courantes. Pour ce projet, je retiens l’adhésion, l’épargne et un suivi simple des prêts.')
p('Je ne cherche pas à reproduire tout le fonctionnement d’une banque. Mon objectif est de réunir les notions du module dans une application locale : un formulaire, un tableau, des règles de validation et une base de données qui conserve les informations après fermeture.')
h('Les fonctions à réaliser')
table([['Fonction','Ce que l’application doit faire'],['Connexion','Vérifier un identifiant et un mot de passe, puis appliquer les droits du rôle.'],['Adhérents','Ajouter, consulter, modifier et supprimer quand cela est possible.'],['Listes','Combiner une recherche, des filtres et une pagination en base.'],['Opérations','Enregistrer les dépôts, retraits, prêts et remboursements.'],['Emails','Jakarta Mail (anciennement JavaMail) : envoi d’emails.'],['Accueil','Afficher quelques chiffres et un graphique.']],[130,377])
h('Les choix retenus')
p('J’utilise H2 pour éviter l’installation d’un serveur de base de données pendant l’évaluation. Maven gère les bibliothèques. Les écrans sont décrits en FXML et les couleurs restent dans une feuille CSS séparée. Cette organisation permet de retrouver les différentes parties du projet sans multiplier les outils.')
p('Le rapport présente le fonctionnement de l’application, son organisation et les principaux cas vérifiés.',True)
end()
begin('2. Utilisateurs et besoins')
p('Les consignes demandent au moins deux profils. Le projet conserve les trois rôles du sujet : administrateur, agent et superviseur. Cela permet de montrer clairement la différence entre la saisie des opérations et leur validation.')
table([['Rôle','Actions principales'],['Administrateur','Gérer les utilisateurs et les agences. Accéder aux adhérents, aux opérations et au suivi des emails. Valider les prêts.'],['Agent','Gérer les adhérents. Saisir les dépôts, retraits, demandes de prêts et remboursements.'],['Superviseur','Consulter les données et les statistiques. Valider un prêt ou annuler une opération.']],[130,377])
h('Exemple de parcours')
p('Un agent se connecte, ouvre la liste des adhérents et ajoute une personne. Un compte d’épargne est créé automatiquement. L’agent peut ensuite enregistrer un dépôt ou un retrait. Pour un prêt, il saisit la demande ; un responsable doit la valider avant les remboursements.')
h('Les contrôles de saisie')
p('Les champs indispensables ne peuvent pas rester vides. Le téléphone doit avoir un format accepté et ne pas être déjà utilisé. L’adresse email est vérifiée. Les montants doivent être positifs et entiers en F CFA. Une date de fin de filtre ne doit pas précéder la date de début.')
p('En cas d’erreur, un message explique ce qui doit être corrigé. Le formulaire reste ouvert pour éviter de tout saisir à nouveau. Les droits sont aussi contrôlés dans les services : masquer un bouton ne suffit pas pour interdire une action.')
end()
begin('3. Modèle de données')
p('Le modèle repose sur l’adhérent et ses opérations. Une agence regroupe plusieurs adhérents. Chaque adhérent possède un compte d’épargne et peut avoir plusieurs opérations. Les entités sont des classes Java annotées avec JPA.')
top=y
box(44,top,180,'Agency',['id','name','address'])
box(326,top,225,'Adherent',['id / number','fullName / phone / email','status / joined','agency'])
arrow(224,top-50,326,top-50,'1 : *')
second=top-160
box(44,second,180,'SavingsAccount',['id / number','opened','member'])
box(326,second,225,'Operation',['kind / state / amount','interest / dueDate','member','loan'])
arrow(410,top-111,410,second,'1 : *')
arrow(326,top-94,224,second-35,'1 : 1')
y=second-145
p('Le champ loan d’une opération relie un remboursement au prêt concerné. Le solde du compte est calculé à partir des dépôts et des retraits validés. Il n’est pas saisi manuellement.')
p('AccountUser représente les comptes de connexion. Email contient les messages en attente ou envoyés. Une petite table Audit conserve une trace interne des modifications.',True)
h('Création de la base')
p('Le fichier src/main/resources/db/schema.sql crée les tables et les relations. Hibernate vérifie ensuite que les entités correspondent au schéma. Les contraintes d’unicité et les clés étrangères complètent les vérifications faites en Java.',True)
end()
begin('4. Organisation du code')
p('J’ai retenu une organisation en couches pour séparer les écrans, les règles de gestion et les accès à la base. L’idée est simple : un contrôleur ne doit pas calculer lui-même un solde ou décider seul des droits d’un utilisateur.')
table([['Dossier','Contenu'],['ui','Contrôleurs des vues FXML, navigation, tableaux et formulaires.'],['service','Connexion, règles de gestion, opérations et emails.'],['repository','Requêtes de recherche, filtres, comptage et pagination.'],['model','Entités JPA : Adherent, Operation, Agency et autres données.'],['config','Ouverture de la base et emplacement des fichiers.']],[130,377])
h('Du bouton à la base')
p('Quand l’utilisateur enregistre un adhérent, le contrôleur récupère les champs. Le service vérifie le rôle et les valeurs. La transaction enregistre les données avec Hibernate. Si tout se passe bien, le tableau est actualisé. Sinon, les modifications sont annulées et un message est affiché.')
h('Les vues')
p('Le projet contient une vue de connexion, une fenêtre principale avec menu, un tableau de bord, une vue de liste commune et un dialogue de formulaire. Les champs des formulaires sont ajoutés dans la grille de ce dialogue selon les données à saisir.')
p('Les vues sont écrites en FXML et les styles sont définis dans app.css.')
h('Les dépendances')
p('Maven fournit JavaFX, Hibernate, H2 et Jakarta Mail. Le code cible Java 17 ou supérieur.',True)
end()
begin('5. Gestion des adhérents')
p('Le formulaire d’adhésion demande le nom, le téléphone, l’email, l’adresse, l’agence et le statut. À l’enregistrement, l’application attribue un numéro et ouvre le compte d’épargne. La liste permet ensuite de consulter ou modifier la fiche.')
screenshot('04-members.png','Figure 1. Liste des adhérents avec recherche, filtres et pagination.')
h('Recherche, filtres et pages')
p('La recherche accepte le nom, le numéro ou le téléphone. Elle peut être combinée avec le statut, l’agence et une période. La pagination utilise deux requêtes : une pour compter les résultats et une pour récupérer la page avec setFirstResult et setMaxResults.')
p('L’utilisateur peut choisir le nombre de lignes, passer à la page suivante ou saisir un numéro de page. Les filtres s’appliquent au total comme aux lignes affichées.',True)
p('Un adhérent sans opération peut être supprimé. Si des opérations existent, la suppression est refusée afin de ne pas perdre les mouvements. Le statut suspendu permet de bloquer les nouvelles opérations.',True)
end()
begin('6. Comptes et opérations')
h('Dépôts et retraits')
p('Le solde correspond aux dépôts validés moins les retraits validés. Les montants sont stockés avec BigDecimal pour éviter les approximations des nombres à virgule flottante. Le compte ne peut pas devenir négatif après un retrait.')
table([['Opération','Résultat'],['Dépôt de 50 000 F CFA','Le solde devient 50 000 F CFA.'],['Retrait de 10 000 F CFA','Le solde devient 40 000 F CFA.'],['Retrait de 40 001 F CFA','La demande est refusée : solde insuffisant.']],[240,267])
h('Le prêt')
p('Une demande de prêt contient le capital, le taux total et l’échéance. Elle commence avec le statut EN_ATTENTE. Un administrateur ou un superviseur peut la valider. La date d’échéance doit être future au moment de cette validation.')
p('Le calcul reste un intérêt simple : pour un capital de 100 000 F CFA à 10 %, le total à rembourser est 110 000 F CFA. Après un remboursement de 60 000 F CFA, le restant dû est 50 000 F CFA. L’application refuse un remboursement supérieur à ce restant.')
h('Modifier ou annuler')
p('Une demande de prêt en attente peut être modifiée ou supprimée. Une opération validée peut être annulée par un responsable, mais reste enregistrée. L’annulation est refusée si elle rend le solde négatif. Un prêt avec des remboursements validés ne peut pas être annulé directement.')
p('Les prêts et leurs remboursements sont séparés de l’épargne.',True)
end()
begin('7. Connexion et emails')
h('Connexion')
p('Au premier lancement, un formulaire permet de créer le compte administrateur. Le mot de passe est haché avant son stockage ; il n’apparaît jamais en clair dans la base. Le projet utilise PBKDF2 avec un sel aléatoire.')
p('Après la connexion, le menu dépend du rôle. L’administrateur peut créer les autres utilisateurs et désactiver un compte. Chaque utilisateur peut changer son propre mot de passe. Les services vérifient le compte et le rôle à chaque action.')
h('Envoi des emails')
p('Jakarta Mail (anciennement JavaMail) assure l’envoi d’emails. La création d’un adhérent prépare un message de bienvenue. Des notifications sont également prévues pour les opérations et les prêts.')
table([['État','Signification'],['EN_ATTENTE','Le message attend son envoi ou une nouvelle tentative.'],['ENVOYE','Le message a été envoyé.'],['ECHEC','Les tentatives ont échoué ; un administrateur peut relancer.']],[130,377])
end()
begin('8. Tableau de bord et tests')
p('L’accueil affiche trois indicateurs : le nombre d’adhérents, l’épargne totale et les opérations du mois. Un graphique compare les dépôts et les retraits des six derniers mois. Les dernières opérations sont listées en dessous.')
screenshot('03-dashboard.png','Figure 2. Tableau de bord de MicroGest.')
h('Vérifications')
reports=list((BASE/'target/surefire-reports').glob('TEST-*.xml'))
roots=[ET.parse(f).getroot() for f in reports]
tests=sum(int(r.attrib['tests']) for r in roots);fail=sum(int(r.attrib['failures'])+int(r.attrib['errors']) for r in roots)
p(f'Les vérifications automatisées donnent {tests} tests exécutés et {fail} échec ou erreur. Elles portent notamment sur les accès, les doublons, la pagination, le solde, les prêts et la conservation des données après réouverture.')
p('Un scénario charge les vues FXML, remplit le formulaire d’un adhérent puis enregistre un dépôt. Il vérifie que le solde obtenu correspond au montant saisi. Les tests utilisent une base séparée de celle de l’application.',True)
p('Ces vérifications couvrent les principaux cas du projet.',True)
end()
begin('9. Difficultés et bilan')
h('Les points à surveiller')
p('Ce projet n’a pas été simple pour moi. Pour garder un travail abordable, je me suis limité aux fonctions nécessaires et à des règles de gestion faciles à expliquer. Le but reste de présenter une application qui fonctionne sur un poste, avec un code que je peux reprendre étape par étape.')
p('La recherche et la pagination demandent une attention particulière : le total des résultats et la page affichée doivent utiliser les mêmes critères. Pour les opérations, il faut aussi éviter qu’un retrait ou une annulation rende le solde négatif. Ces deux points justifient les contrôles présents dans les services.')
h('Bilan')
p('Le projet réunit les principales notions du module : JavaFX et FXML pour les écrans, Hibernate pour la persistance, et des services pour les règles de gestion. L’essentiel pour moi est de pouvoir expliquer le chemin suivi par une donnée, depuis le formulaire jusqu’à son enregistrement en base.')
h('Documents et références')
p('Le dépôt Git du projet est accessible à cette adresse : github.com/Franwill01/JavaMicroGest.',True)
p('Références : consignes du module ; documentation OpenJFX (openjfx.io/openjfx-docs/) ; documentation Hibernate ORM 6.6 (hibernate.org/orm/documentation/6.6/).',True)
end()
assert page==10
c.save()
(BASE/'docs/RAPPORT.md').write_text('\n'.join(md),encoding='utf-8')
print(f'Rapport de {page} pages : {OUT}')
