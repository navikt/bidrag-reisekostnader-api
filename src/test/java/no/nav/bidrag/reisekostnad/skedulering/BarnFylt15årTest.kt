package no.nav.bidrag.reisekostnad.skedulering

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.date.shouldHaveSameYearAs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.bidrag.reisekostnad.model.hovedpartIdent
import no.nav.bidrag.reisekostnad.model.motpartIdent
import no.nav.bidrag.reisekostnad.verifiserDokumentArkivertForForespørsel
import no.nav.bidrag.reisekostnad.verifiserDokumentArkivertForForespørselAntallGanger
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.time.LocalDate
import java.time.LocalDateTime

@DisplayName("BarnFylt15årTest")
class BarnFylt15årTest : DatabehandlerTest() {

    @Test
    fun skalOppretteNyForespørselForBarnSomHarFylt15år() {
        // gitt
        var forespørselUtenBarnOver15år = opppretteForespørsel(true)
        forespørselUtenBarnOver15år.barn = mutableSetOf(testpersonBarn11, testpersonBarn12)

        var originalForespørsel = opppretteForespørsel(true)
        originalForespørsel.barn = mutableSetOf(testpersonBarn10, testpersonBarn15)

        transactionTemplate.executeWithoutResult {
            forespørselDao.save(originalForespørsel)
            forespørselDao.save(forespørselUtenBarnOver15år)
        }

        // hvis
        databehandler.behandleForespørslerSomInneholderBarnSomHarNyligFylt15År()

        // så
        val alleForespørsler = forespørselDao.findAll().toList()
        originalForespørsel = alleForespørsler.find { it.id == originalForespørsel.id }!!
        forespørselUtenBarnOver15år = alleForespørsler.find { it.id == forespørselUtenBarnOver15år.id }!!
        val forespørselMedBarnFylt15År = alleForespørsler.find { it.id != originalForespørsel.id && it.id != forespørselUtenBarnOver15år.id }

        assertSoftly {
            alleForespørsler.size shouldBe 3

            forespørselUtenBarnOver15år.barn.size shouldBe 2
            forespørselUtenBarnOver15år.samtykket shouldBe null

            forespørselMedBarnFylt15År shouldNotBe null
            forespørselMedBarnFylt15År!!.barn.size shouldBe 1
            forespørselMedBarnFylt15År.isKreverSamtykke shouldBe false
            forespørselMedBarnFylt15År.journalført shouldHaveSameDayAs LocalDateTime.now()
            forespørselMedBarnFylt15År.journalført shouldHaveSameYearAs LocalDateTime.now()
            forespørselMedBarnFylt15År.idJournalpost shouldBe "1232132132"

            originalForespørsel.barn.size shouldBe 1
            originalForespørsel.barn.first().fødselsdato shouldBe LocalDate.now().minusYears(10)

            verifiserDokumentArkivertForForespørsel(forespørselMedBarnFylt15År.id)
            verify(brukernotifikasjonkonsument, times(1)).varsleOmAutomatiskInnsending(
                originalForespørsel.hovedpartIdent,
                originalForespørsel.motpartIdent,
                testpersonBarn15.fødselsdato
            )
        }
    }

    @Test
    fun skalOppdatereForespørselHvisInneholderBareEnBarn() {
        // gitt
        var originalForespørsel = opppretteForespørsel(true)
        originalForespørsel.barn = mutableSetOf(testpersonBarn15)

        transactionTemplate.executeWithoutResult {
            forespørselDao.save(originalForespørsel)
        }

        // hvis
        databehandler.behandleForespørslerSomInneholderBarnSomHarNyligFylt15År()

        // så
        val alleForespørsler = forespørselDao.findAll().toList()
        originalForespørsel = alleForespørsler.find { it.id == originalForespørsel.id }!!

        assertSoftly {
            alleForespørsler.size shouldBe 1

            originalForespørsel.barn.size shouldBe 1
            originalForespørsel.samtykket shouldBe null

            originalForespørsel.isKreverSamtykke shouldBe false
            originalForespørsel.journalført shouldHaveSameDayAs LocalDateTime.now()
            originalForespørsel.journalført shouldHaveSameYearAs LocalDateTime.now()
            originalForespørsel.idJournalpost shouldBe "1232132132"

            verifiserDokumentArkivertForForespørsel(originalForespørsel.id)
            verify(brukernotifikasjonkonsument, times(1)).varsleOmAutomatiskInnsending(
                originalForespørsel.hovedpartIdent,
                originalForespørsel.motpartIdent,
                testpersonBarn15.fødselsdato
            )
        }
    }

