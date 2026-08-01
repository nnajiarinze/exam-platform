package se.medbo.examplatform.content.ai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class PdfEvidenceNormalizer {
  static final String VERSION="pdf-evidence-normalization-v3";
  record Normalized(String text,List<Integer> rawStarts,List<Integer> rawEnds,List<String> operations){}
  record Match(String rawText,String normalizedText,int rawStart,int rawEnd,int normalizedStart,int normalizedEnd,List<String> operations){}

  static String normalize(String value){return mapped(value,false).text();}
  static Match uniqueMatch(String source,String evidence){
    var preserved=match(source,evidence,false);if(preserved!=null)return preserved;var dehyphenated=match(source,evidence,true);return dehyphenated!=null?dehyphenated:hybridMatch(source,evidence);
  }
  private static Match match(String source,String evidence,boolean dehyphenate){
    var normalizedSource=mapped(source,dehyphenate);String needle=mapped(evidence,dehyphenate).text();if(needle.isBlank())return null;
    int first=normalizedSource.text().indexOf(needle);if(first<0||normalizedSource.text().indexOf(needle,first+1)>=0)return null;
    int last=first+needle.length();int rawStart=normalizedSource.rawStarts().get(first),rawEnd=normalizedSource.rawEnds().get(last-1);
    return new Match(source.substring(rawStart,rawEnd),needle,rawStart,rawEnd,first,last,normalizedSource.operations());
  }
  private static Match hybridMatch(String source,String evidence){
    String expected=normalize(evidence);if(expected.isBlank())return null;var pattern=new StringBuilder();
    for(int i=0;i<expected.length();i++){char c=expected.charAt(i);if(Character.isWhitespace(c)){pattern.append("\\s+");continue;}pattern.append(java.util.regex.Pattern.quote(String.valueOf(c)));if(Character.isLetter(c)&&i+1<expected.length()&&Character.isLetter(expected.charAt(i+1)))pattern.append("(?:-\\s*)?");}
    var matcher=java.util.regex.Pattern.compile(pattern.toString()).matcher(Normalizer.normalize(source,Normalizer.Form.NFC));if(!matcher.find())return null;int start=matcher.start(),end=matcher.end();if(matcher.find())return null;String raw=source.substring(start,end);var operations=new LinkedHashSet<String>();operations.addAll(mapped(raw,false).operations());if(raw.matches("(?s).*\\p{L}-\\s*[\\r\\n]+\\s*\\p{Ll}.*"))operations.add("PDF_LINE_BREAK_HYPHENATION_ALIGNMENT");int normalizedStart=normalize(source.substring(0,start)).length();return new Match(raw,expected,start,end,normalizedStart,normalizedStart+expected.length(),List.copyOf(operations));
  }
  static Normalized mapped(String input,boolean dehyphenate){
    String source=Normalizer.normalize(input==null?"":input,Normalizer.Form.NFC);var out=new StringBuilder();var starts=new ArrayList<Integer>();var ends=new ArrayList<Integer>();Set<String> operations=new LinkedHashSet<>();
    for(int i=0;i<source.length();){char c=source.charAt(i);
      if(c=='\u00ad'){operations.add("SOFT_HYPHEN_REMOVAL");i++;continue;}
      if(c=='\u00a0'){operations.add("NON_BREAKING_SPACE_TO_SPACE");c=' ';}
      if(dehyphenate&&c=='-'&&i>0&&Character.isLetter(source.charAt(i-1))){int j=i+1;boolean lineBreak=false;while(j<source.length()&&Character.isWhitespace(source.charAt(j))){lineBreak|=source.charAt(j)=='\n'||source.charAt(j)=='\r';j++;}if(lineBreak&&j<source.length()&&Character.isLowerCase(source.charAt(j))){operations.add("PDF_LINE_BREAK_HYPHENATION_REMOVAL");i=j;continue;}}
      if(Character.isWhitespace(c)){int begin=i;boolean multiline=false;while(i<source.length()&&Character.isWhitespace(source.charAt(i))){multiline|=source.charAt(i)=='\n'||source.charAt(i)=='\r';i++;}if(out.length()>0&&out.charAt(out.length()-1)!=' '){out.append(' ');starts.add(begin);ends.add(i);}operations.add(multiline?"MULTILINE_WHITESPACE_COLLAPSE":"WHITESPACE_COLLAPSE");continue;}
      out.append(c);starts.add(i);ends.add(i+1);i++;
    }
    int from=0,to=out.length();while(from<to&&out.charAt(from)==' ')from++;while(to>from&&out.charAt(to-1)==' ')to--;
    return new Normalized(out.substring(from,to),List.copyOf(starts.subList(from,to)),List.copyOf(ends.subList(from,to)),List.copyOf(operations));
  }
}
