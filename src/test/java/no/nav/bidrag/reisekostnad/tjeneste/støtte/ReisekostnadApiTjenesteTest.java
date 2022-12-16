package no.nav.bidrag.reisekostnad.tjeneste.støtte;

import static no.nav.bidrag.reisekostnad.Testperson.testpersonBarn10;
import static no.nav.bidrag.reisekostnad.Testperson.testpersonBarn16;
import static no.nav.bidrag.reisekostnad.Testperson.testpersonGråtass;
import static no.nav.bidrag.reisekostnad.Testperson.testpersonSirup;
import static no.nav.bidrag.reisekostnad.Testperson.testpersonStreng;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import no.nav.bidrag.reisekostnad.Testperson;
import no.nav.bidrag.reisekostnad.database.datamodell.Deaktivator;
import no.nav.bidrag.reisekostnad.database.datamodell.Forelder;
import no.nav.bidrag.reisekostnad.database.datamodell.Forespørsel;
import no.nav.bidrag.reisekostnad.database.datamodell.Oppgavebestilling;
import no.nav.bidrag.reisekostnad.integrasjon.bidrag.person.BidragPersonkonsument;
import no.nav.bidrag.reisekostnad.integrasjon.bidrag.person.api.Familiemedlem;
import no.nav.bidrag.reisekostnad.integrasjon.bidrag.person.api.HentFamilieRespons;
import no.nav.bidrag.reisekostnad.integrasjon.bidrag.person.api.HentPersoninfoRespons;
import no.nav.bidrag.reisekostnad.integrasjon.bidrag.person.api.MotpartBarnRelasjon;
import no.nav.bidrag.reisekostnad.integrasjon.brukernotifikasjon.Brukernotifikasjonkonsument;
import no.nav.bidrag.reisekostnad.tjeneste.Arkiveringstjeneste;
import no.nav.bidrag.reisekostnad.tjeneste.Databasetjeneste;
import no.nav.bidrag.reisekostnad.tjeneste.ReisekostnadApiTjeneste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext
@ExtendWith(MockitoExtension.class)
public class ReisekostnadApiTjenesteTest {

  private @Mock BidragPersonkonsument bidragPersonkonsument;
  private @Mock Brukernotifikasjonkonsument brukernotifikasjonkonsument;
  private @Mock Arkiveringstjeneste arkiveringstjeneste;
  private @Mock Databasetjeneste databasetjeneste;
  private @Mock Mapper mapper;
  private @InjectMocks ReisekostnadApiTjeneste reisekostnadApiTjeneste;

  @BeforeEach
  void resetteMocker() {
    reset(brukernotifikasjonkonsument);
  }

  @Test
  void skalOppretteForespørselKunForMotpartsBarn() {

    // gitt
    var hovedpart = testpersonGråtass;
    var motpart = testpersonStreng;
    var barnKullA = testpersonBarn10;
    var brorAvEnAnnenMor = testpersonBarn16;
    var enAnnenMor = testpersonSirup;

    var valgteKrypterteBarn = Set.of(Krypteringsverktøy.kryptere(barnKullA.getIdent()));
    var familierespons = oppretteHentFamilieRespons(hovedpart, Map.of(motpart, Set.of(barnKullA), enAnnenMor, Set.of(brorAvEnAnnenMor)));

    mockHentPersoninfo(Set.of(barnKullA));
    when(bidragPersonkonsument.hentFamilie(hovedpart.getIdent())).thenReturn(Optional.of(familierespons));
    when(databasetjeneste.lagreNyForespørsel(hovedpart.getIdent(), motpart.getIdent(), Set.of(barnKullA.getIdent()), true)).thenReturn(1);
    doNothing().when(brukernotifikasjonkonsument).oppretteOppgaveTilMotpartOmSamtykke(anyInt(), anyString());

    // hvis
    var respons = reisekostnadApiTjeneste.oppretteForespørselOmFordelingAvReisekostnader(hovedpart.getIdent(), valgteKrypterteBarn);

    // så
    verify(brukernotifikasjonkonsument, times(1)).oppretteOppgaveTilMotpartOmSamtykke(1, motpart.getIdent());
    assertThat(respons.is2xxSuccessful());
  }