    @Test
    fun skalOppdatereForespørselHvisInneholderFlereBarnSomHarFylt15år() {
        // gitt
        var originalForespørsel = opppretteForespørsel(true)
        originalForespørsel.barn = mutableSetOf(testpersonBarn15, testpersonBarn15_3)

        transactionTemplate.executeWithoutResult {
            forespørselDao.save(originalForespørsel)
        }

        // hvis
        databehandler.behandleForespørslerSomInneholderBarnSomHarNyligFylt15År()

        // så
        val alleForespørsler = forespørselDao.findAll().toList()
        originalForespørsel = alleForespørsler.find { it.id == originalForespørsel.id }!!

        assertSoftly {
            alleForespørsler.size shouldBe 1

            originalForespørsel.barn.size shouldBe 2
            originalForespørsel.samtykket shouldBe null

            originalForespørsel.isKreverSamtykke shouldBe false
            originalForespørsel.journalført shouldHaveSameDayAs LocalDateTime.now()
            originalForespørsel.journalført shouldHaveSameYearAs LocalDateTime.now()
            originalForespørsel.idJournalpost shouldBe "1232132132"

            verifiserDokumentArkivertForForespørsel(originalForespørsel.id)
            verify(brukernotifikasjonkonsument, times(1)).varsleOmAutomatiskInnsending(
                originalForespørsel.hovedpartIdent,
                originalForespørsel.motpartIdent,
                testpersonBarn15.fødselsdato
            )
        }
    }

    @Test
    fun skalRulleTilbakeEndringerVedFeil() {
        // gitt
        var forespørselMedFeil = opppretteForespørsel(true)
        forespørselMedFeil.barn = mutableSetOf(testpersonBarn11, testpersonBarn15_2)
        forespørselMedFeil.hovedpart = null

        var forespørselMedToBarn = opppretteForespørsel(true)
        forespørselMedToBarn.barn = mutableSetOf(testpersonBarn10, testpersonBarn15)

        var forespørselMedEttBarn = opppretteForespørsel(true)
        forespørselMedEttBarn.barn = mutableSetOf(testpersonBarn15_3)

        transactionTemplate.executeWithoutResult {
            forespørselDao.save(forespørselMedToBarn)
            forespørselDao.save(forespørselMedFeil)
            forespørselDao.save(forespørselMedEttBarn)
        }

        // hvis
        databehandler.behandleForespørslerSomInneholderBarnSomHarNyligFylt15År()

        // så
        val alleForespørsler = transactionTemplate.execute { forespørselDao.findAll().toList() }!!

        forespørselMedEttBarn = alleForespørsler.find { it.id == forespørselMedEttBarn.id }!!
        forespørselMedToBarn = alleForespørsler.find { it.id == forespørselMedToBarn.id }!!
        forespørselMedFeil = alleForespørsler.find { it.id == forespørselMedFeil.id }!!
        val forespørselMedBarnFylt15År = alleForespørsler.find { it.id != forespørselMedToBarn.id && it.id != forespørselMedFeil.id }

        assertSoftly {
            alleForespørsler.size shouldBe 4
            forespørselMedFeil.barn.size shouldBe 2
            forespørselMedToBarn.barn.size shouldBe 1
            forespørselMedEttBarn.barn.size shouldBe 1

            forespørselMedToBarn.barn.size shouldBe 1
            forespørselMedToBarn.barn.first().fødselsdato shouldBe LocalDate.now().minusYears(10)

            forespørselMedEttBarn.isKreverSamtykke shouldBe false
            forespørselMedEttBarn.journalført shouldHaveSameDayAs LocalDateTime.now()
            forespørselMedEttBarn.idJournalpost shouldBe "1232132132"

            verifiserDokumentArkivertForForespørsel(forespørselMedEttBarn.id)

            forespørselMedBarnFylt15År shouldNotBe null
            forespørselMedBarnFylt15År!!.barn.size shouldBe 1
            forespørselMedBarnFylt15År.isKreverSamtykke shouldBe false
            forespørselMedBarnFylt15År.journalført shouldHaveSameDayAs LocalDateTime.now()
            forespørselMedBarnFylt15År.journalført shouldHaveSameYearAs LocalDateTime.now()
            forespørselMedBarnFylt15År.idJournalpost shouldBe "1232132132"

            verifiserDokumentArkivertForForespørsel(forespørselMedBarnFylt15År.id)
            verifiserDokumentArkivertForForespørselAntallGanger(2)

            verify(brukernotifikasjonkonsument, times(0)).varsleOmAutomatiskInnsending(
                testpersonGråtass.personident,
                testpersonStreng.personident,
                testpersonBarn15_2.fødselsdato
            )
            verify(brukernotifikasjonkonsument, times(1)).varsleOmAutomatiskInnsending(
                testpersonGråtass.personident,
                testpersonStreng.personident,
                testpersonBarn15.fødselsdato
            )
            verify(brukernotifikasjonkonsument, times(1)).varsleOmAutomatiskInnsending(
                testpersonGråtass.personident,
                testpersonStreng.personident,
                testpersonBarn15_3.fødselsdato
            )
        }
    }
}
