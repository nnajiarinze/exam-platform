#!/usr/bin/env python3
"""Guarded, deterministic local demonstration-data reset and seed utility."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass

NAMESPACE = uuid.UUID("9c1fd12e-c5b2-4d56-a15e-c1d41e967ced")
FIXED_AT = "2026-06-15T09:00:00Z"
FIXED_DATE = "2026-06-15"
EXAM_ID = "10000000-0000-5000-8000-000000000001"
HISTORICAL_VERSION_ID = "10000000-0000-5000-8000-000000000011"
CURRENT_VERSION_ID = "10000000-0000-5000-8000-000000000012"
HISTORICAL_RELEASE_ID = "60000000-0000-5000-8000-000000000001"
CURRENT_RELEASE_ID = "60000000-0000-5000-8000-000000000002"
DRAFT_RELEASE_ID = "60000000-0000-5000-8000-000000000003"
BLUEPRINT_ID = "70000000-0000-5000-8000-000000000001"
DEMO_LEARNER_ID = "80000000-0000-5000-8000-000000000001"


@dataclass(frozen=True)
class TopicData:
    code: str
    name: str
    description: str
    source: tuple[str, str, str]
    facts: tuple[str, str, str, str, str]
    misconceptions: tuple[str, str, str]


SUBJECTS: tuple[tuple[str, str, str, tuple[TopicData, ...]], ...] = (
    ("DEMOCRACY", "Demokrati och statsskick", "Hur demokratin, staten och den offentliga makten fungerar.", (
        TopicData("CONSTITUTION", "Grundlagarna", "Sveriges konstitutionella grunder.", ("Sveriges riksdag", "Sveriges grundlagar", "https://www.riksdagen.se/sv/sa-fungerar-riksdagen/demokrati/grundlagarna/"), (
            "Sverige har fyra grundlagar.", "Regeringsformen beskriver hur Sverige styrs och hur den offentliga makten är organiserad.", "Tryckfrihetsförordningen skyddar rätten att ge ut tryckta skrifter.", "Yttrandefrihetsgrundlagen skyddar yttrandefrihet i bland annat radio, tv och vissa digitala medier.", "Successionsordningen reglerar vem som kan ärva den svenska tronen."),
            ("Vanliga myndighetsföreskrifter har samma ställning som grundlag.", "Regeringen kan ensam ändra en grundlag över en natt.", "Kommunfullmäktige beslutar om Sveriges grundlagar.")),
        TopicData("RIKSDAG_GOVERNMENT", "Riksdag och regering", "Skillnaden mellan lagstiftande och styrande ansvar.", ("Sveriges riksdag", "Riksdagens uppgifter", "https://www.riksdagen.se/sv/sa-fungerar-riksdagen/riksdagens-uppgifter/"), (
            "Riksdagen beslutar om lagar i Sverige.", "Riksdagen beslutar om statens budget.", "Riksdagen granskar regeringen.", "Regeringen styr landet och genomför riksdagens beslut.", "Statsministern leder regeringens arbete."),
            ("Polismyndigheten beslutar om statens budget.", "Domstolarna utser alla riksdagsledamöter.", "Kommunerna stiftar nationella lagar.")),
        TopicData("ELECTIONS", "Val och politiskt deltagande", "Fria val och sätt att delta i demokratin.", ("Valmyndigheten", "Det svenska valsystemet", "https://www.val.se/servicelankar/other-languages/english-engelska/the-swedish-electoral-system.html"), (
            "Val i Sverige ska vara fria, hemliga och direkta.", "Riksdagen väljs genom proportionella val.", "Politiska partier konkurrerar om väljarnas röster.", "Rösträtten beror på vilket val det gäller och på väljarens rättsliga anknytning till Sverige.", "Invånare kan påverka samhället även mellan val, exempelvis genom föreningar och kontakt med förtroendevalda."),
            ("Myndigheter bestämmer hur varje person ska rösta.", "Alla valsedlar måste vara offentligt undertecknade.", "Det är förbjudet att kontakta en förtroendevald mellan valen.")),
        TopicData("LOCAL_GOVERNMENT", "Kommuner och regioner", "Lokalt och regionalt självstyre.", ("Sveriges Kommuner och Regioner", "Kommuner och regioner", "https://skr.se/skr/tjanster/kommunerochregioner.431.html"), (
            "Kommuner ansvarar för många lokala tjänster, däribland socialtjänst och grundskola.", "Regioner har ett stort ansvar för hälso- och sjukvård.", "Regioner ansvarar också för regional utveckling och kollektivtrafik.", "Kommuner och regioner styrs av folkvalda församlingar.", "Det kommunala självstyret är en viktig del av den svenska demokratin."),
            ("Kommuner ansvarar för Sveriges försvarsmakt.", "Regioner stiftar Sveriges grundlagar.", "Lokala politiska beslut fattas enbart av statliga ministrar.")),
    )),
    ("RIGHTS_LAW", "Rättigheter, skyldigheter och lag", "Rättigheter, likabehandling och rättsstatens principer.", (
        TopicData("RIGHTS", "Grundläggande fri- och rättigheter", "Centrala demokratiska rättigheter.", ("Sveriges riksdag", "Fri- och rättigheter", "https://www.riksdagen.se/sv/sa-fungerar-riksdagen/demokrati/grundlagarna/"), (
            "Regeringsformen skyddar flera grundläggande fri- och rättigheter.", "Mötesfrihet innebär rätt att ordna och delta i sammankomster.", "Religionsfrihet skyddar rätten att ensam eller tillsammans med andra utöva religion.", "Föreningsfrihet skyddar rätten att sammansluta sig med andra.", "Grundläggande rättigheter kan ha lagliga begränsningar som måste följa grundlagen."),
            ("Endast myndighetsanställda omfattas av grundläggande rättigheter.", "Föreningar måste alltid ledas av en minister.", "Religionsfrihet innebär skyldighet att tillhöra ett trossamfund.")),
        TopicData("EQUALITY", "Jämlikhet och diskriminering", "Likabehandling och skydd mot diskriminering.", ("Diskrimineringsombudsmannen", "Diskriminering och diskrimineringslagen", "https://www.do.se/diskriminering"), (
            "Diskrimineringslagen förbjuder diskriminering inom flera samhällsområden.", "Diskrimineringsgrunderna omfattar bland annat kön, etnisk tillhörighet och funktionsnedsättning.", "Diskrimineringsombudsmannen arbetar mot diskriminering och för lika rättigheter.", "Trakasserier kan vara en form av diskriminering när de har samband med en diskrimineringsgrund.", "Arbetsgivare och utbildningsanordnare har ansvar för aktiva åtgärder mot diskriminering."),
            ("Diskrimineringslagen gäller bara vid allmänna val.", "En arbetsgivare får alltid välja bort sökande på grund av religion.", "Diskrimineringsombudsmannen dömer ut fängelsestraff.")),
        TopicData("RULE_OF_LAW", "Rättsstat och domstolar", "Rättssäkerhet och oberoende domstolar.", ("Sveriges Domstolar", "Så fungerar domstolarna", "https://www.domstol.se/om-sveriges-domstolar/sa-fungerar-domstolarna/"), (
            "Domstolar är självständiga när de avgör enskilda mål.", "Den som är misstänkt för brott ska betraktas som oskyldig tills skuld har fastställts.", "Allmänna domstolar handlägger bland annat brottmål och tvistemål.", "Förvaltningsdomstolar prövar många tvister mellan enskilda och myndigheter.", "Domstolsavgöranden kan ofta överklagas enligt reglerna för den aktuella domstolen."),
            ("Regeringen bestämmer domslutet i varje brottmål.", "Polisen avgör slutligt om en person är skyldig.", "Ett myndighetsbeslut kan aldrig prövas av domstol.")),
        TopicData("RESPONSIBILITIES", "Ansvar i vardagen", "Lagar, skatt och ansvar för barn.", ("Informationsverige.se", "Att leva i Sverige", "https://www.informationsverige.se/sv/om-sverige/"), (
            "Alla som vistas i Sverige ska följa svensk lag.", "Skatter finansierar gemensamma offentliga tjänster.", "Vårdnadshavare ansvarar för att barn får omsorg och möjlighet till utbildning.", "Den som arbetar har både rättigheter och skyldigheter i arbetslivet.", "Myndighetsbeslut ska följas, men många beslut kan begäras omprövade eller överklagas."),
            ("Privatpersoner kan själva välja vilka lagar som gäller dem.", "Offentliga tjänster finansieras bara genom frivilliga gåvor.", "Ett överklagande betyder automatiskt att beslutet upphör för alltid.")),
    )),
    ("SOCIETY", "Det svenska samhället och offentlig service", "Myndigheter och tjänster som människor möter i vardagen.", (
        TopicData("POPULATION_TAX", "Folkbokföring och skatt", "Skatteverkets uppgifter och gemensam finansiering.", ("Skatteverket", "Folkbokföring i Sverige", "https://www.skatteverket.se/privat/folkbokforing.html"), (
            "Skatteverket ansvarar för folkbokföringen i Sverige.", "Folkbokföringen innehåller uppgifter om var personer är bosatta.", "Den som flyttar ska anmäla rätt adress enligt folkbokföringsreglerna.", "Skatteverket hanterar bland annat inkomstdeklarationer och skatter.", "Skatteintäkter används för att finansiera offentlig verksamhet."),
            ("Polismyndigheten ansvarar för all folkbokföring.", "Folkbokföringen registrerar bara företagsadresser.", "Inkomstdeklarationer lämnas till kommunens bibliotek.")),
        TopicData("HEALTHCARE", "Hälso- och sjukvård", "Hur vården huvudsakligen organiseras.", ("1177 Vårdguiden", "Så fungerar vården i Sverige", "https://www.1177.se/sa-fungerar-varden/"), (
            "Regionerna har huvudansvar för hälso- och sjukvården.", "1177 Vårdguiden ger information och vägledning om vård.", "Vårdcentralen är ofta den första kontakten för besvär som inte är akuta.", "Vid ett allvarligt akut tillstånd används larmnumret 112.", "Patienter har rätt att få begriplig information om sin vård."),
            ("Riksdagen driver varje vårdcentral direkt.", "112 används främst för att boka planerade tandläkarbesök.", "Patienter får aldrig ställa frågor om sin behandling.")),
        TopicData("EDUCATION", "Utbildning", "Skolans ansvar och utbildningsvägar.", ("Skolverket", "Det svenska skolsystemet", "https://www.skolverket.se/andra-sprak-other-languages/english-engelska"), (
            "Kommunerna har ett viktigt ansvar för den kommunala skolan.", "Grundskolan är obligatorisk för barn som omfattas av skolplikten.", "Utbildningen ska vara likvärdig oavsett var i landet den anordnas.", "Vuxenutbildning kan ge vuxna möjlighet att komplettera tidigare utbildning.", "Skolverket stödjer och följer upp det svenska skolväsendet."),
            ("Grundskolan är frivillig för alla skolpliktiga barn.", "Polismyndigheten fastställer skolans läroplaner.", "Vuxna får aldrig komplettera en tidigare utbildning.")),
        TopicData("SOCIAL_INSURANCE", "Socialförsäkring och arbete", "Stöd, försäkring och arbetsmarknadsservice.", ("Försäkringskassan", "Socialförsäkringen", "https://www.forsakringskassan.se/privatperson"), (
            "Försäkringskassan administrerar flera delar av socialförsäkringen.", "Socialförsäkringen kan ge ekonomisk trygghet vid vissa livssituationer.", "Arbetsförmedlingen ger stöd till arbetssökande och arbetsgivare.", "Anställningsvillkor påverkas av lagar och kollektivavtal.", "Fackliga organisationer företräder arbetstagare i arbetslivet."),
            ("Försäkringskassan beslutar om alla domstolsdomar.", "Arbetsförmedlingen garanterar varje sökande ett visst arbete.", "Kollektivavtal är samma sak som en grundlag.")),
    )),
    ("HISTORY_CULTURE", "Svensk historia och samhällsutveckling", "Historiska förändringar och kulturell mångfald.", (
        TopicData("DEMOCRATISATION", "Demokratisering", "Utvecklingen mot allmän och lika rösträtt.", ("Sveriges riksdag", "Demokratins genombrott", "https://www.riksdagen.se/sv/sa-fungerar-riksdagen/demokrati/"), (
            "Sveriges demokratisering skedde stegvis under lång tid.", "Folkrörelser bidrog till politiskt deltagande och samhällsförändring.", "Allmän och lika rösträtt innebar en avgörande utvidgning av demokratin.", "Kvinnor och män fick rösta på lika villkor till riksdagen efter demokratiska reformer.", "Den parlamentariska demokratin bygger på att regeringen måste tolereras av riksdagen."),
            ("Sverige införde full demokrati genom ett enda kommunalt beslut.", "Folkrörelser var förbjudna under hela demokratiseringen.", "Parlamentarism betyder att domstolar utser regeringen.")),
        TopicData("INDUSTRIALISATION", "Industrialisering och välfärd", "Förändringar i arbete, städer och social trygghet.", ("Historiska museet", "Sveriges historia", "https://historiska.se/upptack-historien/"), (
            "Industrialiseringen förändrade arbete, produktion och bosättning i Sverige.", "Många människor flyttade från landsbygden till växande städer.", "Folkrörelser organiserade bland annat arbetare, nykterhetsvänner och religiösa grupper.", "Välfärdsstaten byggdes ut genom politiska reformer under 1900-talet.", "Utbyggd utbildning och social trygghet förändrade människors levnadsvillkor."),
            ("Industrialiseringen gjorde att alla städer försvann.", "Välfärdssystemen skapades av privata sportklubbar utan lagstiftning.", "Folkrörelser handlade enbart om militär utbildning.")),
        TopicData("MIGRATION", "Migration och det moderna Sverige", "Migrationens olika former och betydelse.", ("Migrationsverket", "Om migration", "https://www.migrationsverket.se/Om-Migrationsverket/Migration-till-Sverige.html"), (
            "Människor har både utvandrat från och invandrat till Sverige under olika perioder.", "Arbetskraftsinvandring har bidragit till Sveriges samhällsutveckling.", "Skyddsskäl, arbete, studier och familjeanknytning är olika orsaker till migration.", "Migrationsverket prövar många ärenden som gäller uppehållstillstånd och medborgarskap.", "Integration berör deltagande i arbetsliv, utbildning och samhällsliv."),
            ("Migration till Sverige har bara förekommit under ett enda år.", "Kommunbibliotek beslutar om svenskt medborgarskap.", "Integration betyder att människor inte får delta i samhällslivet.")),
        TopicData("MINORITIES", "Nationella minoriteter och samer", "Språk, kultur och urfolksrättigheter.", ("Institutet för språk och folkminnen", "Nationella minoriteter", "https://www.isof.se/nationella-minoritetssprak"), (
            "Sverige erkänner fem nationella minoriteter.", "De nationella minoritetsspråken är finska, jiddisch, meänkieli, romani chib och samiska.", "Samerna är ett urfolk i Sverige.", "Sametinget är både en statlig myndighet och ett folkvalt samiskt parlament.", "Minoritetspolitiken ska skydda språk och kultur samt stärka minoriteternas inflytande."),
            ("Sverige saknar erkända nationella minoriteter.", "Sametinget är en kommunal idrottsförening.", "Nationella minoritetsspråk får inte användas i Sverige.")),
    )),
    ("GEOGRAPHY_WORLD", "Geografi, miljö och Sverige i världen", "Sveriges geografi, miljöansvar och internationella samarbete.", (
        TopicData("GEOGRAPHY", "Sveriges geografi", "Landsdelar, natur och befolkning.", ("Lantmäteriet", "Sveriges geografi", "https://www.lantmateriet.se/sv/kartor/"), (
            "Sverige ligger i norra Europa på den skandinaviska halvön.", "Sverige gränsar till Norge och Finland.", "Östersjön ligger öster om stora delar av Sverige.", "Götaland, Svealand och Norrland är Sveriges tre landsdelar.", "Sveriges befolkning är ojämnt fördelad mellan tätbefolkade och glest befolkade områden."),
            ("Sverige ligger på den iberiska halvön.", "Sverige har landgräns mot Spanien.", "Norrland är en självständig stat utanför Sverige.")),
        TopicData("ENVIRONMENT", "Klimat och miljöansvar", "Hållbar användning av natur och resurser.", ("Naturvårdsverket", "Miljöarbete i Sverige", "https://www.naturvardsverket.se/"), (
            "Sverige har tydliga årstidsvariationer, men klimatet varierar mellan olika delar av landet.", "Allemansrätten ger möjlighet att vistas i naturen tillsammans med ansvar att inte störa eller förstöra.", "Avfall ska sorteras enligt lokala regler och producentansvar.", "Kommuner har viktiga uppgifter inom avfallshantering och miljöskydd.", "Miljöproblem kräver insatser från individer, företag och offentliga aktörer."),
            ("Allemansrätten ger rätt att skada mark och lämna skräp.", "Samma klimat råder alltid i hela Sverige.", "Miljöskydd är förbjudet för kommuner.")),
        TopicData("EU", "Sverige och Europeiska unionen", "Medlemskap och gemensamt beslutsfattande.", ("Europeiska unionen", "Sverige i EU", "https://european-union.europa.eu/principles-countries-history/country-profiles/sweden_sv"), (
            "Sverige är medlem i Europeiska unionen.", "EU-länder samarbetar inom många politiska och ekonomiska områden.", "Svenska medborgare väljer ledamöter till Europaparlamentet.", "EU-rätten påverkar svensk lagstiftning inom områden där EU har befogenhet.", "Sverige deltar i EU:s institutioner samtidigt som riksdag och regering har nationella uppgifter."),
            ("Sverige står helt utanför Europeiska unionen.", "Europaparlamentet utses av svenska kommunbibliotek.", "EU-medlemskap innebär att Sveriges riksdag har avskaffats.")),
        TopicData("INTERNATIONAL", "Nordiskt och internationellt samarbete", "Samarbete med andra länder och organisationer.", ("Regeringskansliet", "Sverige i världen", "https://www.regeringen.se/sveriges-regering/utrikesdepartementet/"), (
            "Sverige samarbetar med de andra nordiska länderna.", "Nordiska rådet är ett forum för parlamentariskt samarbete i Norden.", "Sverige är medlem i Förenta nationerna.", "Internationellt samarbete kan handla om fred, säkerhet, handel, miljö och mänskliga rättigheter.", "Utrikespolitiken hanteras av regeringen under riksdagens demokratiska kontroll."),
            ("Sverige får inte samarbeta med sina nordiska grannländer.", "Förenta nationerna är en svensk kommun.", "Utrikespolitik beslutas enbart av privata företag.")),
    )),
)


def stable_id(kind: str, code: str) -> str:
    return str(uuid.uuid5(NAMESPACE, f"{kind}:{code}"))


def sql(value: object) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def validate_dataset() -> None:
    topic_codes: set[str] = set()
    statements: set[str] = set()
    for _, _, _, topics in SUBJECTS:
        if len(topics) != 4:
            raise ValueError("Each subject must contain four balanced topics")
        for topic in topics:
            if topic.code in topic_codes:
                raise ValueError(f"Duplicate topic code: {topic.code}")
            topic_codes.add(topic.code)
            if len(topic.facts) != 5 or len(topic.misconceptions) != 3:
                raise ValueError(f"Topic {topic.code} must contain five facts and three distractors")
            for statement in topic.facts:
                if statement in statements or not statement.endswith("."):
                    raise ValueError(f"Duplicate or malformed fact: {statement}")
                statements.add(statement)
    if len(SUBJECTS) != 5 or len(topic_codes) != 20 or len(statements) != 100:
        raise ValueError("Demo dataset cardinality changed unexpectedly")


def build_dataset() -> dict[str, object]:
    validate_dataset()
    subjects = []
    questions = []
    facts = []
    sources = []
    objectives = []
    for subject_index, (subject_code, subject_name, subject_description, topics) in enumerate(SUBJECTS):
        subject_id = stable_id("subject", subject_code)
        subject = {"id": subject_id, "code": subject_code, "name": subject_name, "description": subject_description, "sortOrder": subject_index, "topics": []}
        for topic_index, topic in enumerate(topics):
            topic_id = stable_id("topic", topic.code)
            source_id = stable_id("source", topic.code)
            publisher, source_title, source_url = topic.source
            sources.append({"id": source_id, "publisher": publisher, "title": source_title, "url": source_url})
            topic_record = {"id": topic_id, "code": topic.code, "name": topic.name, "description": topic.description, "sortOrder": topic_index, "questions": []}
            for objective_index in range(2):
                objective_code = f"{topic.code}_{objective_index + 1}"
                objectives.append({"id": stable_id("objective", objective_code), "topicId": topic_id, "code": objective_code, "title": ("Förklara centrala principer inom " if objective_index == 0 else "Tillämpa kunskap om ") + topic.name.lower()})
            for fact_index, statement in enumerate(topic.facts):
                global_index = len(facts)
                editorial_states = {
                    96: ("UNREVIEWED", "DRAFT"),
                    97: ("UNDER_REVIEW", "DRAFT"),
                    98: ("REQUIRES_UPDATE", "DRAFT"),
                    99: ("APPROVED", "RETIRED"),
                }
                review_status, lifecycle_status = editorial_states.get(global_index, ("APPROVED", "ACTIVE"))
                code = f"{topic.code}_{fact_index + 1:02d}"
                fact_id = stable_id("fact", code)
                fact_version_id = stable_id("fact-version", code)
                objective_id = stable_id("objective", f"{topic.code}_{1 + fact_index % 2}")
                facts.append({"id": fact_id, "versionId": fact_version_id, "code": code, "objectiveId": objective_id, "sourceId": source_id, "statement": statement, "reviewStatus": review_status, "status": lifecycle_status})
                question_id = stable_id("question", code)
                question_version_id = stable_id("question-version", code)
                if fact_index < 3:
                    qtype = "SINGLE_CHOICE"
                    prompt = f"Vilket påstående om {topic.name.lower()} är korrekt?"
                    option_texts = [statement, topic.misconceptions[fact_index], topic.misconceptions[(fact_index + 1) % 3]]
                    correct = {0}
                elif fact_index == 3:
                    qtype = "MULTIPLE_CHOICE"
                    prompt = f"Vilka påståenden om {topic.name.lower()} är korrekta? Välj alla korrekta svar."
                    option_texts = [topic.facts[3], topic.facts[4], topic.misconceptions[0], topic.misconceptions[1]]
                    correct = {0, 1}
                else:
                    qtype = "TRUE_FALSE"
                    prompt = statement
                    option_texts = ["Sant", "Falskt"]
                    correct = {0}
                options = [{"id": stable_id("option", f"{code}:{i}"), "text": text, "correct": i in correct, "feedback": "Korrekt enligt den angivna demonstrationskällan." if i in correct else "Detta påstående stämmer inte med den länkade kunskapsfaktan.", "sortOrder": i} for i, text in enumerate(option_texts)]
                question = {"id": question_id, "versionId": question_version_id, "factId": fact_id, "factVersionId": fact_version_id, "objectiveId": objective_id, "code": f"DEMO_{code}", "type": qtype, "prompt": prompt, "explanation": statement if qtype != "MULTIPLE_CHOICE" else f"Båda dessa påståenden är korrekta: {topic.facts[3]} {topic.facts[4]}", "difficulty": ("EASY", "MEDIUM", "MEDIUM", "HARD", "EASY")[fact_index], "reviewStatus": review_status, "status": lifecycle_status, "options": options}
                questions.append(question)
                topic_record["questions"].append(question)
            subject["topics"].append(topic_record)
        subjects.append(subject)
    return {"subjects": subjects, "sources": sources, "objectives": objectives, "facts": facts, "questions": questions}


def snapshot(dataset: dict[str, object]) -> tuple[dict[str, object], str]:
    snapshot_subjects = []
    for subject in dataset["subjects"]:
        snapshot_topics = []
        for topic in subject["topics"]:
            snapshot_questions = []
            for question in topic["questions"]:
                if question["reviewStatus"] != "APPROVED" or question["status"] != "ACTIVE":
                    continue
                snapshot_questions.append({"id": question["id"], "versionId": question["versionId"], "knowledgeFactId": question["factId"], "questionType": question["type"], "prompt": question["prompt"], "explanation": question["explanation"], "language": "sv", "difficulty": question["difficulty"], "active": True, "correctOptionIds": [o["id"] for o in question["options"] if o["correct"]], "answerOptions": question["options"]})
            snapshot_topics.append({"id": topic["id"], "name": topic["name"], "description": topic["description"], "sortOrder": topic["sortOrder"], "questions": snapshot_questions})
        snapshot_subjects.append({"id": subject["id"], "name": subject["name"], "sortOrder": subject["sortOrder"], "topics": snapshot_topics})
    material = {"schemaVersion": "1.1", "externalReleaseId": CURRENT_RELEASE_ID, "examId": "swedish-citizenship", "examVersionId": "2026.2-demo", "releaseVersion": "2026.2-demo", "releaseStatus": "PUBLISHED", "publishedAt": FIXED_AT, "subjects": snapshot_subjects}
    canonical = json.dumps(material, ensure_ascii=False, separators=(",", ":"))
    checksum = hashlib.sha256(canonical.encode()).hexdigest()
    result = dict(material)
    result["checksum"] = checksum
    # Match the Java record property order: checksum precedes subjects.
    result = {key: result[key] for key in ("schemaVersion", "externalReleaseId", "examId", "examVersionId", "releaseVersion", "releaseStatus", "publishedAt", "checksum", "subjects")}
    return result, checksum


def content_reset_sql() -> str:
    return """BEGIN;
