package no.nav.bidrag.reisekostnad.integrasjon.brukernotifikasjon;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import no.nav.bidrag.reisekostnad.database.datamodell.Forelder;
import no.nav.bidrag.reisekostnad.feilhåndtering.Feilkode;
import no.nav.bidrag.reisekostnad.feilhåndtering.InternFeil;
import no.nav.bidrag.reisekostnad.konfigurasjon.Egenskaper;
import no.nav.bidrag.reisekostnad.tjeneste.Databasetjeneste;
import no.nav.brukernotifikasjon.schemas.builders.DoneInputBuilder;
import no.nav.brukernotifikasjon.schemas.input.DoneInput;
import no.nav.brukernotifikasjon.schemas.input.NokkelInput;
import no.nav.bidrag.reisekostnad.database.dao.OppgavebestillingDao;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
@Value
public class Ferdigprodusent {

  KafkaTemplate kafkaTemplate;
  Databasetjeneste databasetjeneste;
  OppgavebestillingDao oppgavebestillingDao;
  Egenskaper egenskaper;

  public void ferdigstilleFarsSigneringsoppgave(Forelder motpart, NokkelInput nokkel) {

    var oppgaveSomSkalFerdigstilles = oppgavebestillingDao.henteOppgavebestilling(nokkel.getEventId());

    if (oppgaveSomSkalFerdigstilles.isPresent() && oppgaveSomSkalFerdigstilles.get().getFerdigstilt() == null) {
      var melding = oppretteDone();
      try {
        kafkaTemplate.send(egenskaper.getBrukernotifikasjon().getEmneFerdig(), nokkel, melding);
      } catch (Exception e) {
        throw new InternFeil(Feilkode.BRUKERNOTIFIKASJON_OPPRETTE_OPPGAVE, e);
      }

      log.info("Ferdigmelding ble sendt for oppgave med eventId {}.");
      databasetjeneste.setteOppgaveTilFerdigstilt(nokkel.getEventId());
    } else {
      log.warn("Fant ingen aktiv oppgavebestilling for eventId {} (gjelder far med id: {}). Bestiller derfor ikke ferdigstilling.",
          nokkel.getEventId(), motpart.getId());
    }
  }

  private DoneInput oppretteDone() {
    return new DoneInputBuilder()
        .withTidspunkt(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime())
        .build();
  }
}
