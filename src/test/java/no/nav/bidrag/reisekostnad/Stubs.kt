package no.nav.bidrag.reisekostnad

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.matching.ContainsPattern
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder

fun getBidragDokumentRequestPatternBuilder(forespørselId: Int?): RequestPatternBuilder? {
    val verify = WireMock.postRequestedFor(
        WireMock.urlEqualTo("/bidrag-dokument/journalpost/JOARK")
    )
    verify.withRequestBody(ContainsPattern(String.format("\"referanseId\":\"REISEKOSTNAD_%s\"", forespørselId)))
    return verify
}

fun verifiserDokumentArkivertForForespørsel(forespørselId: Int?) {
    WireMock.verify(getBidragDokumentRequestPatternBuilder(forespørselId))
}

fun verifiserDokumentArkivertForForespørselAntallGanger(antallGanger: Int) {
    WireMock.verify(
        antallGanger,
        WireMock.postRequestedFor(
            WireMock.urlEqualTo("/bidrag-dokument/journalpost/JOARK")
        )
    )
}

fun verifiserDokumentIkkeArkivertForForespørsel(forespørselId: Int?) {
    WireMock.verify(0, getBidragDokumentRequestPatternBuilder(forespørselId))
}