UPDATE content_release SET previous_release_id=NULL;
DELETE FROM release_activation_history; DELETE FROM release_delivery_attempt; DELETE FROM published_release_snapshot;
DELETE FROM release_validation_run; DELETE FROM content_release_item; DELETE FROM content_release;
DELETE FROM review_comment; DELETE FROM review_record; DELETE FROM review_item;
UPDATE question SET current_version_id=NULL; DELETE FROM question_tag; DELETE FROM question_knowledge_fact;
DELETE FROM question_option; DELETE FROM question_version; DELETE FROM question;
UPDATE knowledge_fact SET current_version_id=NULL; DELETE FROM knowledge_fact_source; DELETE FROM knowledge_fact_version; DELETE FROM knowledge_fact;
DELETE FROM learning_objective; UPDATE source_reference SET replacement_source_id=NULL; DELETE FROM source_reference;
DELETE FROM topic; DELETE FROM subject; DELETE FROM exam_version; DELETE FROM exam;
TRUNCATE TABLE audit_event;
COMMIT;"""


def learning_reset_sql() -> str:
    return """BEGIN;
DELETE FROM mock_exam_subject_result; DELETE FROM mock_exam_topic_result; DELETE FROM mock_exam_response_selection;
DELETE FROM mock_exam_response; DELETE FROM mock_exam_question; DELETE FROM mock_exam_attempt; DELETE FROM mock_exam_topic_allocation; DELETE FROM mock_exam_blueprint;
DELETE FROM practice_response_selection; DELETE FROM practice_response; DELETE FROM practice_session_question; DELETE FROM practice_session;
DELETE FROM bookmark; DELETE FROM topic_progress; DELETE FROM learner_profile; DELETE FROM imported_release_activation_history;
DELETE FROM imported_answer_option; DELETE FROM imported_question; DELETE FROM imported_topic; DELETE FROM imported_subject; DELETE FROM imported_content_release;
COMMIT;"""


def content_seed_sql(dataset: dict[str, object], snap: dict[str, object], checksum: str) -> str:
    lines = ["BEGIN;", "SELECT set_config('app.actor_id','demo-seed',true);", "SELECT set_config('app.actor_name','Deterministic demo seed',true);", "SELECT set_config('app.actor_roles','SYSTEM',true);"]
    lines.append(f"INSERT INTO exam VALUES ({sql(EXAM_ID)},'SWEDISH_CITIZENSHIP','Kunskapsprov för svenskt medborgarskap – övningsmaterial','SE','ACTIVE',{sql(FIXED_AT)},{sql(FIXED_AT)},1);")
    lines.append(f"INSERT INTO exam_version VALUES ({sql(HISTORICAL_VERSION_ID)},{sql(EXAM_ID)},'2026.1-demo','2026.1 Demo – historiskt övningsmaterial','ARCHIVED','2026-01-15','2026-05-31',{sql(FIXED_AT)},{sql(FIXED_AT)},1);")
    lines.append(f"INSERT INTO exam_version VALUES ({sql(CURRENT_VERSION_ID)},{sql(EXAM_ID)},'2026.2-demo','2026.2 Demo – aktuellt övningsmaterial','ACTIVE','2026-06-01',NULL,{sql(FIXED_AT)},{sql(FIXED_AT)},1);")
    for source in dataset["sources"]:
        lines.append("INSERT INTO source_reference(id,publisher,title,url,source_type,accessed_at,copyright_notes,internal_notes,review_status,status,created_at,updated_at,version) VALUES(" + ",".join(map(sql, (source["id"], source["publisher"], source["title"], source["url"], "GOVERNMENT_WEBPAGE", FIXED_DATE, "Public institutional landing page; verify before production editorial use.", "Demonstration metadata, fixed review date.", "REVIEWED", "ACTIVE", FIXED_AT, FIXED_AT, 1))) + ");")
    for subject in dataset["subjects"]:
        lines.append("INSERT INTO subject VALUES(" + ",".join(map(sql, (subject["id"], CURRENT_VERSION_ID, subject["code"], subject["name"], subject["description"], subject["sortOrder"], "ACTIVE", FIXED_AT, FIXED_AT, 1))) + ");")
        for topic in subject["topics"]:
            lines.append("INSERT INTO topic VALUES(" + ",".join(map(sql, (topic["id"], subject["id"], topic["code"], topic["name"], topic["description"], topic["sortOrder"], "ACTIVE", FIXED_AT, FIXED_AT, 1))) + ");")
    for objective in dataset["objectives"]:
        lines.append("INSERT INTO learning_objective VALUES(" + ",".join(map(sql, (objective["id"], objective["topicId"], objective["code"], objective["title"], "Demonstrationsmål för redaktionell och pedagogisk granskning.", "ACTIVE", FIXED_AT, FIXED_AT, 1))) + ");")
    for fact in dataset["facts"]:
        reviewer = "demo-content-reviewer" if fact["reviewStatus"] in ("APPROVED", "REQUIRES_UPDATE") else None
        lines.append("INSERT INTO knowledge_fact(id,learning_objective_id,current_version_id,canonical_statement,review_status,status,valid_from,created_at,updated_at,version) VALUES(" + ",".join(map(sql, (fact["id"], fact["objectiveId"], None, fact["statement"], fact["reviewStatus"], fact["status"], "2026-06-01", FIXED_AT, FIXED_AT, 1))) + ");")
        lines.append("INSERT INTO knowledge_fact_version(id,knowledge_fact_id,version_number,canonical_statement,review_status,valid_from,author_id,reviewer_id,review_note,created_at,updated_at) VALUES(" + ",".join(map(sql, (fact["versionId"], fact["id"], 1, fact["statement"], fact["reviewStatus"], "2026-06-01", "demo-content-author", reviewer, "Demonstration editorial review history.", FIXED_AT, FIXED_AT))) + ");")
        lines.append(f"UPDATE knowledge_fact SET current_version_id={sql(fact['versionId'])} WHERE id={sql(fact['id'])};")
        lines.append(f"INSERT INTO knowledge_fact_source VALUES({sql(fact['versionId'])},{sql(fact['sourceId'])});")
        if fact["reviewStatus"] != "UNREVIEWED":
            review_id = stable_id("fact-review", fact["code"])
            lines.append("INSERT INTO review_item(id,content_type,content_id,content_version_id,author_id,review_status,lifecycle_status,priority,assigned_reviewer_id,assigned_at,submitted_at,updated_at,version) VALUES(" + ",".join(map(sql, (review_id, "KNOWLEDGE_FACT", fact["id"], fact["versionId"], "demo-content-author", fact["reviewStatus"], fact["status"], "NORMAL", "demo-content-reviewer", FIXED_AT, FIXED_AT, FIXED_AT, 1))) + ");")
            action = "APPROVE" if fact["reviewStatus"] == "APPROVED" else ("REQUIRES_UPDATE" if fact["reviewStatus"] == "REQUIRES_UPDATE" else "SUBMIT")
            lines.append("INSERT INTO review_record(id,review_item_id,content_version_id,action,from_status,to_status,actor_id,actor_roles,comment,reason_code,metadata,created_at) VALUES(" + ",".join(map(sql, (stable_id("fact-review-record", fact["code"]), review_id, fact["versionId"], action, "UNDER_REVIEW" if action != "SUBMIT" else "UNREVIEWED", fact["reviewStatus"], "demo-content-reviewer" if action != "SUBMIT" else "demo-content-author", "CONTENT_REVIEWER" if action != "SUBMIT" else "CONTENT_AUTHOR", "Demonstration workflow event.", "DEMO_REVIEW", '{}'))) + "::jsonb," + sql(FIXED_AT) + ");")
    for index, question in enumerate(dataset["questions"]):
        reviewer = "demo-content-reviewer" if question["reviewStatus"] in ("APPROVED", "REQUIRES_UPDATE") else None
        lines.append("INSERT INTO question(id,learning_objective_id,current_version_id,code,question_type,question_text,difficulty,review_status,status,created_at,updated_at,version) VALUES(" + ",".join(map(sql, (question["id"], question["objectiveId"], None, question["code"], question["type"], question["prompt"], question["difficulty"], question["reviewStatus"], question["status"], FIXED_AT, FIXED_AT, 1))) + ");")
        lines.append("INSERT INTO question_version VALUES(" + ",".join(map(sql, (question["versionId"], question["id"], 1, question["objectiveId"], question["type"], question["prompt"], question["difficulty"], question["explanation"], question["reviewStatus"], "demo-content-author", reviewer, "Demonstration editorial review history.", FIXED_AT, FIXED_AT))) + ");")
        lines.append(f"UPDATE question SET current_version_id={sql(question['versionId'])} WHERE id={sql(question['id'])};")
        lines.append(f"INSERT INTO question_knowledge_fact VALUES({sql(question['versionId'])},{sql(question['factVersionId'])});")
        for option in question["options"]:
            lines.append("INSERT INTO question_option VALUES(" + ",".join(map(sql, (option["id"], question["versionId"], option["sortOrder"], option["text"], option["correct"], option["feedback"]))) + ");")
        if question["reviewStatus"] != "UNREVIEWED":
            review_id = stable_id("review", question["code"])
            lines.append("INSERT INTO review_item(id,content_type,content_id,content_version_id,author_id,review_status,lifecycle_status,priority,assigned_reviewer_id,assigned_at,submitted_at,updated_at,version) VALUES(" + ",".join(map(sql, (review_id, "QUESTION", question["id"], question["versionId"], "demo-content-author", question["reviewStatus"], question["status"], "NORMAL", "demo-content-reviewer", FIXED_AT, FIXED_AT, FIXED_AT, 1))) + ");")
            action = "APPROVE" if question["reviewStatus"] == "APPROVED" else ("REQUIRES_UPDATE" if question["reviewStatus"] == "REQUIRES_UPDATE" else "SUBMIT")
            lines.append("INSERT INTO review_record(id,review_item_id,content_version_id,action,from_status,to_status,actor_id,actor_roles,comment,reason_code,metadata,created_at) VALUES(" + ",".join(map(sql, (stable_id("review-record", question["code"]), review_id, question["versionId"], action, "UNDER_REVIEW" if action != "SUBMIT" else "UNREVIEWED", question["reviewStatus"], "demo-content-reviewer" if action != "SUBMIT" else "demo-content-author", "CONTENT_REVIEWER" if action != "SUBMIT" else "CONTENT_AUTHOR", "Demonstration workflow event.", "DEMO_REVIEW", '{}'))) + "::jsonb," + sql(FIXED_AT) + ");")
    snapshot_json = json.dumps(snap, ensure_ascii=False, separators=(",", ":"))
    for release_id, number, name, status, previous in ((HISTORICAL_RELEASE_ID, "2026.1-demo", "Historisk demonstrationsrelease", "RETIRED", None), (CURRENT_RELEASE_ID, "2026.2-demo", "Aktuell demonstrationsrelease", "PUBLISHED", HISTORICAL_RELEASE_ID), (DRAFT_RELEASE_ID, "2026.3-draft", "Nästa redaktionella demonstrationsrelease", "DRAFT", CURRENT_RELEASE_ID)):
        published = FIXED_AT if status in ("PUBLISHED", "RETIRED") else None
        release_count = 40 if status == "RETIRED" else (96 if status == "PUBLISHED" else 0)
        lines.append("INSERT INTO content_release(id,exam_id,exam_version_id,release_number,display_name,description,status,created_by,created_at,updated_at,published_at,published_by,retired_at,checksum,snapshot_schema_version,knowledge_fact_count,question_count,last_validated_version,previous_release_id,version) VALUES(" + ",".join(map(sql, (release_id, EXAM_ID, HISTORICAL_VERSION_ID if status == "RETIRED" else CURRENT_VERSION_ID, number, name, "Demonstration preparation content; not an official examination.", status, "demo-content-publisher", FIXED_AT, FIXED_AT, published, "demo-content-publisher" if published else None, FIXED_AT if status == "RETIRED" else None, checksum if status in ("PUBLISHED", "RETIRED") else None, "1.1", release_count, release_count, 1 if status != "DRAFT" else None, previous, 1))) + ");")
    released_questions = [question for question in dataset["questions"] if question["reviewStatus"] == "APPROVED" and question["status"] == "ACTIVE"]
    for index, question in enumerate(released_questions):
        for content_type, content_id, version_id, code, order, automatic in (("QUESTION", question["id"], question["versionId"], question["code"], index, False), ("KNOWLEDGE_FACT", question["factId"], question["factVersionId"], None, index, True)):
            lines.append("INSERT INTO content_release_item VALUES(" + ",".join(map(sql, (stable_id("release-item", f"{CURRENT_RELEASE_ID}:{content_type}:{version_id}"), CURRENT_RELEASE_ID, content_type, content_id, version_id, code, order, automatic, FIXED_AT))) + ");")
    lines.append("INSERT INTO published_release_snapshot VALUES(" + ",".join(map(sql, (CURRENT_RELEASE_ID, "1.1", checksum, snapshot_json))) + f"::jsonb,{sql(FIXED_AT)},{len(snapshot_json.encode())});")
    lines.extend(["COMMIT;"])
    return "\n".join(lines)


def assert_safe(args: argparse.Namespace, environ: dict[str, str] | None = None) -> None:
    env = os.environ if environ is None else environ
    profile = env.get("DEMO_DATA_ENVIRONMENT", "").strip().lower()
    allowed = {"local", "development", "dev", "test", "seed"}
    if env.get("ALLOW_DESTRUCTIVE_DEV_RESET") != "true":
        raise RuntimeError("ALLOW_DESTRUCTIVE_DEV_RESET=true is required")
    if not args.confirm_reset:
        raise RuntimeError("--confirm-reset is required")
    if profile not in allowed or profile in {"prod", "production"}:
        raise RuntimeError("DEMO_DATA_ENVIRONMENT must explicitly identify a non-production environment")
    protected = {"prod", "production", "amazonaws.com", "azure.com", "cloudsql", "rds"}
    for host in (args.content_db_host, args.learning_db_host):
        normalized = host.lower()
        if any(token in normalized for token in protected):
            raise RuntimeError(f"Refusing protected database host: {host}")
        if normalized not in {"localhost", "127.0.0.1", "content-database", "learning-database"}:
            raise RuntimeError(f"Database host is not an approved local/Compose host: {host}")


def run_psql(service: str, user: str, database: str, script: str) -> None:
    subprocess.run(["docker", "compose", "exec", "-T", service, "psql", "-v", "ON_ERROR_STOP=1", "-U", user, "-d", database], input=script, text=True, check=True)


def post(url: str, headers: dict[str, str] | None = None) -> dict[str, object]:
    request = urllib.request.Request(url, method="POST", headers=headers or {})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"POST {url} failed: {error.code} {error.read().decode()}") from error


def seed_blueprint(dataset: dict[str, object]) -> str:
    allocations = []
    for index, subject in enumerate(dataset["subjects"]):
        topic = subject["topics"][0]
        allocations.append(f"({sql(stable_id('allocation', topic['code']))},{sql(BLUEPRINT_ID)},{sql(topic['id'])},4)")
    learner = "INSERT INTO learner_profile(id,external_identity_id,display_name,interface_language,explanation_language,created_at,updated_at) VALUES(" + ",".join(map(sql, (DEMO_LEARNER_ID, "dev-learner-001", "Demo learner", "sv", "sv", FIXED_AT, FIXED_AT))) + ");"
    return "BEGIN; " + learner + " INSERT INTO mock_exam_blueprint(id,exam_id,name,total_questions,duration_minutes,passing_percentage,active,created_at,updated_at,description,randomize_questions,randomize_options,max_attempts_per_day,version) VALUES(" + ",".join(map(sql, (BLUEPRINT_ID, "swedish-citizenship", "Demonstrationsprov – inga officiella provregler", 20, 30, 70, True, FIXED_AT, FIXED_AT, "Lokalt demonstrationsprov för att testa hela flödet.", True, True, 10, 1))) + "); INSERT INTO mock_exam_topic_allocation(id,blueprint_id,external_topic_id,question_count) VALUES " + ",".join(allocations) + "; COMMIT;"


def summary() -> None:
    content_query = "SELECT json_build_object('exams',(SELECT count(*) FROM exam),'examVersions',(SELECT count(*) FROM exam_version),'subjects',(SELECT count(*) FROM subject),'topics',(SELECT count(*) FROM topic),'objectives',(SELECT count(*) FROM learning_objective),'sources',(SELECT count(*) FROM source_reference),'facts',(SELECT count(*) FROM knowledge_fact),'questions',(SELECT count(*) FROM question),'singleChoice',(SELECT count(*) FROM question WHERE question_type='SINGLE_CHOICE'),'trueFalse',(SELECT count(*) FROM question WHERE question_type='TRUE_FALSE'),'multipleChoice',(SELECT count(*) FROM question WHERE question_type='MULTIPLE_CHOICE'),'releases',(SELECT count(*) FROM content_release),'activeRelease',(SELECT release_number FROM content_release WHERE status='ACTIVE'))::text;"
    learning_query = "SELECT json_build_object('importedReleases',(SELECT count(*) FROM imported_content_release),'activeRelease',(SELECT external_release_id FROM imported_content_release WHERE status='ACTIVE'),'importedQuestions',(SELECT count(*) FROM imported_question),'practiceEligible',(SELECT count(*) FROM imported_question q JOIN imported_content_release r ON r.id=q.content_release_id WHERE r.status='ACTIVE' AND q.active),'mockBlueprints',(SELECT count(*) FROM mock_exam_blueprint WHERE active))::text;"
    subprocess.run(["docker", "compose", "exec", "-T", "content-database", "psql", "-At", "-U", "content", "-d", "content", "-c", content_query], check=True)
    subprocess.run(["docker", "compose", "exec", "-T", "learning-database", "psql", "-At", "-U", "learning", "-d", "learning", "-c", learning_query], check=True)


def validate_databases() -> None:
    content_checks = """DO $$ BEGIN
