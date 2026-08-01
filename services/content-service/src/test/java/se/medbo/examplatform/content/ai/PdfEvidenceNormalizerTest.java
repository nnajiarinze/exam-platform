package se.medbo.examplatform.content.ai;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class PdfEvidenceNormalizerTest {
  @Test void alignsPdfLineHyphenationMultilineWhitespaceSoftHyphensAndNbsp(){
    assertThat(PdfEvidenceNormalizer.uniqueMatch("Över en miljon svenskar utvand-\nrade till USA.","Över en miljon svenskar utvandrade till USA.").rawText()).isEqualTo("Över en miljon svenskar utvand-\nrade till USA.");
    assertThat(PdfEvidenceNormalizer.uniqueMatch("en snabb teknisk utveck-\r\nling.","en snabb teknisk utveckling.")).isNotNull();
    assertThat(PdfEvidenceNormalizer.uniqueMatch("ett informations-\noch kunskapssamhälle.","ett informations- och kunskapssamhälle.").normalizedText()).isEqualTo("ett informations- och kunskapssamhälle.");
    assertThat(PdfEvidenceNormalizer.uniqueMatch("en stor sam-\nhällsförändring till ett informations-\noch kunskapssamhälle.","en stor samhällsförändring till ett informations- och kunskapssamhälle.").normalizedText()).isEqualTo("en stor samhällsförändring till ett informations- och kunskapssamhälle.");
    assertThat(PdfEvidenceNormalizer.normalize("kun\u00adskaps\u00a0samhälle")).isEqualTo("kunskaps samhälle");
  }
  @Test void preservesPunctuationAndQualifiersAndRejectsParaphrasesAndCrossSectionMatches(){
    assertThat(PdfEvidenceNormalizer.uniqueMatch("År 1809 antogs lagen.","1809 antogs lagen.")).isNotNull();
    assertThat(PdfEvidenceNormalizer.uniqueMatch("År 1809 antogs lagen.","År 1809 antogs den viktiga lagen.")).isNull();
    assertThat(PdfEvidenceNormalizer.uniqueMatch("År 1809 antogs lagen.","År 1809 antogs lagen!")).isNull();
    assertThat(PdfEvidenceNormalizer.uniqueMatch("första delen", "första delen andra delen")).isNull();
  }
  @Test void rejectsAmbiguousRepeatedEvidence(){assertThat(PdfEvidenceNormalizer.uniqueMatch("samma text. samma text.","samma text.")).isNull();}
}
