package se.medbo.examplatform.content.exam;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.medbo.examplatform.content.shared.DomainException;

@Service
public class ExamStructureService {
    private static final Logger LOG = LoggerFactory.getLogger(ExamStructureService.class);
    private static final String EXAM_FIELDS="id,code,name,country_code AS \"countryCode\",status,created_at AS \"createdAt\",updated_at AS \"updatedAt\",version";
    private static final String VERSION_FIELDS="id,exam_id AS \"examId\",version_code AS \"versionCode\",display_name AS \"displayName\",status,valid_from AS \"validFrom\",valid_to AS \"validTo\",created_at AS \"createdAt\",updated_at AS \"updatedAt\",version";
    private static final String NODE_FIELDS="id,code,name,description,sort_order AS \"sortOrder\",status,created_at AS \"createdAt\",updated_at AS \"updatedAt\",version";
    private final JdbcClient jdbc;
    public ExamStructureService(JdbcClient jdbc) { this.jdbc = jdbc; }

    public record ExamInput(String code, String name, String countryCode, String status, Long version) {}
    public record VersionInput(String versionCode, String displayName, String status, LocalDate validFrom, LocalDate validTo, Long version) {}
    public record NodeInput(String code, String name, String description, Integer sortOrder, String status, Long version) {}
    public record OrderInput(List<UUID> ids) {}

    public Map<String,Object> listExams(int page, int size, String search, String status, String country) {
        size = Math.min(Math.max(size, 1), 100); page = Math.max(page, 0);
        var where = " WHERE (CAST(:search AS text) IS NULL OR lower(e.name) LIKE lower('%' || :search || '%') OR lower(e.code) LIKE lower('%' || :search || '%')) AND (CAST(:status AS text) IS NULL OR e.status=:status) AND (CAST(:country AS text) IS NULL OR e.country_code=:country) ";
        var count = jdbc.sql("SELECT count(*) FROM exam e" + where).param("search", blank(search)).param("status", blank(status)).param("country", blank(country)).query(Long.class).single();
        var items = jdbc.sql("SELECT e.id,e.code,e.name,e.country_code AS \"countryCode\",e.status,e.created_at AS \"createdAt\",e.updated_at AS \"updatedAt\",e.version,(SELECT count(*) FROM exam_version v WHERE v.exam_id=e.id) AS \"versionCount\" FROM exam e" + where + " ORDER BY e.updated_at DESC, e.id LIMIT :size OFFSET :offset")
                .param("search", blank(search)).param("status", blank(status)).param("country", blank(country)).param("size", size).param("offset", page * size).query().listOfRows();
        return page(items, page, size, count);
    }

    @Transactional public Map<String,Object> createExam(ExamInput input) {
        require(input.code(), "code"); require(input.name(), "name"); require(input.countryCode(), "countryCode"); status(input.status());
        var id=UUID.randomUUID(); var now=OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.sql("INSERT INTO exam(id,code,name,country_code,status,created_at,updated_at) VALUES (:id,:code,:name,:country,:status,:now,:now)")
                .param("id",id).param("code",input.code().trim()).param("name",input.name().trim()).param("country",input.countryCode().trim().toUpperCase()).param("status",input.status()).param("now",now).update();
        log("exam.created", id); return exam(id);
    }
    public Map<String,Object> exam(UUID id) { return one("SELECT "+EXAM_FIELDS+",(SELECT count(*) FROM exam_version v WHERE v.exam_id=exam.id) AS \"versionCount\" FROM exam WHERE id=:id", id, "Exam"); }
    @Transactional public Map<String,Object> updateExam(UUID id, ExamInput input) {
        require(input.name(),"name"); status(input.status()); expect(input.version());
        int changed=jdbc.sql("UPDATE exam SET code=:code,name=:name,country_code=:country,status=:status,updated_at=:now,version=version+1 WHERE id=:id AND version=:version")
                .param("code",input.code()).param("name",input.name()).param("country",input.countryCode()).param("status",input.status()).param("now",OffsetDateTime.now(ZoneOffset.UTC)).param("id",id).param("version",input.version()).update();
        staleOrMissing(changed,"exam",id); return exam(id);
    }
    @Transactional public Map<String,Object> archiveExam(UUID id, long version) { updateArchive("exam",id,version); log("exam.archived",id); return exam(id); }

