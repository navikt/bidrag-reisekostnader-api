package no.nav.bidrag.reisekostnad.model

import java.time.LocalDate

const val SYSTEMBRUKER_ID = "SYSTEM"
const val MDC_KORRELASJONSID = "correlationId"

val dato15ÅrTilbakeFraIdag = LocalDate.now().minusYears(15)