IF EXISTS(SELECT 1 FROM topic t LEFT JOIN learning_objective o ON o.topic_id=t.id WHERE o.id IS NULL) THEN RAISE EXCEPTION 'topic without objective'; END IF;
IF EXISTS(SELECT 1 FROM knowledge_fact f WHERE f.review_status='APPROVED' AND NOT EXISTS(SELECT 1 FROM knowledge_fact_source s WHERE s.knowledge_fact_version_id=f.current_version_id)) THEN RAISE EXCEPTION 'approved fact without source'; END IF;
IF EXISTS(SELECT 1 FROM question q WHERE q.review_status='APPROVED' AND NOT EXISTS(SELECT 1 FROM question_knowledge_fact f WHERE f.question_version_id=q.current_version_id)) THEN RAISE EXCEPTION 'approved question without fact'; END IF;
IF EXISTS(SELECT 1 FROM question q JOIN question_version v ON v.id=q.current_version_id LEFT JOIN question_option o ON o.question_version_id=v.id WHERE q.question_type IN ('SINGLE_CHOICE','TRUE_FALSE') GROUP BY q.id HAVING count(*) FILTER(WHERE o.correct)<>1) THEN RAISE EXCEPTION 'invalid single-answer question'; END IF;
IF EXISTS(SELECT 1 FROM question q JOIN question_version v ON v.id=q.current_version_id LEFT JOIN question_option o ON o.question_version_id=v.id WHERE q.question_type='MULTIPLE_CHOICE' GROUP BY q.id HAVING count(*) FILTER(WHERE o.correct)<1) THEN RAISE EXCEPTION 'multiple-choice question without correct option'; END IF;
IF (SELECT count(*) FROM content_release WHERE status='ACTIVE')<>1 THEN RAISE EXCEPTION 'expected exactly one active Content release'; END IF;
END $$;"""
    learning_checks = """DO $$ BEGIN