  @Test
  void skalBestilleSlettingAvSamtykkeoppgaveDersomHovedpartTrekkerForespørsel() {

    // gitt
    var idForespørsel = 1;
    var hovedpart = testpersonGråtass;
    var motpart = testpersonStreng;
    var deaktivertForespørsel = Forespørsel.builder().id(idForespørsel).deaktivert(LocalDateTime.now()).deaktivertAv(Deaktivator.HOVEDPART)
        .motpart(Forelder.builder().personident(motpart.getIdent()).build()).build();
    var aktivOppgave = Oppgavebestilling.builder().eventId("eventId").build();

    when(databasetjeneste.deaktivereForespørsel(idForespørsel, hovedpart.getIdent())).thenReturn(deaktivertForespørsel);
    when(databasetjeneste.henteAktiveOppgaverMotpart(idForespørsel, motpart.getIdent())).thenReturn(Set.of(aktivOppgave));

    // hvis
    var respons = reisekostnadApiTjeneste.trekkeForespørsel(idForespørsel, hovedpart.getIdent());

    // så
    assertAll(() -> assertThat(respons.is2xxSuccessful()),
        () -> verify(brukernotifikasjonkonsument, times(1)).sletteSamtykkeoppgave("eventId", motpart.getIdent()));
  }

  @Test
  void skalBestilleSlettingAvSamtykkeoppgaveDersomMotpartTrekkerForespørsel() {

    // gitt
    var idForespørsel = 1;
    var hovedpart = testpersonGråtass;
    var motpart = testpersonStreng;
    var deaktivertForespørsel = Forespørsel.builder().id(idForespørsel).deaktivert(LocalDateTime.now()).deaktivertAv(Deaktivator.MOTPART)
        .motpart(Forelder.builder().personident(motpart.getIdent()).build()).hovedpart(Forelder.builder().personident(hovedpart.getIdent()).build())
        .build();
    var aktivOppgave = Oppgavebestilling.builder().eventId("eventId").build();

    when(databasetjeneste.deaktivereForespørsel(idForespørsel, motpart.getIdent())).thenReturn(deaktivertForespørsel);
    when(databasetjeneste.henteAktiveOppgaverMotpart(idForespørsel, motpart.getIdent())).thenReturn(Set.of(aktivOppgave));

    // hvis
    var respons = reisekostnadApiTjeneste.trekkeForespørsel(idForespørsel, motpart.getIdent());

    // så
    assertAll(() -> assertThat(respons.is2xxSuccessful()),
        () -> verify(brukernotifikasjonkonsument, times(1)).sletteSamtykkeoppgave("eventId", motpart.getIdent()));
  }

  @Test
  void skalBestilleOpprettelseAvSamtykkeoppgaveVedOpprettelseAvForespørsel() {

    // gitt
    var idForespørsel = 1;
    var hovedpart = testpersonGråtass;
    var motpart = testpersonStreng;
    var barn = testpersonBarn10;

    var valgteKrypterteBarn = Set.of(Krypteringsverktøy.kryptere(barn.getIdent()));
    var familierespons = oppretteHentFamilieRespons(hovedpart, motpart, Set.of(barn));

    mockHentPersoninfo(Set.of(barn));
    when(bidragPersonkonsument.hentFamilie(hovedpart.getIdent())).thenReturn(Optional.of(familierespons));
    when(databasetjeneste.lagreNyForespørsel(hovedpart.getIdent(), motpart.getIdent(), Set.of(barn.getIdent()), true)).thenReturn(idForespørsel);
    doNothing().when(brukernotifikasjonkonsument).oppretteOppgaveTilMotpartOmSamtykke(anyInt(), anyString());

    // hvis
    var respons = reisekostnadApiTjeneste.oppretteForespørselOmFordelingAvReisekostnader(hovedpart.getIdent(), valgteKrypterteBarn);

    // så
    verify(brukernotifikasjonkonsument, times(1)).oppretteOppgaveTilMotpartOmSamtykke(1, motpart.getIdent());
    assertAll(() -> assertThat(respons.is2xxSuccessful()),
        () -> verify(brukernotifikasjonkonsument, times(1)).oppretteOppgaveTilMotpartOmSamtykke(idForespørsel, motpart.getIdent()));
  }

