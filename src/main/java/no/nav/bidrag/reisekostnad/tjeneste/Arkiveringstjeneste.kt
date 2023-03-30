package no.nav.bidrag.reisekostnad.tjeneste

import mu.KotlinLogging
import no.nav.bidrag.reisekostnad.database.datamodell.Forespørsel
import no.nav.bidrag.reisekostnad.feilhåndtering.Feilkode
import no.nav.bidrag.reisekostnad.feilhåndtering.Valideringsfeil
import no.nav.bidrag.reisekostnad.integrasjon.bidrag.doument.BidragDokumentkonsument
import no.nav.bidrag.reisekostnad.integrasjon.bidrag.doument.pdf.PdfGenerator
import no.nav.bidrag.reisekostnad.model.erArkivert
import no.nav.bidrag.reisekostnad.model.kanArkiveres
import no.nav.bidrag.reisekostnad.tjeneste.støtte.Mapper
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import javax.transaction.Transactional

private val log = KotlinLogging.logger {}

@Service
class Arkiveringstjeneste(
    val bidragDokumentkonsument: BidragDokumentkonsument,
    val mapper: Mapper,
    val databasetjeneste: Databasetjeneste
) {
    @Transactional
    fun arkivereForespørsel(idForespørsel: Int) {
        try {
            val forespørsel = databasetjeneste.henteAktivForespørsel(idForespørsel)

            if (forespørsel.erArkivert) return
            if (!forespørsel.kanArkiveres) throw Valideringsfeil(Feilkode.KAN_IKKE_ARKIVERE_FORESPØRSEL)

            val pdfDokument = opprettPdf(forespørsel)
            val referanseId = "$REISEKOSTNAD_REFERANSEIDPREFIKS$idForespørsel"

            val respons = bidragDokumentkonsument.opprettJournalpost(forespørsel.hovedpart.personident, referanseId, pdfDokument)
            forespørsel.journalført = LocalDateTime.now()
            forespørsel.idJournalpost = respons.journalpostId
            log.info { "Arkivert dokument for forespørsel $idForespørsel med journalpostid ${respons.journalpostId}" }
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved arkivering av dokument for forespørsel $idForespørsel", e)
        }
    }

    private fun opprettPdf(forespørsel: Forespørsel): ByteArray {
        val barn = forespørsel.barn.map { mapper.tilPersonDto(it?.personident) }.toSet()
        val hovedpart = mapper.tilPersonDto(forespørsel.hovedpart.personident)
        val motpart = mapper.tilPersonDto(forespørsel.motpart.personident)
        return PdfGenerator.genererePdf(barn, hovedpart, motpart, forespørsel.samtykket)
    }

    companion object {
        private const val REISEKOSTNAD_REFERANSEIDPREFIKS = "REISEKOSTNAD_"
    }
}