IF (SELECT count(*) FROM imported_content_release WHERE status='ACTIVE')<>1 THEN RAISE EXCEPTION 'expected exactly one active imported release'; END IF;
IF (SELECT count(DISTINCT q.question_type) FROM imported_question q JOIN imported_content_release r ON r.id=q.content_release_id WHERE r.status='ACTIVE' AND q.active)<>3 THEN RAISE EXCEPTION 'active release must contain all three question types'; END IF;
IF EXISTS(SELECT 1 FROM mock_exam_topic_allocation a WHERE a.question_count>(SELECT count(*) FROM imported_question q JOIN imported_topic t ON t.id=q.topic_id JOIN imported_content_release r ON r.id=q.content_release_id WHERE t.external_topic_id=a.external_topic_id AND r.status='ACTIVE' AND q.active)) THEN RAISE EXCEPTION 'mock allocation exceeds eligible questions'; END IF;
END $$;"""
    run_psql("content-database", "content", "content", content_checks)
    run_psql("learning-database", "learning", "learning", learning_checks)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("reset-content", "reset-learning", "reset-seed-all", "validate"))
    parser.add_argument("--confirm-reset", action="store_true")
    parser.add_argument("--content-db-host", default="content-database")
    parser.add_argument("--content-db-name", default="content")
    parser.add_argument("--learning-db-host", default="learning-database")
    parser.add_argument("--learning-db-name", default="learning")
    args = parser.parse_args()
    dataset = build_dataset()
    snap, checksum = snapshot(dataset)
    if args.command == "validate":
        print(json.dumps({"subjects": 5, "topics": 20, "objectives": 40, "sources": 20, "facts": 100, "questions": 100, "checksum": checksum}, indent=2))
        return
    assert_safe(args)
    print(f"DESTRUCTIVE DEVELOPMENT RESET\n  Content DB: {args.content_db_host}/{args.content_db_name}\n  Learning DB: {args.learning_db_host}/{args.learning_db_name}", flush=True)
    if args.command in ("reset-learning", "reset-seed-all"):
        run_psql("learning-database", "learning", args.learning_db_name, learning_reset_sql())
    if args.command in ("reset-content", "reset-seed-all"):
        run_psql("content-database", "content", args.content_db_name, content_reset_sql())
    if args.command == "reset-content":
        run_psql("content-database", "content", args.content_db_name, content_seed_sql(dataset, snap, checksum))
        return
    if args.command == "reset-learning":
        run_psql("learning-database", "learning", args.learning_db_name, seed_blueprint(dataset))
        return
    if args.command == "reset-seed-all":
        run_psql("content-database", "content", args.content_db_name, content_seed_sql(dataset, snap, checksum))
        headers = {"X-Admin-Identity": "demo-content-publisher", "X-Admin-Roles": "CONTENT_PUBLISHER"}
        post(f"http://localhost:8082/api/v1/admin/releases/{CURRENT_RELEASE_ID}/deliver", headers)
        post(f"http://localhost:8082/api/v1/admin/releases/{CURRENT_RELEASE_ID}/activate", headers)
        run_psql("learning-database", "learning", args.learning_db_name, seed_blueprint(dataset))
        validate_databases()
        summary()


if __name__ == "__main__":
    main()
