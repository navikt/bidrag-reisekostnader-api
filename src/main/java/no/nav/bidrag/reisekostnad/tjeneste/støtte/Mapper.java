package no.nav.bidrag.reisekostnad.tjeneste.støtte;

import static no.nav.bidrag.reisekostnad.integrasjon.bidrag.person.BidragPersonkonsument.FORMAT_FØDSELSDATO;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import no.nav.bidrag.reisekostnad.api.dto.ut.BrukerinformasjonDto;
import no.nav.bidrag.reisekostnad.api.dto.ut.ForespørselDto;
import no.nav.bidrag.reisekostnad.api.dto.ut.MotpartDto;
import no.nav.bidrag.reisekostnad.api.dto.ut.PersonDto;
import no.nav.bidrag.reisekostnad.database.dao.ForespørselDao;
import no.nav.bidrag.reisekostnad.database.datamodell.Barn;
import no.nav.bidrag.reisekostnad.database.datamodell.Forespørsel;
import no.nav.bidrag.reisekostnad.database.datamodell.Person;
import no.nav.bidrag.reisekostnad.integrasjon.bidrag.person.BidragPersonkonsument;
import no.nav.bidrag.reisekostnad.integrasjon.bidrag.person.api.Diskresjonskode;
import no.nav.bidrag.reisekostnad.integrasjon.bidrag.person.api.Familiemedlem;
import no.nav.bidrag.reisekostnad.integrasjon.bidrag.person.api.HentFamilieRespons;
import no.nav.bidrag.reisekostnad.integrasjon.bidrag.person.api.MotpartBarnRelasjon;
import org.apache.commons.lang3.StringUtils;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.config.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Mapper {

  public static final String PERSON_IKKE_FUNNET = "UKJENT";
  private ModelMapper modelMapper = new ModelMapper();
  private ForespørselDao forespørselDao;
  private BidragPersonkonsument bidragPersonkonsument;

  @Autowired
  public Mapper(BidragPersonkonsument bidragPersonkonsument, ForespørselDao forespørselDao) {
    this.bidragPersonkonsument = bidragPersonkonsument;
    this.forespørselDao = forespørselDao;
    this.modelMapper.getConfiguration()
        .setFieldMatchingEnabled(true)
        .setFieldAccessLevel(Configuration.AccessLevel.PRIVATE);
    oppretteTypeMaps();
  }

  private void oppretteTypeMaps() {
    this.modelMapper.createTypeMap(Familiemedlem.class, PersonDto.class);
    this.modelMapper.createTypeMap(Forespørsel.class, ForespørselDto.class);
    this.modelMapper.createTypeMap(ForespørselDto.class, Forespørsel.class);
    this.modelMapper.createTypeMap(Person.class, PersonDto.class);
  }

  public BrukerinformasjonDto tilDto(HentFamilieRespons familieRespons) {
    var forespørslerHvorPersonErHovedpart = forespørselDao.henteAktiveForespørslerHvorPersonErHovedpart(familieRespons.getPerson().getIdent());
    var forespørslerHvorPersonErMotpart = forespørselDao.henteAktiveForespørslerHvorPersonErMotpart(familieRespons.getPerson().getIdent());

    var familierUtenDiskresjon = henteMotpartBarnRelasjonerSomIkkeHarDiskresjon(familieRespons);

    return BrukerinformasjonDto.builder()
        .fornavn(familieRespons.getPerson().getFornavn())
        .kjønn(familieRespons.getPerson().getKjønn())
        .harSkjulteFamilieenheterMedDiskresjon(familierUtenDiskresjon.size() < familieRespons.getPersonensMotpartBarnRelasjon().size())
        .kanSøkeOmFordelingAvReisekostnader(personHarDeltForeldreansvar(familierUtenDiskresjon))
        .barnMinstFemtenÅr(henteBarnOverFemtenÅr(familierUtenDiskresjon))
        .forespørslerSomHovedpart(tilForespørselDto(forespørslerHvorPersonErHovedpart))
        .forespørslerSomMotpart(tilForespørselDto(forespørslerHvorPersonErMotpart))
        .motparterMedFellesBarnUnderFemtenÅr(filtrereUtMotparterMedFellesBarnUnderFemtenÅr(familierUtenDiskresjon))
        .build();
  }

  private PersonDto tilDto(Familiemedlem familiemedlem) {

    var egenskapmapper = modelMapper.getTypeMap(Familiemedlem.class, PersonDto.class);

    Converter<String, String> konverterePersonident = ident -> ident.getSource() == null ? null : kryptere(ident.getSource());

    egenskapmapper.addMappings(mapper -> mapper.using(konverterePersonident).map(Familiemedlem::getIdent, PersonDto::setIdent));
    egenskapmapper.addMappings(mapper -> mapper.map(Familiemedlem::getFoedselsdato, PersonDto::setFødselsdato));

    return modelMapper.map(familiemedlem, PersonDto.class);
  }

  /**
   * Filtrerer bort alle familieenehter hvor enten motpart eller minst ett av barna har diskresjon
   */
  private Set<MotpartBarnRelasjon> henteMotpartBarnRelasjonerSomIkkeHarDiskresjon(HentFamilieRespons familierespons) {
    var motpartBarnRelasjonUtenMotparterMedDiskresjon = filtrereBortEnheterDerMotpartHarDiskresjon(familierespons.getPersonensMotpartBarnRelasjon());

   return  motpartBarnRelasjonUtenMotparterMedDiskresjon.stream()
        .filter(Objects::nonNull)
        .filter(m -> !Diskresjonskode.harMinstEttFamiliemedlemHarDiskresjon(m.getFellesBarn()))
        .collect(
            Collectors.toSet());
  }

  private Set<MotpartBarnRelasjon> filtrereBortEnheterDerMotpartHarDiskresjon(List<MotpartBarnRelasjon> motpartBarnRelasjons) {
    return motpartBarnRelasjons.stream().filter(Objects::nonNull).filter(m -> StringUtils.isEmpty(m.getMotpart().getDiskresjonskode()))
        .collect(Collectors.toSet());
  }

  private boolean personHarDeltForeldreansvar(Set<MotpartBarnRelasjon> motpartBarnRelasjoner) {
    return motpartBarnRelasjoner.size() > 0
        && motpartBarnRelasjoner.iterator().hasNext()
        && motpartBarnRelasjoner.iterator().next().getFellesBarn().size() > 0;
  }

  private Set<PersonDto> henteBarnOverFemtenÅr(Set<MotpartBarnRelasjon> motpartBarnRelasjoner) {
    return motpartBarnRelasjoner.stream().filter(Objects::nonNull)
        .flatMap(mbr -> mbr.getFellesBarn().stream())
        .filter(this::erMinstFemtenÅr)
        .map(this::tilDto)
        .collect(Collectors.toSet());
  }

  private boolean erMinstFemtenÅr(Familiemedlem barn) {

    if (barn == null || barn.getFoedselsdato() == null) {
      return false;
    }

    return barn.getFoedselsdato().isBefore(LocalDate.now().plusDays(1).minusYears(15));
  }

  private boolean erUnderFemtenÅr(Familiemedlem barn) {

    if (barn == null || barn.getFoedselsdato() == null) {
      return false;
    }

    return barn.getFoedselsdato().isAfter(LocalDate.now().minusYears(15));
  }

  private Set<MotpartDto> filtrereUtMotparterMedFellesBarnUnderFemtenÅr(Set<MotpartBarnRelasjon> motpartBarnRelasjoner) {
    Set<MotpartDto> motparterMedBarnUnderFemtenÅr = new HashSet<>();
    for (MotpartBarnRelasjon motpartBarnRelasjon : motpartBarnRelasjoner) {
      var barnUnderFemtenÅr = filtereUtBarnUnderFemtenÅr(motpartBarnRelasjon.getFellesBarn());
      if (barnUnderFemtenÅr.size() > 0) {
        var motpartDto = MotpartDto.builder()
            .motpart(tilDto(motpartBarnRelasjon.getMotpart()))
            .fellesBarnUnder15År(barnUnderFemtenÅr)
            .build();
        motparterMedBarnUnderFemtenÅr.add(motpartDto);
      }
    }
    return motparterMedBarnUnderFemtenÅr;
  }

  private Set<PersonDto> filtereUtBarnUnderFemtenÅr(List<Familiemedlem> barn) {
    return barn.stream().filter(Objects::nonNull).filter(this::erUnderFemtenÅr).map(this::tilDto).collect(Collectors.toSet());
  }

  private Set<ForespørselDto> tilForespørselDto(Set<Forespørsel> forespørsler) {
    return forespørsler.stream().filter(Objects::nonNull).map(this::tilForespørselDto).collect(Collectors.toSet());
  }

  private ForespørselDto tilForespørselDto(Forespørsel forespørsel) {
    var forespørselmapper = modelMapper.getTypeMap(Forespørsel.class, ForespørselDto.class);

    Converter<String, LocalDate> konvertereDatostreng = d -> d.getSource() == null ? null
        : LocalDate.parse(d.getSource(), DateTimeFormatter.ofPattern(FORMAT_FØDSELSDATO));

    Converter<Person, PersonDto> tilPersonDto = context -> tilPersonDto(context.getSource().getPersonident());
    Converter<Set<Person>, Set<PersonDto>> tilPersonDtoSet = context -> context.getSource().stream()
        .map(element -> tilPersonDto(element.getPersonident())).collect(Collectors.toSet());

    forespørselmapper.addMappings(mapper -> mapper.using(konvertereDatostreng).map(Forespørsel::getHovedpart, ForespørselDto::setHovedpart));
    forespørselmapper.addMappings(mapper -> mapper.using(konvertereDatostreng).map(Forespørsel::getMotpart, ForespørselDto::setMotpart));

    forespørselmapper.addMappings(mapper -> mapper.using(tilPersonDto).map(Forespørsel::getHovedpart, ForespørselDto::setHovedpart));
    forespørselmapper.addMappings(mapper -> mapper.using(tilPersonDto).map(Forespørsel::getMotpart, ForespørselDto::setMotpart));
    forespørselmapper.addMappings(mapper -> mapper.using(tilPersonDtoSet).map(Forespørsel::getBarn, ForespørselDto::setBarn));

    return modelMapper.map(forespørsel, ForespørselDto.class);
  }

  public Set<Barn> tilEntitet(Set<String> personidenterBarn) {
    return personidenterBarn.stream().filter(Objects::nonNull).filter(s -> !s.isEmpty())
        .map(personident -> Barn.builder().personident(personident).build()).collect(
            Collectors.toSet());
  }

  public Set<String> tilStringSet(List<Familiemedlem> familiemeldlemmer) {
    return familiemeldlemmer.stream().filter(Objects::nonNull).map(f -> f.getIdent()).collect(Collectors.toSet());
  }

  private PersonDto tilPersonDto(String personident) {
    var personinfo = bidragPersonkonsument.hentPersoninfo(personident);
    if (personinfo.isPresent()) {
      return new PersonDto(kryptere(personident), personinfo.get().getFornavn(), personinfo.get().getFoedselsdato());
    } else {
      return new PersonDto(null, PERSON_IKKE_FUNNET, null);
    }
  }

  private String kryptere(String ukryptertPersonident) {
    return Krypteringsverktøy.kryptere(ukryptertPersonident);
  }
}
