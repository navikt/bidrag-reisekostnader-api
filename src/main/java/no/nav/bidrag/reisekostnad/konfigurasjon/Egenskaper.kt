package no.nav.bidrag.reisekostnad.konfigurasjon

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties
class Egenskaperkonfig {
    @Bean
    fun egenskaperBrukernotifikasjon(): Brukernotifikasjon {
        return Brukernotifikasjon()
    }

    @Bean
    fun egenskaper(): Egenskaper {
        return Egenskaper()
    }
}

@ConfigurationProperties(prefix = "egenskaper")
data class Egenskaper(
    var appnavnReisekostnad: String = "bidrag-reisekostnad",
    var urlReisekostnad: String = "",
    var brukernotifikasjon: Brukernotifikasjon = Brukernotifikasjon()
)

@ConfigurationProperties(prefix = "brukernotifikasjon")
data class Brukernotifikasjon(
    var emneBeskjed: String = "min-side.aapen-brukernotifikasjon-beskjed-v1",
    var emneFerdig: String = "min-side.aapen-brukernotifikasjon-done-v1",
    var emneOppgave: String = "min-side.aapen-brukernotifikasjon-oppgave-v1",
    var grupperingsidReisekostnad: String = "reisekostnad",
    var synlighetBeskjedAntallMaaneder: Int = 1,
    var levetidOppgaveAntallDager: Int = 30,
    var sikkerhetsnivaaBeskjed: Int = 3,
    var sikkerhetsnivaaOppgave: Int = 3,
    var skruddPaa: Boolean = true
)
