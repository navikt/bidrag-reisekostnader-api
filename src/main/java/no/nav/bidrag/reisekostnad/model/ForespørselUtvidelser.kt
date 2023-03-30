package no.nav.bidrag.reisekostnad.model

import no.nav.bidrag.reisekostnad.database.datamodell.Forespørsel

val Forespørsel.erArkivert get() = journalført != null
val Forespørsel.kanArkiveres get() = !erArkivert && deaktivert == null && (!isKreverSamtykke || samtykket != null)
val Forespørsel.hovedpartIdent get() = hovedpart.personident
val Forespørsel.motpartIdent get() = motpart.personident
val Forespørsel.identerBarnSomHarFylt15år get() = barnSomHarFylt15år.map { it.personident }.toMutableSet()
val Forespørsel.barnSomHarFylt15år get() = barn.filter { it.harFylt15år }.toMutableSet()
val Forespørsel.alleBarnHarFylt15år get() = barnSomHarFylt15år.size == barn.size
fun Forespørsel.fjernBarnSomHarFylt15år() {
    barn = barn.filter { !it.harFylt15år }.toSet()
}
