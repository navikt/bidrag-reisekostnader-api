package no.nav.bidrag.reisekostnad.integrasjon.bidrag.doument

import no.nav.bidrag.dokument.dto.AvsenderMottakerDto
import no.nav.bidrag.dokument.dto.JournalpostType
import no.nav.bidrag.dokument.dto.OpprettDokumentDto
import no.nav.bidrag.dokument.dto.OpprettJournalpostRequest
import no.nav.bidrag.dokument.dto.OpprettJournalpostResponse
import no.nav.bidrag.reisekostnad.feilhåndtering.Arkiveringsfeil
import no.nav.bidrag.reisekostnad.feilhåndtering.Feilkode
import no.nav.bidrag.reisekostnad.konfigurasjon.Applikasjonskonfig.SIKKER_LOGG
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate

@Component
class BidragDokumentkonsument(@Qualifier("bidrag-dokument-azure-client-credentials") private val restTemplate: RestTemplate) {

    companion object {
        const val BEHANDLINGSTEMA_REISEKOSTNADER = "ab0129"
        const val DOKUMENTTITTEL = "Fordeling av reisekostnader"
        const val KONTEKST_ROT_BIDRAG_DOKUMENT = "/bidrag-dokument"
    }

    @Retryable(value = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 1000, multiplier = 2.0))
    fun opprettJournalpost(gjelderIdent: String, referanseid: String, dokumentByte: ByteArray): OpprettJournalpostResponse {
        val forespørsel = opprettJournalpostforespørsel(gjelderIdent, referanseid, dokumentByte)
        return try {
            restTemplate.exchange("$KONTEKST_ROT_BIDRAG_DOKUMENT/journalpost/JOARK", HttpMethod.POST, HttpEntity(forespørsel), OpprettJournalpostResponse::class.java).body!!
        } catch (hsce: HttpStatusCodeException) {
            if (HttpStatus.BAD_REQUEST == hsce.statusCode) {
                SIKKER_LOGG.warn("Kall mot bidrag-dokument for opprettelse av journalpost returnerte httpstatus ${hsce.statusCode} for gjelder-ident ${forespørsel.gjelderIdent}", hsce)
                throw Arkiveringsfeil(Feilkode.ARKIVERINGSFEIL_OPPGITTE_DATA, hsce.statusCode)
            } else {
                SIKKER_LOGG.warn("Kall mot bidrag-dokument for opprettelse av journalpost returnerte httpstatus ${hsce.statusCode} for referanseid ${forespørsel.referanseId}", hsce)
                throw Arkiveringsfeil(Feilkode.ARKIVERINGSFEIL, hsce.statusCode)
            }
        }
    }

    private fun opprettJournalpostforespørsel(gjelderIdent: String, referanseid: String, dokumentByte: ByteArray): OpprettJournalpostRequest {
        return OpprettJournalpostRequest(
            gjelderIdent = gjelderIdent,
            journalposttype = JournalpostType.INNGÅENDE,
            referanseId = referanseid,
            behandlingstema = BEHANDLINGSTEMA_REISEKOSTNADER,
            avsenderMottaker = AvsenderMottakerDto(ident = gjelderIdent),
            dokumenter = listOf(OpprettDokumentDto(tittel = DOKUMENTTITTEL, fysiskDokument = dokumentByte))
        )
    }
}
