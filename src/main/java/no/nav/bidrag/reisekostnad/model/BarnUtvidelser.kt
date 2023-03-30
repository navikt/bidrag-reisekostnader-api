package no.nav.bidrag.reisekostnad.model

import no.nav.bidrag.reisekostnad.database.datamodell.Barn

val Barn.harFylt15år get() = fødselsdato <= dato15ÅrTilbakeFraIdag