    public List<Map<String,Object>> versions(UUID examId, String status, LocalDate validAt, String search) {
        exists("exam",examId,"Exam");
        return jdbc.sql("SELECT "+VERSION_FIELDS+" FROM exam_version WHERE exam_id=:id AND (CAST(:status AS text) IS NULL OR status=:status) AND (CAST(:search AS text) IS NULL OR lower(display_name) LIKE lower('%'||:search||'%') OR lower(version_code) LIKE lower('%'||:search||'%')) AND (CAST(:validAt AS date) IS NULL OR (valid_from IS NULL OR valid_from<=:validAt) AND (valid_to IS NULL OR valid_to>=:validAt)) ORDER BY created_at DESC")
                .param("id",examId).param("status",blank(status)).param("search",blank(search)).param("validAt",validAt).query().listOfRows();
    }
    @Transactional public Map<String,Object> createVersion(UUID examId, VersionInput input) {
        exists("exam",examId,"Exam"); validateDates(input.validFrom(),input.validTo()); require(input.versionCode(),"versionCode"); require(input.displayName(),"displayName"); status(input.status());
        var id=UUID.randomUUID(); var now=OffsetDateTime.now(ZoneOffset.UTC); jdbc.sql("INSERT INTO exam_version(id,exam_id,version_code,display_name,status,valid_from,valid_to,created_at,updated_at) VALUES(:id,:exam,:code,:name,:status,:from,:to,:now,:now)")
                .param("id",id).param("exam",examId).param("code",input.versionCode()).param("name",input.displayName()).param("status",input.status()).param("from",input.validFrom()).param("to",input.validTo()).param("now",now).update(); log("exam_version.created",id); return examVersion(id);
    }
    public Map<String,Object> examVersion(UUID id) { return one("SELECT "+VERSION_FIELDS+" FROM exam_version WHERE id=:id",id,"Exam version"); }
    @Transactional public Map<String,Object> updateVersion(UUID id, VersionInput input) { validateDates(input.validFrom(),input.validTo()); expect(input.version()); int c=jdbc.sql("UPDATE exam_version SET version_code=:code,display_name=:name,status=:status,valid_from=:from,valid_to=:to,updated_at=:now,version=version+1 WHERE id=:id AND version=:version").param("code",input.versionCode()).param("name",input.displayName()).param("status",input.status()).param("from",input.validFrom()).param("to",input.validTo()).param("now",OffsetDateTime.now(ZoneOffset.UTC)).param("id",id).param("version",input.version()).update(); staleOrMissing(c,"exam_version",id); return examVersion(id); }
    @Transactional public Map<String,Object> archiveVersion(UUID id,long version){ updateArchive("exam_version",id,version); return examVersion(id); }

    public List<Map<String,Object>> subjects(UUID versionId){ exists("exam_version",versionId,"Exam version"); return jdbc.sql("SELECT "+NODE_FIELDS+",exam_version_id AS \"examVersionId\" FROM subject WHERE exam_version_id=:id ORDER BY sort_order,id").param("id",versionId).query().listOfRows(); }
    @Transactional public Map<String,Object> createSubject(UUID versionId,NodeInput in){ return createNode("subject","exam_version_id",versionId,in,"subject.created"); }
    public Map<String,Object> subject(UUID id){ return one("SELECT "+NODE_FIELDS+",exam_version_id AS \"examVersionId\" FROM subject WHERE id=:id",id,"Subject"); }
    @Transactional public Map<String,Object> updateSubject(UUID id,NodeInput in){ return updateNode("subject",id,in); }
    @Transactional public Map<String,Object> archiveSubject(UUID id,long v){ updateArchive("subject",id,v); return subject(id); }
    @Transactional public void orderSubjects(UUID versionId,OrderInput in){ reorder("subject","exam_version_id",versionId,in.ids()); log("subjects.reordered",versionId); }

    public List<Map<String,Object>> topics(UUID subjectId){ exists("subject",subjectId,"Subject"); return jdbc.sql("SELECT "+NODE_FIELDS+",subject_id AS \"subjectId\" FROM topic WHERE subject_id=:id ORDER BY sort_order,id").param("id",subjectId).query().listOfRows(); }
    @Transactional public Map<String,Object> createTopic(UUID subjectId,NodeInput in){ return createNode("topic","subject_id",subjectId,in,"topic.created"); }
    public Map<String,Object> topic(UUID id){ return one("SELECT "+NODE_FIELDS+",subject_id AS \"subjectId\" FROM topic WHERE id=:id",id,"Topic"); }
    @Transactional public Map<String,Object> updateTopic(UUID id,NodeInput in){ return updateNode("topic",id,in); }
    @Transactional public Map<String,Object> archiveTopic(UUID id,long v){ updateArchive("topic",id,v); return topic(id); }
    @Transactional public void orderTopics(UUID subjectId,OrderInput in){ reorder("topic","subject_id",subjectId,in.ids()); log("topics.reordered",subjectId); }

