package no.nav.bidrag.reisekostnad.skedulering

import mu.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.bidrag.reisekostnad.integrasjon.brukernotifikasjon.Brukernotifikasjonkonsument
import no.nav.bidrag.reisekostnad.konfigurasjon.Applikasjonskonfig.FORESPØRSLER_SYNLIGE_I_ANTALL_DAGER_ETTER_SISTE_STATUSOPPDATERING
import no.nav.bidrag.reisekostnad.model.alleBarnHarFylt15år
import no.nav.bidrag.reisekostnad.model.hovedpartIdent
import no.nav.bidrag.reisekostnad.model.motpartIdent
import no.nav.bidrag.reisekostnad.tjeneste.Arkiveringstjeneste
import no.nav.bidrag.reisekostnad.tjeneste.Databasetjeneste
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

@Component
class Databehandler(
    private val arkiveringstjeneste: Arkiveringstjeneste,
    private val brukernotifikasjonkonsument: Brukernotifikasjonkonsument,
    private val databasetjeneste: Databasetjeneste
) {
    @Scheduled(cron = "\${kjøreplan.databehandling.arkivere}")
    @SchedulerLock(name = "forespørsel_til_arkiv", lockAtLeastFor = "PT5M", lockAtMostFor = "PT14M")
    fun arkiverForespørslerSomErKlareForInnsending() {
        val idForespørslerForInnsending = databasetjeneste.henteForespørslerSomErKlareForInnsending()
        log.info("Fant totalt ${idForespørslerForInnsending.size} forespørsler som vil bli forsøkt oversendt til dokumentarkivet")
        idForespørslerForInnsending.forEach {
            arkiveringstjeneste.arkivereForespørsel(it)
            log.info("Arkivering av forespørsel med id $it ble utført.")
        }
        log.info("Arkivering av alle de ${idForespørslerForInnsending.size} forespørslene er utført")
    }

    @Scheduled(cron = "\${kjøreplan.databehandling.fylt_15}")
    @SchedulerLock(name = "forespørsel_barn_fylt_nylig_15år", lockAtLeastFor = "PT5M", lockAtMostFor = "PT14M")
    fun behandleForespørslerSomInneholderBarnSomHarNyligFylt15År() {
        val forespørslerOver15År = databasetjeneste.hentForespørselSomInneholderBarnSomHarFylt15år()
        log.info("Fant totalt ${forespørslerOver15År.size} forespørsler som inneholder barn som har nylig fylt 15 år")
        forespørslerOver15År.forEach { originalForespørsel ->
            try {
                val nyForespørsel =
                    if (originalForespørsel.alleBarnHarFylt15år) {
                        databasetjeneste.oppdaterForespørselTilÅIkkeKreveSamtykke(originalForespørsel.id)
                    } else {
                        databasetjeneste.overførBarnSomHarFylt15årTilNyForespørsel(originalForespørsel.id)
                    }
                arkiveringstjeneste.arkivereForespørsel(nyForespørsel.id)
                log.info("Antall barn i forespørselen som nettopp har fylt 15 år: {}", nyForespørsel.barn.size)
                brukernotifikasjonkonsument.varsleOmAutomatiskInnsending(
                    nyForespørsel.hovedpartIdent,
                    nyForespørsel.motpartIdent,
                    nyForespørsel.barn.stream().findFirst().get().fødselsdato
                )
            } catch (e: Exception) {
                log.error(
                    "Det skjedde en feil ved behandling av forespørsel ${originalForespørsel.id} som inneholder barn som har nylig fylt 15 år. Rullet tilbake alle endringer",
                    e
                )
            }
        }
        log.info("Behandlet alle forespørsler ${forespørslerOver15År.size} som inneholder barn som har nylig fylt 15 år")
    }

    @Scheduled(cron = "\${kjøreplan.databehandling.deaktivere}")
    @SchedulerLock(name = "deaktivere", lockAtLeastFor = "PT5M", lockAtMostFor = "PT14M")
    fun deaktivereJournalførteOgUtgåtteForespørsler() {
        log.info("Deaktivere journalførte og utgåtte forepørsler, varsle foreldre om utløpt samtykkefrist, og slette relaterte samtykkeoppgaver")

        // Deaktivere journalførte forespørsler
        deaktivereJournalførteForespørsler()

        // Deaktivere forespørsler med utgått samtykkefrist, samt sende varsel til foreldre
        deaktivereForespørslerMedUtgåttSamtykkefrist()

        // Aktive samtykkeoppgaver skal ferdigstilles dersom relatert forespørsel er deaktivert
        ferdigstilleUtgåtteSamtykkeoppgaver()
    }

    @Scheduled(cron = "\${kjøreplan.databehandling.anonymisere}")
    @SchedulerLock(name = "anonymisere", lockAtLeastFor = "PT5M", lockAtMostFor = "PT14M")
    fun anonymisereBarnOgSletteForeldreSomIkkeErKnyttetTilAktiveForespørsler() {
        log.info(
            "Sletter personidenter som kun er tilknyttet forespørsler som har vært deaktive i minst {} dager",
            FORESPØRSLER_SYNLIGE_I_ANTALL_DAGER_ETTER_SISTE_STATUSOPPDATERING
        )

        var antallBarnSomBleAnonymisert = databasetjeneste.anonymisereBarnUtenTilknytningTilAktiveForespørsler()
        log.info("Anonymiserte totalt $antallBarnSomBleAnonymisert barn.")

        var antallForeldreSomBleSlettet = databasetjeneste.sletteForeldreUtenTilknytningTilAktiveForespørsler()
        log.info("Slettet totalt $antallForeldreSomBleSlettet foreldre.")
    }

    private fun ferdigstilleUtgåtteSamtykkeoppgaver() {
        var oppgaverSomSkalFerdigstilles = databasetjeneste.henteOppgaverSomSkalFerdigstilles()
        log.info("Fant ${oppgaverSomSkalFerdigstilles.size} aktive oppgaver som skal ferdigstilles.")
        var antallFerdigstilteOppgaver = 0

        oppgaverSomSkalFerdigstilles.forEach {
            var oppgaveBleFerdigstilt = brukernotifikasjonkonsument.ferdigstilleSamtykkeoppgave(it.eventId, it.forelder.personident)
            if (oppgaveBleFerdigstilt) antallFerdigstilteOppgaver++
        }

        log.info("$antallFerdigstilteOppgaver av de ${oppgaverSomSkalFerdigstilles.size} identifiserte oppgavene ble ferdigstilt.")
    }

    private fun deaktivereJournalførteForespørsler() {
        var journalførteAktiveForespørsler = databasetjeneste.henteIdTilAktiveForespørsler(
            LocalDateTime.now().minusDays(FORESPØRSLER_SYNLIGE_I_ANTALL_DAGER_ETTER_SISTE_STATUSOPPDATERING.toLong()),
            true
        )

        log.info("Fant ${journalførteAktiveForespørsler.size} journalførte forespørsler som skal deaktiveres.")

        journalførteAktiveForespørsler.forEach { id -> databasetjeneste.deaktivereForespørsel(id, null); }

        if (journalførteAktiveForespørsler.size > 0) log.info("Alle de ${journalførteAktiveForespørsler.size} journalførte forespørslene ble deaktivert.")
    }

    private fun deaktivereForespørslerMedUtgåttSamtykkefrist() {
        var iderTilAktiveForespørslerOpprettetForMinstXAntallDagerSiden = databasetjeneste.henteIdTilAktiveForespørsler(
            LocalDate.now().minusDays(FORESPØRSLER_SYNLIGE_I_ANTALL_DAGER_ETTER_SISTE_STATUSOPPDATERING.toLong()).atStartOfDay(),
            false
        )
        log.info("Antall forespørsler med utgått samtykkefrist som vil bli forsøkt deaktivert: ${iderTilAktiveForespørslerOpprettetForMinstXAntallDagerSiden.size}.")
        var varselSendt = 0
        iderTilAktiveForespørslerOpprettetForMinstXAntallDagerSiden.forEach { id ->
            var forespørsel = databasetjeneste.henteAktivForespørsel(id)

            var samtykketidspunkt = forespørsel.samtykket
            if (forespørsel.journalført == null || samtykketidspunkt == null || LocalDate.now()
                .minusDays(FORESPØRSLER_SYNLIGE_I_ANTALL_DAGER_ETTER_SISTE_STATUSOPPDATERING.toLong()).atStartOfDay().isAfter(samtykketidspunkt)
            ) {
                databasetjeneste.deaktivereForespørsel(id, null)
                try {
                    brukernotifikasjonkonsument.varsleForeldreOmManglendeSamtykke(
                        forespørsel.hovedpartIdent,
                        forespørsel.motpartIdent,
                        forespørsel.opprettet.toLocalDate()
                    )
                    varselSendt++
                } catch (e: Exception) {
                    e.printStackTrace()
                    log.error("En feil oppstod ved varsling om manglende samtykke av forespørsel {}", forespørsel.id)
                }
            }
            ferdiglogg(varselSendt, iderTilAktiveForespørslerOpprettetForMinstXAntallDagerSiden.size)
        }
    }

    private fun ferdiglogg(varselSendt: Int, antallVurderteForespørsler: Int) {
        var alleForeldreBleVarslet = varselSendt == antallVurderteForespørsler
        var loggStrengDeaktivert = "Alle de $antallVurderteForespørsler forespørslene med utgått samtykkefrist ble deaktivert."
        var loggstreng = if (alleForeldreBleVarslet) {
            "$loggStrengDeaktivert Samtlige foreldre ble varslet."
        } else {
            "$loggStrengDeaktivert Foreldrene ble varslet for $varselSendt av $antallVurderteForespørsler.size forespørsler"
        }

        if (antallVurderteForespørsler > 0) log.info(loggstreng)
    }
}
