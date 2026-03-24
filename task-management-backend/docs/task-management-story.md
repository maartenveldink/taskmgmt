h1. Doel

Valideren of een CQRS-architectuur met het Axon Framework een passende en beheersbare oplossing is voor een TaskManagement systeem, in vergelijking met een traditionele CRUD-aanpak. Het resultaat is een
go/no-go beslissing voor de verdere architectuurkeuze.

h1. Context

We starten een greenfield TaskManagement systeem waarbij taken worden aangemaakt door een extern systeem en worden afgehandeld door interne gebruikers of gebruikersgroepen. Voordat we een
architectuurkeuze maken, willen we via een tijdgebonden PoC (2 weken) de haalbaarheid, complexiteit en onderhoudbaarheid van CQRS met Axon toetsen aan een concrete use case. De tech stack is Java,
Quarkus, Axon Framework en Kubernetes. De focus ligt op de backend; een frontend is een nice-to-have.

h1. Omschrijving

Implementeer de volgende end-to-end use case als PoC:

* Een extern systeem stuurt een command om een nieuwe taak aan te maken.
* Het systeem slaat de taak op en wijst deze toe aan een gebruiker of gebruikersgroep.
* Gebruikers kunnen hun toegewezen taken inzien (Query-side / read model).
* Een gebruiker zet een taak op 'In Progress'.
* Een gebruiker zet een taak op 'Done'.

De implementatie bevat minimaal:
* Een Aggregate voor de taak met bijbehorende Commands en Events (Axon).
* Een Command Handler voor het aanmaken en statuswijzigen van taken.
* Een Event Handler die een read model opbouwt voor de query-side.
* Een Query Handler voor het ophalen van taken per gebruiker of groep.
* Een REST API (Quarkus) als ingang voor zowel externe commands als gebruikersacties.

*Saga — Deadline management*
* Een Saga die start bij het aanmaken van een taak en de deadline bewaakt.
* Als een taak niet binnen de gestelde deadline op 'Done' staat, triggert de Saga een vervolgactie (bijv. een notificatie-event of escalatiecommand).
* Dit valideert het Axon Saga-concept als mechanisme voor tijdgebonden proceslogica.

*Audittrail projectie*
* Naast het functionele read model wordt een aparte projectie opgebouwd die alle events op een taak registreert als audittrail.
* De audittrail is opvraagbaar per taak en toont minimaal: tijdstip, eventtype en relevante payload (bijv. statuswijziging, toewijzing).
* Dit demonstreert de toegevoegde waarde van event sourcing voor traceerbaarheid.

Na oplevering voert het team een gezamenlijke review uit waarbij de implementatie wordt vergeleken met een equivalente CRUD-aanpak op de volgende criteria:
* Complexiteit van de code en de architectuur.
* Onderhoudbaarheid op de lange termijn.
* Hoeveelheid boilerplate / framework-overhead.
* Begrijpelijkheid voor nieuwe teamleden.

h1. Acceptatiecriteria

* De volledige use case (aanmaken → toewijzen → in progress → done) werkt end-to-end via de REST API.
* Commands en Events zijn correct gemodelleerd en gedocumenteerd in de code.
* Het read model toont de juiste taakoverzichten per gebruiker/gebruikersgroep.
* De Saga bewaakt de deadline en triggert aantoonbaar een vervolgactie bij het overschrijden ervan.
* De audittrail projectie is opvraagbaar en toont een correcte chronologische weergave van alle events per taak.
* De applicatie draait lokaal (of op een dev Kubernetes cluster).
* Het team heeft de review uitgevoerd en een gedocumenteerde go/no-go beslissing opgesteld, inclusief onderbouwing op basis van de reviewcriteria.
* Er is een korte vergelijking (bijv. als README of ADR) aanwezig tussen de CQRS-implementatie en hoe dezelfde use case eruit zou zien als CRUD.

h1. Testen

* Handmatige integratietest via REST API (bijv. Postman of curl) die de volledige use case doorloopt.
* Unit tests op de Aggregate en Command/Event Handlers.
* Test die de Saga triggert door een taak bewust de deadline te laten overschrijden (bijv. via een korte test-deadline).
* Verificatie dat de audittrail alle verwachte events bevat na doorlopen van de use case.
* Verificatie dat het functionele read model consistent is na het verwerken van events.
* (Nice-to-have) Angular frontend toont taakoverzicht voor een testgebruiker.

h1. Randvoorwaarden

* Doorlooptijd: maximaal 2 weken.
* Scope: backend is verplicht, Angular frontend is nice-to-have.
* Tech stack: Java, Quarkus, Axon Framework, Kubernetes.
* Er wordt geen productie-waardige oplossing verwacht; de PoC hoeft niet te voldoen aan security- of performance-eisen.
* Het team is beschikbaar voor de gezamenlijke review aan het einde van de 2 weken.
* Er is een vergelijkingsreferentie nodig: het team moet voldoende kennis hebben van CRUD om de vergelijking eerlijk te maken.