  @Test
  void skalVarsleOmNeiTilSamtykke() {

    // gitt
    var idForespørsel = 1;
    var hovedpart = testpersonGråtass;
    var motpart = testpersonStreng;
    var deaktivertForespørsel = Forespørsel.builder().id(idForespørsel).deaktivert(LocalDateTime.now()).deaktivertAv(Deaktivator.MOTPART)
        .motpart(Forelder.builder().personident(motpart.getIdent()).build()).hovedpart(Forelder.builder().personident(hovedpart.getIdent()).build())
        .build();
    var aktivOppgave = Oppgavebestilling.builder().eventId("eventId").build();

    when(databasetjeneste.deaktivereForespørsel(idForespørsel, motpart.getIdent())).thenReturn(deaktivertForespørsel);
    when(databasetjeneste.henteAktiveOppgaverMotpart(idForespørsel, motpart.getIdent())).thenReturn(Set.of(aktivOppgave));

    // hvis
    var respons = reisekostnadApiTjeneste.trekkeForespørsel(idForespørsel, motpart.getIdent());

    // så
    assertAll(() -> assertThat(respons.is2xxSuccessful()),
        () -> verify(brukernotifikasjonkonsument, times(1)).sletteSamtykkeoppgave("eventId", motpart.getIdent()),
        () -> verify(brukernotifikasjonkonsument, times(1)).varsleOmNeiTilSamtykke(hovedpart.getIdent(), motpart.getIdent()));

  }

  private HentFamilieRespons oppretteHentFamilieRespons(Testperson hovedpart, Map<Testperson, Set<Testperson>> motpartBarnrelasjoner) {

    return HentFamilieRespons.builder().person(tilFamiliemedlem(hovedpart)).personensMotpartBarnRelasjon(
        motpartBarnrelasjoner.entrySet().stream().map(b -> tilMotpartBarnRelasjon(b.getKey(), b.getValue())).collect(Collectors.toList())).build();
  }

  private HentFamilieRespons oppretteHentFamilieRespons(Testperson hovedpart, Testperson motpart, Set<Testperson> fellesBarn) {
    return oppretteHentFamilieRespons(hovedpart, Map.of(motpart, fellesBarn));
  }

  private void mockHentPersoninfo(Set<Testperson> testpersoner) {

    for (Testperson testperson : testpersoner) {
      var respons = HentPersoninfoRespons.builder().foedselsdato(testperson.getFødselsdato()).fornavn(testperson.getFornavn()).build();

      when(bidragPersonkonsument.hentPersoninfo(testperson.getIdent())).thenReturn(respons);
    }
  }

  private MotpartBarnRelasjon tilMotpartBarnRelasjon(Testperson motpart, Set<Testperson> fellesBarn) {
    return MotpartBarnRelasjon.builder().motpart(tilFamiliemedlem(motpart))
        .fellesBarn(fellesBarn.stream().map(this::tilFamiliemedlem).collect(Collectors.toList())).build();
  }

  private Familiemedlem tilFamiliemedlem(Testperson testperson) {
    return Familiemedlem.builder().ident(testperson.getIdent()).fornavn(testperson.getFornavn())
        .foedselsdato(LocalDate.now().minusYears(testperson.getAlder())).build();
  }
}