    private Map<String,Object> createNode(String table,String parent,UUID parentId,NodeInput in,String action){ exists(parent.equals("subject_id")?"subject":"exam_version",parentId,"Parent"); require(in.code(),"code"); require(in.name(),"name"); status(in.status()); if(in.sortOrder()==null||in.sortOrder()<0) throw validation("sortOrder must be non-negative"); var id=UUID.randomUUID(); var now=OffsetDateTime.now(ZoneOffset.UTC); jdbc.sql("INSERT INTO "+table+"(id,"+parent+",code,name,description,sort_order,status,created_at,updated_at) VALUES(:id,:parent,:code,:name,:description,:sort,:status,:now,:now)").param("id",id).param("parent",parentId).param("code",in.code()).param("name",in.name()).param("description",in.description()).param("sort",in.sortOrder()).param("status",in.status()).param("now",now).update(); log(action,id); return table.equals("subject")?subject(id):topic(id); }
    private Map<String,Object> updateNode(String table,UUID id,NodeInput in){ expect(in.version()); int c=jdbc.sql("UPDATE "+table+" SET code=:code,name=:name,description=:description,sort_order=:sort,status=:status,updated_at=:now,version=version+1 WHERE id=:id AND version=:version").param("code",in.code()).param("name",in.name()).param("description",in.description()).param("sort",in.sortOrder()).param("status",in.status()).param("now",OffsetDateTime.now(ZoneOffset.UTC)).param("id",id).param("version",in.version()).update(); staleOrMissing(c,table,id); return table.equals("subject")?subject(id):topic(id); }
    private void reorder(String table,String parent,UUID parentId,List<UUID> ids){ var existing=jdbc.sql("SELECT id FROM "+table+" WHERE "+parent+"=:parent ORDER BY sort_order").param("parent",parentId).query(UUID.class).list(); if(ids==null||!existing.containsAll(ids)||!ids.containsAll(existing)||ids.size()!=existing.size()) throw DomainException.conflict("Order must contain every child exactly once"); jdbc.sql("UPDATE "+table+" SET sort_order=sort_order+:offset WHERE "+parent+"=:parent").param("offset",existing.size()+1).param("parent",parentId).update(); for(int i=0;i<ids.size();i++) jdbc.sql("UPDATE "+table+" SET sort_order=:sort,updated_at=:now,version=version+1 WHERE id=:id AND "+parent+"=:parent").param("sort",i).param("now",OffsetDateTime.now(ZoneOffset.UTC)).param("id",ids.get(i)).param("parent",parentId).update(); }
    private void updateArchive(String table,UUID id,long version){ int c=jdbc.sql("UPDATE "+table+" SET status='ARCHIVED',updated_at=:now,version=version+1 WHERE id=:id AND version=:version AND status<>'ARCHIVED'").param("now",OffsetDateTime.now(ZoneOffset.UTC)).param("id",id).param("version",version).update(); staleOrMissing(c,table,id); }
    private void exists(String table,UUID id,String label){ if(!jdbc.sql("SELECT EXISTS(SELECT 1 FROM "+table+" WHERE id=:id)").param("id",id).query(Boolean.class).single()) throw DomainException.notFound(label); }
    private Map<String,Object> one(String sql,UUID id,String label){ var rows=jdbc.sql(sql).param("id",id).query().listOfRows(); if(rows.isEmpty()) throw DomainException.notFound(label); return rows.getFirst(); }
    private void staleOrMissing(int c,String table,UUID id){if(c==0){exists(table,id,"Resource");throw DomainException.conflict("The resource was changed by another administrator");}}
    private void status(String s){if(s==null||!List.of("DRAFT","ACTIVE","ARCHIVED").contains(s))throw validation("Invalid status");}
    private void validateDates(LocalDate from,LocalDate to){if(from!=null&&to!=null&&to.isBefore(from))throw validation("validTo cannot be before validFrom");}
    private void require(String s,String field){if(s==null||s.isBlank())throw validation(field+" is required");}
    private void expect(Long v){if(v==null||v<0)throw validation("version is required");}
    private DomainException validation(String m){return new DomainException(HttpStatus.UNPROCESSABLE_ENTITY,"VALIDATION_ERROR",m);}
    private String blank(String s){return s==null||s.isBlank()?null:s;}
    private Map<String,Object> page(List<Map<String,Object>> items,int p,int s,long total){return Map.of("items",items,"page",p,"size",s,"totalItems",total,"totalPages",(total+s-1)/s);}
    private void log(String action,UUID id){LOG.info("content_action={} actor={} entity_id={}",action,SecurityContextHolder.getContext().getAuthentication().getName(),id);}
}
