package no.nav.bidrag.reisekostnad.skedulering

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import no.nav.bidrag.reisekostnad.verifiserDokumentArkivertForForespørsel
import no.nav.bidrag.reisekostnad.verifiserDokumentIkkeArkivertForForespørsel
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import javax.transaction.Transactional

@DisplayName("ArkiverSkeduleringTest")
class ArkiverTest : DatabehandlerTest() {

    @Test
    @Transactional
    fun skalArkivereFlereForespørsler() {
        val forespørsel = opppretteForespørsel()
        val forespørsel2 = opppretteForespørsel()
        val forespørsel3 = opppretteForespørsel()
        forespørselDao.save(forespørsel)
        forespørselDao.save(forespørsel2)
        forespørselDao.save(forespørsel3)
        databehandler.arkiverForespørslerSomErKlareForInnsending()

        assertSoftly {
            forespørsel.journalført shouldHaveSameDayAs LocalDateTime.now()
            forespørsel2.journalført shouldHaveSameDayAs LocalDateTime.now()
            forespørsel3.journalført shouldHaveSameDayAs LocalDateTime.now()
            forespørsel.idJournalpost shouldBe "1232132132"
            verifiserDokumentArkivertForForespørsel(forespørsel.id)
            verifiserDokumentArkivertForForespørsel(forespørsel2.id)
            verifiserDokumentArkivertForForespørsel(forespørsel3.id)
        }
    }

    @Test
    @Transactional
    fun skalArkivereForespørslerSomIkkeKreverSamtykke() {
        val forespørsel = opppretteForespørsel()
        forespørsel.isKreverSamtykke = false
        forespørsel.samtykket = null
        forespørselDao.save(forespørsel)
        databehandler.arkiverForespørslerSomErKlareForInnsending()

        assertSoftly {
            forespørsel.journalført shouldHaveSameDayAs LocalDateTime.now()
            forespørsel.idJournalpost shouldBe "1232132132"
            verifiserDokumentArkivertForForespørsel(forespørsel.id)
        }
    }

    @Test
    @Transactional
    fun skalArkivereForespørslerSomErSamtykket() {
        val forespørsel = opppretteForespørsel()
        forespørsel.isKreverSamtykke = true
        forespørsel.samtykket = LocalDateTime.now()
        forespørselDao.save(forespørsel)
        databehandler.arkiverForespørslerSomErKlareForInnsending()

        assertSoftly {
            forespørsel.journalført shouldHaveSameDayAs LocalDateTime.now()
            forespørsel.idJournalpost shouldBe "1232132132"
            verifiserDokumentArkivertForForespørsel(forespørsel.id)
        }
    }

    @Test
    @Transactional
    fun skalIkkeArkivereForespørslerSomIkkeErSamtykket() {
        val forespørsel = opppretteForespørsel()
        forespørsel.isKreverSamtykke = true

        val forespørsel2 = opppretteForespørsel()
        forespørsel2.isKreverSamtykke = true

        val forespørselDeaktivert = opppretteForespørsel()
        forespørselDeaktivert.deaktivert = LocalDateTime.now()
        forespørselDao.save(forespørsel)
        forespørselDao.save(forespørselDeaktivert)
        forespørselDao.save(forespørsel2)
        databehandler.arkiverForespørslerSomErKlareForInnsending()

        assertSoftly {
            forespørsel.journalført shouldBe null
            forespørsel.idJournalpost shouldBe null
            forespørsel2.journalført shouldBe null
            forespørsel2.idJournalpost shouldBe null
            forespørselDeaktivert.journalført shouldBe null
            forespørselDeaktivert.idJournalpost shouldBe null
            verifiserDokumentIkkeArkivertForForespørsel(forespørsel.id)
            verifiserDokumentIkkeArkivertForForespørsel(forespørselDeaktivert.id)
            verifiserDokumentIkkeArkivertForForespørsel(forespørsel2.id)
        }
    }
}
