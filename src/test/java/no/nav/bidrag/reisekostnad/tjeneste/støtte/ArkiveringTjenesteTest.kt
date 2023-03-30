package no.nav.bidrag.reisekostnad.tjeneste.støtte

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.date.shouldHaveSameDayAs
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import io.mockk.verify
import no.nav.bidrag.dokument.dto.OpprettJournalpostResponse
import no.nav.bidrag.reisekostnad.api.dto.ut.PersonDto
import no.nav.bidrag.reisekostnad.database.datamodell.Barn
import no.nav.bidrag.reisekostnad.database.datamodell.Forelder
import no.nav.bidrag.reisekostnad.database.datamodell.Forespørsel
import no.nav.bidrag.reisekostnad.feilhåndtering.Arkiveringsfeil
import no.nav.bidrag.reisekostnad.feilhåndtering.Feilkode
import no.nav.bidrag.reisekostnad.integrasjon.bidrag.doument.BidragDokumentkonsument
import no.nav.bidrag.reisekostnad.integrasjon.bidrag.doument.pdf.PdfGenerator
import no.nav.bidrag.reisekostnad.tjeneste.Arkiveringstjeneste
import no.nav.bidrag.reisekostnad.tjeneste.Databasetjeneste
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class ArkiveringTjenesteTest {
    @MockK
    private lateinit var bidragDokument: BidragDokumentkonsument

    @MockK
    private lateinit var databasetjeneste: Databasetjeneste

    @MockK
    lateinit var mapper: Mapper

    @InjectMockKs
    lateinit var arkiveringstjeneste: Arkiveringstjeneste

    // TESTDATA
    private val hovedpartFodselsdato = LocalDate.parse("2022-01-02")
    private val motpartFodselsdato = LocalDate.parse("2022-01-02")
    private val identHovedpart = "123213213"
    private val identMotpart = "3541555"

    private val identBarn1 = "565551251"
    private val identBarn2 = "41244124"
    private val barn = setOf(Barn.builder().personident(identBarn1).build(), Barn.builder().personident(identBarn2).build())

    private val barn1Dto = PersonDto(Krypteringsverktøy.kryptere(identBarn1), null, null, motpartFodselsdato)
    private val barn2Dto = PersonDto(Krypteringsverktøy.kryptere(identBarn2), null, null, motpartFodselsdato)
    private val hovedpartDto = PersonDto(Krypteringsverktøy.kryptere(identHovedpart), null, null, hovedpartFodselsdato)
    private val motpartDto = PersonDto(Krypteringsverktøy.kryptere(identMotpart), null, null, motpartFodselsdato)
    private val produsertDokument = "Produsert dokument".toByteArray()

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()

        mockkStatic(PdfGenerator::class)
        every { PdfGenerator.genererePdf(any(), any(), any(), any()) } returns produsertDokument
        every { mapper.tilPersonDto(identHovedpart) } returns hovedpartDto
        every { mapper.tilPersonDto(identMotpart) } returns motpartDto
        every { mapper.tilPersonDto(identBarn2) } returns barn2Dto
        every { mapper.tilPersonDto(identBarn1) } returns barn1Dto
    }

    @Test
    fun skalOppretteDokumentOgArkivereDokument() {
        val responsJournalpostId = "123213213333"

        val forespørsel = oppretteForespørsel(identHovedpart, identMotpart, barn)
        forespørsel.id = 5151515

        every { databasetjeneste.henteAktivForespørsel(any()) } returns forespørsel
        every { bidragDokument.opprettJournalpost(any(), any(), any()) } returns OpprettJournalpostResponse(responsJournalpostId)

        arkiveringstjeneste.arkivereForespørsel(forespørsel.id)

        assertSoftly {
            forespørsel.journalført shouldHaveSameDayAs LocalDateTime.now()
            forespørsel.idJournalpost shouldBe responsJournalpostId
            verify { bidragDokument.opprettJournalpost(identHovedpart, match { it.contains(forespørsel.id.toString()) }, produsertDokument) }
            verify { PdfGenerator.genererePdf(mutableSetOf(barn1Dto, barn2Dto), hovedpartDto, motpartDto, any()) }
        }
    }

    @Test
    fun skalIkkeArkivereHvisAlleredeArkivert() {
        val forespørsel = oppretteForespørsel(identHovedpart, identMotpart, barn)
        forespørsel.id = 5151515
        forespørsel.journalført = LocalDateTime.now()
        forespørsel.idJournalpost = "13123"

        every { databasetjeneste.henteAktivForespørsel(any()) } returns forespørsel

        arkiveringstjeneste.arkivereForespørsel(forespørsel.id)

        assertSoftly {
            verify(exactly = 0, verifyBlock = { bidragDokument.opprettJournalpost(any(), any(), any()) })
        }
    }

    @Test
    fun skalIkkeArkivereHvisDeaktivert() {
        val forespørsel = oppretteForespørsel(identHovedpart, identMotpart, barn)
        forespørsel.id = 5151515
        forespørsel.deaktivert = LocalDateTime.now()

        every { databasetjeneste.henteAktivForespørsel(any()) } returns forespørsel

        arkiveringstjeneste.arkivereForespørsel(forespørsel.id)

        assertSoftly {
            verify(exactly = 0, verifyBlock = { bidragDokument.opprettJournalpost(any(), any(), any()) })
        }
    }

    @Test
    fun skalIkkeArkivereHvisManglerSamtykke() {
        val forespørsel = oppretteForespørsel(identHovedpart, identMotpart, barn)
        forespørsel.id = 5151515
        forespørsel.isKreverSamtykke = true
        forespørsel.samtykket = null

        every { databasetjeneste.henteAktivForespørsel(any()) } returns forespørsel

        arkiveringstjeneste.arkivereForespørsel(forespørsel.id)

        assertSoftly {
            verify(exactly = 0, verifyBlock = { bidragDokument.opprettJournalpost(any(), any(), any()) })
        }
    }

    @Test
    fun skalArkivereHvisIkkeKreverSamtykkeOGManglerSamtykke() {
        val responsJournalpostId = "123213213333"

        val forespørsel = oppretteForespørsel(identHovedpart, identMotpart, barn)
        forespørsel.id = 5151515
        forespørsel.isKreverSamtykke = false
        forespørsel.samtykket = null

        every { databasetjeneste.henteAktivForespørsel(any()) } returns forespørsel
        every { bidragDokument.opprettJournalpost(any(), any(), any()) } returns OpprettJournalpostResponse(responsJournalpostId)

        arkiveringstjeneste.arkivereForespørsel(forespørsel.id)

        assertSoftly {
            forespørsel.journalført shouldHaveSameDayAs LocalDateTime.now()
            forespørsel.idJournalpost shouldBe responsJournalpostId
            verify { bidragDokument.opprettJournalpost(identHovedpart, match { it.contains(forespørsel.id.toString()) }, produsertDokument) }
            verify { PdfGenerator.genererePdf(mutableSetOf(barn1Dto, barn2Dto), hovedpartDto, motpartDto, any()) }
        }
    }

    @Test
    fun skalIkkeKasteFeilHvisOpprettDokumentFeiler() {
        val forespørsel = oppretteForespørsel(identHovedpart, identMotpart, barn)
        forespørsel.id = 5151515

        every { databasetjeneste.henteAktivForespørsel(any()) } returns forespørsel
        every { bidragDokument.opprettJournalpost(any(), any(), any()) } throws Arkiveringsfeil(Feilkode.ARKIVERINGSFEIL, HttpStatus.BAD_REQUEST)

        arkiveringstjeneste.arkivereForespørsel(forespørsel.id)

        assertSoftly {
            forespørsel.journalført shouldBe null
            forespørsel.idJournalpost shouldBe null
            verify { bidragDokument.opprettJournalpost(identHovedpart, match { it.contains(forespørsel.id.toString()) }, any()) }
        }
    }

    private fun oppretteForespørsel(identHovedpart: String, identMotpart: String, barn: Set<Barn>): Forespørsel {
        return Forespørsel.builder()
            .barn(barn)
            .hovedpart(Forelder.builder().personident(identHovedpart).build())
            .motpart(Forelder.builder().personident(identMotpart).build())
            .opprettet(LocalDateTime.now().minusDays(5))
            .samtykket(LocalDateTime.now().minusDays(5))
            .build()
    }
}
